import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.Version;
import java.io.ByteArrayOutputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.leavesmc.leaves.protocol.core.LeavesProtocol;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.protocol.core.invoker.PlayerInvokerHolder;
import org.leavesmc.leaves.protocol.servux.PacketSplitter;
import org.leavesmc.leaves.protocol.servux.ServuxEntityDataProtocol;
import org.leavesmc.leaves.protocol.servux.ServuxEntityDataProtocol.EntityDataPayload;
import org.leavesmc.leaves.protocol.servux.ServuxEntityDataProtocol.EntityDataPayloadType;
import org.leavesmc.leaves.protocol.servux.ServuxStructuresProtocol.StructuresPayload;
import org.leavesmc.leaves.protocol.servux.ServuxStructuresProtocol.StructuresPayloadType;

/** Bounded regression checks against real compiled server classes and runtime dependencies. */
public final class ServuxRegression {
    private static final Gson JSON = new Gson();
    private static final int SIZE = 256 * 1024;
    private static final int[] LENGTHS = {0, 1, 127, 128, 32762, 262144, 1048571, 1048572, 2097149};

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void emit(Map<String, ?> value) {
        System.out.println("SERVUX_TEST " + JSON.toJson(value));
    }

    private static long direct() {
        return UnpooledByteBufAllocator.DEFAULT.metric().usedDirectMemory();
    }

    private static long heap() {
        return UnpooledByteBufAllocator.DEFAULT.metric().usedHeapMemory();
    }

    private static void balanced(long nativeBefore, long heapBefore, String name) {
        require(direct() == nativeBefore, name + ": direct delta = " + (direct() - nativeBefore));
        require(heap() == heapBefore, name + ": heap allocator delta = " + (heap() - heapBefore));
    }

    private static FriendlyByteBuf buffer(boolean direct, int size) {
        return new FriendlyByteBuf(direct ? Unpooled.directBuffer(size) : Unpooled.buffer(size));
    }

