import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import net.minecraft.network.FriendlyByteBuf;
import org.leavesmc.leaves.protocol.servux.PacketSplitter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Regression probes for the unmodified PacketSplitter, not proposed fixes.
 * Assertions describe the observed vulnerable behavior. This isolated JVM owns
 * all test sessions; reflective cleanup is performed by this harness, not by
 * production code. Unreachable temporary copies cannot be recovered by it.
 */
public final class SplitterProbe {
    private static final Field SESSIONS;
    private static final Field RECEIVED;
    private static final Method SEND_WITH_LIMIT;

    static {
        try {
            SESSIONS = PacketSplitter.class.getDeclaredField("READING_SESSIONS");
            SESSIONS.setAccessible(true);
            Class<?> session = Class.forName(PacketSplitter.class.getName() + "$ReadingSession");
            RECEIVED = session.getDeclaredField("received");
            RECEIVED.setAccessible(true);
            SEND_WITH_LIMIT = PacketSplitter.class.getDeclaredMethod("send",
                PacketSplitter.IPacketSplitterHandler.class, FriendlyByteBuf.class,
                Class.forName("net.minecraft.server.level.ServerPlayer", false,
                    PacketSplitter.class.getClassLoader()), int.class);
            SEND_WITH_LIMIT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Exception {
        require(sessions().isEmpty(), "Run in an isolated process with no existing sessions");
        run("normal_multifragment", SplitterProbe::normalMultifragment);
        run("incomplete_session_retained", SplitterProbe::incompleteSession);
        run("receive_uses_64_mib_limit", SplitterProbe::receiveLimit);
        run("negative_declared_length", SplitterProbe::negativeLength);
        run("oversize_header_session_retained", SplitterProbe::oversizeHeader);
        run("cumulative_length_overrun_accepted", SplitterProbe::cumulativeOverrun);
        run("send_multifragment_ownership", SplitterProbe::sendMultifragment);
        run("send_callback_exception_retains_buffers", SplitterProbe::sendCallbackException);
        require(sessions().isEmpty(), "Harness cleanup must remove every test session");
        System.out.println("AUDIT {\"suite\":\"PacketSplitter\",\"scenarios\":8,\"all_observations_confirmed\":true,"
            + "\"cleanup\":\"test_owned_buffers_and_reflective_session_cleanup\","
            + "\"not_tested\":\"real_player_disconnect_or_concurrent_region_threads\"}");
    }

    private static String normalMultifragment() throws Exception {
        long directBefore = UnpooledByteBufAllocator.DEFAULT.metric().usedDirectMemory();
        FriendlyByteBuf first = fragment(6, 1, 2);
        FriendlyByteBuf second = bytes(3, 4, 5, 6);
        FriendlyByteBuf complete = null;
        try {
            require(PacketSplitter.receive(1, first) == null, "First fragment must be incomplete");
            require(sessions().size() == 1, "Incomplete session must be tracked");
            complete = PacketSplitter.receive(1, second);
            require(complete != null, "Second fragment must complete message");
            byte[] actual = new byte[complete.readableBytes()];
            complete.getBytes(complete.readerIndex(), actual);
            require(Arrays.equals(actual, new byte[] {1, 2, 3, 4, 5, 6}), "Reassembly bytes differ");
            require(sessions().isEmpty(), "Completed session must be removed");
            require(first.refCnt() == 1 && second.refCnt() == 1 && complete.refCnt() == 1,
                "Inputs and returned output remain caller-owned");
        } finally {
            release(first, second, complete);
        }
        long retainedDirect = UnpooledByteBufAllocator.DEFAULT.metric().usedDirectMemory() - directBefore;
        require(retainedDirect == 6,
            "Expected both temporary readBytes copies to retain 6 direct bytes after releasing owned buffers, got "
                + retainedDirect);
        return "\"bytes\":6,\"wire_equal\":true,\"session_removed\":true,\"output_refcnt_before_cleanup\":1,"
            + "\"retained_direct_bytes_after_owned_buffers_released\":" + retainedDirect;
    }

    private static String incompleteSession() throws Exception {
        FriendlyByteBuf first = fragment(128, 1, 2, 3, 4);
        try {
            require(PacketSplitter.receive(2, first) == null, "Expected incomplete result");
            first.release();
            ByteBuf retained = (ByteBuf) RECEIVED.get(sessions().get(2L));
            require(retained != null && retained.refCnt() == 1 && retained.capacity() == 128,
                "Session must retain its assembly buffer after input release");
            return "\"declared_bytes\":128,\"received_bytes\":4,\"retained_capacity\":128,"
                + "\"session_remains_after_input_release\":true";
        } finally {
            release(first);
        }
    }

    private static String receiveLimit() throws Exception {
        int size = 2 * PacketSplitter.DEFAULT_MAX_RECEIVE_SIZE_C2S;
        require(size == 2 * 1024 * 1024, "Probe allocation must remain bounded to 2 MiB");
        FriendlyByteBuf first = fragment(size);
        try {
            require(PacketSplitter.receive(3, first) == null, "Default receive accepts >1 MiB declaration");
            ByteBuf retained = (ByteBuf) RECEIVED.get(sessions().get(3L));
            require(retained.capacity() == size, "Declared size is allocated before body arrives");
            require(PacketSplitter.DEFAULT_MAX_RECEIVE_SIZE_S2C == 64 * 1024 * 1024,
                "Unexpected default S2C limit");
            return "\"accepted_declared_bytes\":" + size + ",\"body_bytes\":0,\"allocated_heap_bytes\":"
                + retained.capacity() + ",\"defined_c2s_limit_bytes\":1048576,\"used_default_limit_bytes\":67108864";
        } finally {
            release(first);
        }
    }

    private static String negativeLength() throws Exception {
        FriendlyByteBuf first = fragment(-1);
        try {
            expect(IllegalArgumentException.class, () -> PacketSplitter.receive(4, first));
            require(sessions().containsKey(4L), "Failed negative session remains mapped");
            require(RECEIVED.get(sessions().get(4L)) == null, "No assembly buffer should be allocated");
            return "\"exception\":\"IllegalArgumentException\",\"retained_sessions\":1,\"allocated_body_bytes\":0";
        } finally {
            release(first);
        }
    }

    private static String oversizeHeader() throws Exception {
        FriendlyByteBuf first = fragment(PacketSplitter.DEFAULT_MAX_RECEIVE_SIZE_S2C + 1);
        FriendlyByteBuf followup = bytes(1);
        try {
            expect(IllegalArgumentException.class, () -> PacketSplitter.receive(5, first));
            require(sessions().containsKey(5L), "Oversize session is not removed");
            require(RECEIVED.get(sessions().get(5L)) == null, "Oversize body must not be allocated");
            expect(NullPointerException.class, () -> PacketSplitter.receive(5, followup));
            return "\"header_only\":true,\"allocated_body_bytes\":0,\"retained_sessions\":1,"
                + "\"first_exception\":\"IllegalArgumentException\",\"followup_exception\":\"NullPointerException\"";
        } finally {
            release(first, followup);
        }
    }

    private static String cumulativeOverrun() throws Exception {
        FriendlyByteBuf first = fragment(4, 1, 2);
        FriendlyByteBuf second = bytes(3, 4, 5);
        FriendlyByteBuf complete = null;
        try {
            require(PacketSplitter.receive(6, first) == null, "First fragment must be incomplete");
            complete = PacketSplitter.receive(6, second);
            require(complete != null && complete.readableBytes() == 5,
                "Current implementation accepts more bytes than declared");
            require(sessions().isEmpty(), "Overrun is treated as complete");
            return "\"declared_bytes\":4,\"returned_bytes\":5,\"overrun_rejected\":false";
        } finally {
            release(first, second, complete);
        }
    }

    private static String sendMultifragment() throws Exception {
        FriendlyByteBuf packet = bytes(1, 2, 3, 4, 5, 6, 7, 8);
        List<FriendlyByteBuf> fragments = new ArrayList<>();
        try {
            PacketSplitter.IPacketSplitterHandler handler = (player, fragment) -> fragments.add(fragment);
            require(Boolean.TRUE.equals(SEND_WITH_LIMIT.invoke(null, handler, packet, null, 4)), "Send failed");
            require(packet.refCnt() == 0, "Successful send releases input packet");
            require(fragments.size() == 2, "Expected two outgoing fragments");
            require(fragments.get(0).readVarInt() == 8, "First fragment must carry total length");
            for (int i = 0; i < 8; i++) {
                require(fragments.get(i / 4).readUnsignedByte() == i + 1, "Split bytes differ");
            }
            require(fragments.stream().allMatch(buf -> buf.refCnt() == 1),
                "Outgoing fragments remain owned by handler");
            return "\"fragments\":2,\"input_refcnt\":0,\"fragment_refcnt\":1,\"wire_equal\":true";
        } finally {
            release(packet);
            for (FriendlyByteBuf fragment : fragments) release(fragment);
        }
    }

    private static String sendCallbackException() throws Exception {
        FriendlyByteBuf packet = bytes(1, 2, 3, 4, 5, 6, 7, 8);
        FriendlyByteBuf[] fragment = new FriendlyByteBuf[1];
        try {
            expect(IllegalStateException.class, () -> PacketSplitter.send((player, buf) -> {
                fragment[0] = buf;
                throw new IllegalStateException("deliberate test callback failure");
            }, packet, null));
            require(packet.refCnt() == 1 && fragment[0] != null && fragment[0].refCnt() == 1,
                "Current send path lacks cleanup when callback throws");
            return "\"exception\":\"IllegalStateException\",\"input_refcnt_after_failure\":1,"
                + "\"fragment_refcnt_after_failure\":1";
        } finally {
            release(packet, fragment[0]);
        }
    }

    private static void run(String name, Probe probe) throws Exception {
        require(sessions().isEmpty(), "Previous test session was not cleaned up");
        try {
            String details = probe.run();
            System.out.println("AUDIT {\"scenario\":\"" + name + "\",\"observation_confirmed\":true," + details + "}");
        } finally {
            // Test-only cleanup; there is no production cancellation API.
            for (Object session : sessions().values()) release((ByteBuf) RECEIVED.get(session));
            sessions().clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Object> sessions() throws IllegalAccessException {
        return (Map<Long, Object>) SESSIONS.get(null);
    }

    private static FriendlyByteBuf fragment(int declaredSize, int... values) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(declaredSize);
        for (int value : values) buf.writeByte(value);
        return buf;
    }

    private static FriendlyByteBuf bytes(int... values) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        for (int value : values) buf.writeByte(value);
        return buf;
    }

    private static void release(ByteBuf... buffers) {
        for (ByteBuf buf : buffers) if (buf != null && buf.refCnt() > 0) buf.release();
    }

    private static void expect(Class<? extends Throwable> expected, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (actual instanceof InvocationTargetException invocation) actual = invocation.getCause();
            require(expected.isInstance(actual), "Expected " + expected.getName() + " but got " + actual);
            return;
        }
        throw new AssertionError("Expected " + expected.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }

    @FunctionalInterface
    private interface Probe { String run() throws Exception; }
}