    private static byte[] bytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) (i * 31 + 17);
        return data;
    }

    private static StructuresPayload structure(byte[] expected, boolean directSource) {
        FriendlyByteBuf source = buffer(directSource, expected.length + 7);
        try {
            source.writeZero(7).writeBytes(expected);
            source.readerIndex(7);
            StructuresPayload payload = new StructuresPayload(StructuresPayloadType.PACKET_S2C_STRUCTURE_DATA, source);
            require(source.refCnt() == 1 && source.readerIndex() == 7,
                "Snapshot must neither consume nor release borrowed source");
            source.setZero(0, source.writerIndex());
            return payload;
        } finally {
            source.release();
        }
    }

    private static byte[] encode(StructuresPayload payload) {
        FriendlyByteBuf output = buffer(false, 32);
        try {
            StructuresPayload.CODEC.encode(output, payload);
            require(output.readVarInt() == 2, "Unexpected structure packet discriminator");
            return ByteBufUtil.getBytes(output);
        } finally {
            output.release();
        }
    }

    private static void wire() {
        long nativeBefore = direct();
        long heapBefore = heap();
        for (int length : LENGTHS) {
            byte[] expected = bytes(length);
            for (boolean directSource : new boolean[] {false, true}) {
                StructuresPayload payload = structure(expected, directSource);
                require(Arrays.equals(expected, encode(payload)), "Wire bytes differ at length " + length);
                require(Arrays.equals(expected, encode(payload)), "Repeated encode mutated payload");
            }
            emit(Map.of("test", "structure_wire_and_snapshot", "length", length,
                "heapAndDirectSources", true, "repeatEncode", true, "sourceIndexUnchanged", true));
        }
        StructuresPayload payload = structure(bytes(64), true);
        FriendlyByteBuf shortOutput = new FriendlyByteBuf(Unpooled.buffer(1, 1));
        try {
            try {
                StructuresPayload.CODEC.encode(shortOutput, payload);
                throw new AssertionError("Expected undersized output to fail");
            } catch (IndexOutOfBoundsException expected) {
                require(Arrays.equals(bytes(64), encode(payload)), "Failed encode damaged payload");
            }
        } finally {
            shortOutput.release();
        }
        balanced(nativeBefore, heapBefore, "wire");
        emit(Map.of("test", "failed_encode_then_retry", "passed", true));
    }

    private static void pressure() throws Exception {
        long nativeBefore = direct();
        long heapBefore = heap();
        long poolBefore = directPool();
        byte[] expected = bytes(SIZE);
        for (int i = 1; i <= 256; i++) {
            StructuresPayload payload = structure(expected, false);
            require(Arrays.equals(expected, encode(payload)), "Pressure wire content differs");
            if (i == 64 || i == 128 || i == 256) {
                balanced(nativeBefore, heapBefore, "pressure " + i);
                emit(Map.of("test", "structure_pressure", "iterations", i, "payloadBytes", SIZE,
                    "unreleasedDirectBytes", direct() - nativeBefore,
                    "unreleasedHeapBufferBytes", heap() - heapBefore,
                    "directBufferPoolDelta", directPool() - poolBefore));
            }
        }
        WeakReference<Object> witness = new WeakReference<>(new Object());
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(50);
        }
        require(witness.get() == null, "GC did not collect witness");
        balanced(nativeBefore, heapBefore, "post-GC pressure");
        emit(Map.of("test", "pressure_after_gc", "gcWitnessCollected", true,
            "unreleasedDirectBytes", direct() - nativeBefore, "unreleasedHeapBufferBytes", heap() - heapBefore));
    }

    private static long directPool() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
            .filter(pool -> pool.getName().equals("direct")).mapToLong(BufferPoolMXBean::getMemoryUsed).sum();
    }

    private static EntityDataPayload entity(EntityDataPayloadType type) throws Exception {
        Constructor<EntityDataPayload> constructor = EntityDataPayload.class.getDeclaredConstructor(EntityDataPayloadType.class);
        constructor.setAccessible(true);
        return constructor.newInstance(type);
    }

    private static void set(EntityDataPayload payload, String name, Object value) throws Exception {
        Field field = EntityDataPayload.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(payload, value);
    }

    private static void entitySimple() throws Exception {
        long nativeBefore = direct();
        long heapBefore = heap();
        CompoundTag nbt = new CompoundTag();
        nbt.putString("name", "servux-regression");
        nbt.putInt("value", 1234);
        for (EntityDataPayloadType type : new EntityDataPayloadType[] {
            EntityDataPayloadType.PACKET_S2C_METADATA,
            EntityDataPayloadType.PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE,
            EntityDataPayloadType.PACKET_S2C_ENTITY_NBT_RESPONSE_SIMPLE
        }) {
            EntityDataPayload payload = entity(type);
            set(payload, "nbt", nbt);
            set(payload, "pos", new BlockPos(12, 64, -19));
            set(payload, "entityId", 321);
            FriendlyByteBuf first = buffer(false, 64);
            FriendlyByteBuf second = buffer(false, 64);
            try {
                EntityDataPayload.CODEC.encode(first, payload);
                byte[] expected = ByteBufUtil.getBytes(first);
                require(first.readVarInt() == type.type, "Simple entity type changed");
                if (type == EntityDataPayloadType.PACKET_S2C_BLOCK_NBT_RESPONSE_SIMPLE) {
                    require(first.readBlockPos().equals(new BlockPos(12, 64, -19)), "Block location changed");
                } else if (type == EntityDataPayloadType.PACKET_S2C_ENTITY_NBT_RESPONSE_SIMPLE) {
                    require(first.readVarInt() == 321, "Entity id changed");
                }
                require(nbt.equals(first.readNbt()), "NBT data changed");
                require(!first.isReadable(), "Unexpected trailing simple payload bytes");
                first.readerIndex(0);
                EntityDataPayload decoded = EntityDataPayload.CODEC.decode(first);
                EntityDataPayload.CODEC.encode(second, decoded);
                require(Arrays.equals(expected, ByteBufUtil.getBytes(second)), "Simple entity roundtrip differs");
                emit(Map.of("test", "entity_simple_roundtrip", "packetType", type.type, "passed", true));
            } finally {
                first.release(); second.release();
            }
        }
        balanced(nativeBefore, heapBefore, "entity simple");
    }

    private static void decoding() {
        long nativeBefore = direct();
        long heapBefore = heap();
        byte[] expected = bytes(SIZE);
        for (int i = 0; i < 256; i++) {
            FriendlyByteBuf structures = buffer(i % 2 == 0, SIZE + 5);
            FriendlyByteBuf entities = buffer(i % 2 != 0, SIZE + 5);
            FriendlyByteBuf output = buffer(false, SIZE + 5);
            StructuresPayload decoded;
            EntityDataPayload entityDecoded;
            try {
                structures.writeVarInt(2).writeBytes(expected);
                entities.writeVarInt(11).writeBytes(expected);
                decoded = StructuresPayload.CODEC.decode(structures);
                entityDecoded = EntityDataPayload.CODEC.decode(entities);
                require(!structures.isReadable() && !entities.isReadable(), "Decoder left payload unread");
            } finally {
                structures.release(); entities.release();
            }
            try {
                require(Arrays.equals(expected, encode(decoded)), "Structure decode snapshot invalid after source release");
                EntityDataPayload.CODEC.encode(output, entityDecoded);
                require(output.readVarInt() == 11, "Entity split discriminator changed");
                require(Arrays.equals(expected, ByteBufUtil.getBytes(output)), "Entity split content changed");
                output.clear();
                EntityDataPayload.CODEC.encode(output, entityDecoded);
                require(output.readVarInt() == 11, "Repeat entity split discriminator changed");
                require(Arrays.equals(expected, ByteBufUtil.getBytes(output)), "Repeat entity split encoding mutated data");
            } finally {
                output.release();
            }
        }
        balanced(nativeBefore, heapBefore, "decode pressure");
        emit(Map.of("test", "structure_and_entity_decode_pressure", "iterations", 256,
            "payloadBytes", SIZE, "unreleasedDirectBytes", direct() - nativeBefore,
            "unreleasedHeapBufferBytes", heap() - heapBefore, "sourceReleasedBeforeReencode", true));
    }

    private static void unsupportedUploads() {
        long nativeBefore = direct();
        long heapBefore = heap();
        for (int type : new int[] {12, 13}) {
            for (int length : new int[] {0, 1, 5, 32762, SIZE}) {
                for (int i = 0; i < 32; i++) {
                    FriendlyByteBuf input = buffer(i % 2 == 0, length + 5);
                    try {
                        input.writeVarInt(type);
                        for (int j = 0; j < length; j++) input.writeByte(0xff);
                        EntityDataPayload decoded = EntityDataPayload.CODEC.decode(input);
                        require(!input.isReadable(), "Unsupported upload bytes must be consumed");
                        // No player/session lookup is necessary for an unsupported operation.
                        ServuxEntityDataProtocol.onPacketReceive(null, decoded);
                    } finally {
                        input.release();
                    }
                }
            }
        }
        balanced(nativeBefore, heapBefore, "unsupported upload");
        emit(Map.of("test", "unsupported_upload_discard", "packetTypes", List.of(12, 13),
            "iterations", 320, "malformedPayloadContents", true,
            "unreleasedDirectBytes", direct() - nativeBefore, "unreleasedHeapBufferBytes", heap() - heapBefore));
    }

    private static void splitter() {
        long nativeBefore = direct();
        long heapBefore = heap();
        for (int size : new int[] {0, 1, PacketSplitter.MAX_PAYLOAD_PER_PACKET_S2C,
            PacketSplitter.MAX_PAYLOAD_PER_PACKET_S2C + 1, 2 * PacketSplitter.MAX_PAYLOAD_PER_PACKET_S2C + 31}) {
            byte[] expected = bytes(size);
            FriendlyByteBuf source = buffer(false, Math.max(1, size));
            source.writeBytes(expected);
            List<FriendlyByteBuf> fragments = new ArrayList<>();
            List<StructuresPayload> delayedPayloads = new ArrayList<>();
            ByteArrayOutputStream reconstructed = new ByteArrayOutputStream(size);
            try {
                require(PacketSplitter.send((player, fragment) -> {
                    require(fragment.refCnt() == 1, "Callback must borrow a live fragment");
                    fragments.add(fragment);
                    delayedPayloads.add(new StructuresPayload(StructuresPayloadType.PACKET_S2C_STRUCTURE_DATA, fragment));
                    if (fragments.size() == 1) require(fragment.readVarInt() == size, "Incorrect length prefix");
                    byte[] content = ByteBufUtil.getBytes(fragment);
                    reconstructed.write(content, 0, content.length);
                }, source, null), "Send failed");
                require(source.refCnt() == 0, "Send must release owned source");
                require(!fragments.isEmpty(), "Empty payload still requires a zero length frame");
                for (FriendlyByteBuf fragment : fragments) require(fragment.refCnt() == 0, "Sent fragment leaked");
                require(Arrays.equals(expected, reconstructed.toByteArray()), "Fragment bytes differ");
                ByteArrayOutputStream delayed = new ByteArrayOutputStream(size);
                for (int i = 0; i < delayedPayloads.size(); i++) {
                    FriendlyByteBuf content = new FriendlyByteBuf(Unpooled.wrappedBuffer(encode(delayedPayloads.get(i))));
                    try {
                        if (i == 0) require(content.readVarInt() == size, "Delayed length prefix differs");
                        byte[] bytes = ByteBufUtil.getBytes(content);
                        delayed.write(bytes, 0, bytes.length);
                    } finally {
                        content.release();
                    }
                }
                require(Arrays.equals(expected, delayed.toByteArray()), "Deferred encoding used released fragments");
                emit(Map.of("test", "splitter_success", "payloadBytes", size,
                    "fragments", fragments.size(), "sourceAndFragmentsReleased", true,
                    "deferredPayloadEncoding", true));
            } finally {
                if (source.refCnt() > 0) source.release();
                for (FriendlyByteBuf fragment : fragments) if (fragment.refCnt() > 0) fragment.release();
            }
        }
        FriendlyByteBuf source = buffer(true, 1024);
        source.writeZero(1024);
        FriendlyByteBuf[] borrowed = new FriendlyByteBuf[1];
        try {
            try {
                PacketSplitter.send((player, fragment) -> {
                    borrowed[0] = fragment;
                    throw new IllegalStateException("expected callback failure");
                }, source, null);
                throw new AssertionError("Expected callback exception");
            } catch (IllegalStateException expected) {
                require("expected callback failure".equals(expected.getMessage()), "Unexpected send error");
                require(source.refCnt() == 0 && borrowed[0] != null && borrowed[0].refCnt() == 0,
                    "Failure must release both source and current fragment");
            }
        } finally {
            if (source.refCnt() > 0) source.release();
            if (borrowed[0] != null && borrowed[0].refCnt() > 0) borrowed[0].release();
        }
        balanced(nativeBefore, heapBefore, "splitter");
        emit(Map.of("test", "splitter_callback_exception", "sourceAndFragmentReleased", true));
    }

    private static void nbtSerialization() {
        long nativeBefore = direct();
        long heapBefore = heap();
        CompoundTag valid = new CompoundTag();
        valid.putString("Structures", "example");
        valid.putInt("version", 2);
        List<byte[]> sent = new ArrayList<>();
        PacketSplitter.sendNbt((player, fragment) -> sent.add(ByteBufUtil.getBytes(fragment)), valid, null);
        require(sent.size() == 1, "Expected one small NBT fragment");
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.wrappedBuffer(sent.get(0)));
        try {
            int length = packet.readVarInt();
            require(length == packet.readableBytes(), "NBT split length differs");
            require(valid.equals(packet.readNbt()) && !packet.isReadable(), "NBT split content differs");
        } finally {
            packet.release();
        }

        // Java NBT strings use modified UTF-8 with an unsigned-short byte limit.
        // This is a real serialization failure, not a mock of the cleanup implementation.
        CompoundTag invalid = new CompoundTag();
        invalid.putString("too_long", "x".repeat(65536));
        boolean[] callbackInvoked = {false};
        boolean failed = false;
        try {
            PacketSplitter.sendNbt((player, fragment) -> callbackInvoked[0] = true, invalid, null);
        } catch (RuntimeException expected) {
            failed = true;
        }
        require(failed, "Oversized modified UTF-8 string must fail NBT serialization");
        require(!callbackInvoked[0], "Serialization failure must happen before sending");
        balanced(nativeBefore, heapBefore, "NBT serialization failure");
        emit(Map.of("test", "nbt_success_and_serialization_failure", "successBytesPreserved", true,
            "failedBeforeCallback", true, "unreleasedDirectBytes", direct() - nativeBefore,
            "unreleasedHeapBufferBytes", heap() - heapBefore));
    }

    public static final class DisabledProtocol implements LeavesProtocol {
        int joins;
        int leaves;

        @Override public boolean isActive() { return false; }
        @ProtocolHandler.PlayerJoin public void joined(ServerPlayer player) { joins++; }
        @ProtocolHandler.PlayerLeave public void left(ServerPlayer player) { leaves++; }
    }

    private static void disabledCleanup() throws Exception {
        DisabledProtocol owner = new DisabledProtocol();
        var join = DisabledProtocol.class.getDeclaredMethod("joined", ServerPlayer.class);
        var leave = DisabledProtocol.class.getDeclaredMethod("left", ServerPlayer.class);
        new PlayerInvokerHolder<>(owner, join, join.getAnnotation(ProtocolHandler.PlayerJoin.class)).invoke(null);
        new PlayerInvokerHolder<>(owner, leave, leave.getAnnotation(ProtocolHandler.PlayerLeave.class)).invoke(null);
        require(owner.joins == 0, "Disabled protocol must still skip join behavior");
        require(owner.leaves == 1, "Disabling protocol must not skip departing-player cleanup");
        emit(Map.of("test", "disabled_protocol_player_leave", "joinSkipped", true, "leaveInvoked", true));
    }

    private static void concurrent() throws Exception {
        long nativeBefore = direct();
        long heapBefore = heap();
        StructuresPayload shared = structure(bytes(32762), true);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(() -> {
                    byte[] expected = bytes(32762);
                    for (int i = 0; i < 128; i++) {
                        require(Arrays.equals(expected, encode(shared)), "Concurrent shared payload encode differs");
                        FriendlyByteBuf input = buffer(true, expected.length + 5);
                        try {
                            input.writeVarInt(11).writeBytes(expected);
                            EntityDataPayload.CODEC.decode(input);
                        } finally {
                            input.release();
                        }
                    }
                    return true;
                });
            }
            for (var future : executor.invokeAll(tasks)) require(future.get(), "Worker failed");
        }
        balanced(nativeBefore, heapBefore, "concurrent codecs");
        emit(Map.of("test", "concurrent_codecs", "workers", 4, "iterationsPerWorker", 128,
            "unreleasedDirectBytes", direct() - nativeBefore, "unreleasedHeapBufferBytes", heap() - heapBefore));
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Initialize both codecs outside the allocator baselines.
        require(StructuresPayload.CODEC != null && EntityDataPayload.CODEC != null, "Missing codecs");
        emit(Map.of("test", "environment", "java", System.getProperty("java.version"),
            "netty", Version.identify().get("netty-buffer").artifactVersion(),
            "structuresLocation", StructuresPayload.class.getProtectionDomain().getCodeSource().getLocation().toString(),
            "splitterLocation", PacketSplitter.class.getProtectionDomain().getCodeSource().getLocation().toString()));
        switch (args[0]) {
            case "wire" -> wire();
            case "pressure" -> pressure();
            case "decode" -> decoding();
            case "entity" -> entitySimple();
            case "uploads" -> unsupportedUploads();
            case "splitter" -> splitter();
            case "nbt" -> nbtSerialization();
            case "lifecycle" -> disabledCleanup();
            case "concurrent" -> concurrent();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
