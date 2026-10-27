import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.Version;
import io.netty.util.internal.PlatformDependent;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.leavesmc.leaves.protocol.servux.PacketSplitter;
import org.leavesmc.leaves.protocol.servux.ServuxStructuresProtocol.StructuresPayload;
import org.leavesmc.leaves.protocol.servux.ServuxStructuresProtocol.StructuresPayloadType;

/** Calls the real server classes. Every mode runs in a separate bounded JVM. */
public class MemoryProbe {
    private static final Gson JSON = new Gson();
    private static final int SIZE = 256 * 1024;

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void emit(Map<String, ?> value) {
        System.out.println("AUDIT " + JSON.toJson(value));
    }

    static long direct() {
        return UnpooledByteBufAllocator.DEFAULT.metric().usedDirectMemory();
    }

    static long directPool() {
        return ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
            .filter(pool -> pool.getName().equals("direct"))
            .mapToLong(BufferPoolMXBean::getMemoryUsed).sum();
    }

    static FriendlyByteBuf heap(int size) {
        FriendlyByteBuf result = new FriendlyByteBuf(Unpooled.buffer(size));
        check(!result.isDirect(), "Expected production-style heap source");
        result.writeZero(size);
        return result;
    }

    static void encode(FriendlyByteBuf output, FriendlyByteBuf input, boolean control) {
        if (control) {
            output.writeVarInt(StructuresPayloadType.PACKET_S2C_STRUCTURE_DATA.type);
            output.writeBytes(input, input.readableBytes());
        } else {
            StructuresPayload.CODEC.encode(output,
                new StructuresPayload(StructuresPayloadType.PACKET_S2C_STRUCTURE_DATA, input));
        }
    }

    static void once(boolean control) {
        FriendlyByteBuf input = heap(SIZE);
        FriendlyByteBuf output = new FriendlyByteBuf(Unpooled.buffer(SIZE + 5));
        try {
            encode(output, input, control);
            check(input.readableBytes() == 0, "Source reader index differs");
            check(output.readVarInt() == 2 && output.readableBytes() == SIZE, "Bad wire length");
        } finally {
            // The test owns and frees inputs/outputs. The discarded intermediate is inaccessible.
            output.release();
            input.release();
        }
    }

    static void collect() throws Exception {
        WeakReference<Object> sentinel = new WeakReference<>(new Object());
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(50);
        }
        check(sentinel.get() == null, "GC did not collect the witness object");
    }

    static void memory(boolean control) throws Exception {
        // Initialize codecs before the baseline; the zero-length case cannot leak data bytes.
        FriendlyByteBuf warmInput = heap(0);
        FriendlyByteBuf warmOutput = new FriendlyByteBuf(Unpooled.buffer());
        try { encode(warmOutput, warmInput, false); }
        finally { warmInput.release(); warmOutput.release(); }
        long baseline = direct();
        long poolBaseline = directPool();
        for (int i = 1; i <= 256; i++) {
            once(control);
            if (i == 64 || i == 128 || i == 256) {
                long delta = direct() - baseline;
                check(delta == (control ? 0 : (long) i * SIZE), "Unexpected allocation delta: " + delta);
                emit(Map.of("test", control ? "control" : "structures", "iterations", i,
                    "payloadBytes", SIZE, "unreleasedDirectBytes", delta,
                    "directBufferPoolDelta", directPool() - poolBaseline));
            }
        }
        collect();
        long delta = direct() - baseline;
        check(delta == (control ? 0 : 256L * SIZE), "Post-GC delta differs");
        emit(Map.of("test", control ? "control_gc" : "structures_gc", "unreleasedDirectBytes", delta,
            "directBufferPoolDelta", directPool() - poolBaseline, "gcWitnessCollected", true));
    }

    static void equivalence() {
        int[] sizes = {0, 1, 127, 128, 32762, 262144, 1048571, 1048572, 2097149};
        for (int size : sizes) {
            FriendlyByteBuf first = heap(size + 7);
            FriendlyByteBuf second = heap(size + 7);
            FriendlyByteBuf original = new FriendlyByteBuf(Unpooled.buffer(size + 5));
            FriendlyByteBuf control = new FriendlyByteBuf(Unpooled.buffer(size + 5));
            try {
                for (int i = 0; i < size + 7; i++) {
                    first.setByte(i, i * 31 + 17);
                    second.setByte(i, i * 31 + 17);
                }
                first.readerIndex(7);
                second.readerIndex(7);
                encode(original, first, false);
                encode(control, second, true);
                check(ByteBufUtil.equals(original, control), "Wire differs for " + size);
                check(first.readerIndex() == second.readerIndex(), "Reader index differs");
                emit(Map.of("test", "wire_equivalence", "payloadBytes", size,
                    "bytesEqual", true, "readerIndexEqual", true));
            } finally {
                first.release(); second.release(); original.release(); control.release();
            }
        }
    }

    static void splitter() {
        int size = 2 * PacketSplitter.MAX_PAYLOAD_PER_PACKET_S2C + 31;
        FriendlyByteBuf input = heap(size);
        for (int i = 0; i < size; i++) input.setByte(i, i * 17 + 3);
        byte[] expected = ByteBufUtil.getBytes(input);
        long baseline = direct();
        int[] chunks = {0};
        FriendlyByteBuf reconstructed = new FriendlyByteBuf(Unpooled.buffer(size));
        try {
            PacketSplitter.send((player, fragment) -> {
                FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer(fragment.readableBytes() + 5));
                try {
                    encode(wire, fragment, false);
                    check(wire.readVarInt() == 2, "Bad packet type");
                    if (chunks[0]++ == 0) check(wire.readVarInt() == size, "Bad total prefix");
                    reconstructed.writeBytes(wire, wire.readableBytes());
                } finally {
                    wire.release();
                    // Explicit harness cleanup isolates the encoder's otherwise unreachable copy.
                    fragment.release();
                }
            }, input, null);
            check(input.refCnt() == 0, "Splitter did not release source on success");
            check(chunks[0] == 3, "Expected three fragments");
            check(Arrays.equals(expected, ByteBufUtil.getBytes(reconstructed)), "Split bytes differ");
            long leaked = direct() - baseline;
            check(leaked == size + 4L, "Expected payload bytes plus 4-byte total length prefix");
            emit(Map.of("test", "actual_splitter_to_codec", "fragments", chunks[0],
                "payloadBytes", size, "wireEqual", true, "unreleasedDirectBytes", leaked));
        } finally {
            reconstructed.release();
            if (input.refCnt() > 0) input.release();
        }
    }

    static void allocatorSemantics() {
        ByteBuf heap = Unpooled.buffer(4096).writeZero(4096);
        ByteBuf read = heap.readBytes(1024);
        ByteBuf copy = heap.copy();
        try {
            check(read.isDirect(), "Expected direct readBytes result");
            check(!copy.isDirect(), "Expected heap copy result for heap source");
            emit(Map.of("test", "allocator_semantics", "heapSourceIsDirect", heap.isDirect(),
                "readBytesIsDirect", read.isDirect(), "copyIsDirect", copy.isDirect(),
                "copyRefCnt", copy.refCnt(), "readBytesRefCnt", read.refCnt()));
        } finally { heap.release(); read.release(); copy.release(); }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        Field cleaner = PlatformDependent.class.getDeclaredField("CLEANER");
        cleaner.setAccessible(true);
        String cleanerClass = cleaner.get(null).getClass().getName();
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("test", "environment");
        environment.put("java", System.getProperty("java.version"));
        environment.put("netty", Version.identify().get("netty-buffer").artifactVersion());
        environment.put("cleaner", cleanerClass);
        environment.put("preferDirect", PlatformDependent.directBufferPreferred());
        environment.put("codecLocation", StructuresPayload.class.getProtectionDomain().getCodeSource().getLocation().toString());
        emit(environment);
        check(cleanerClass.endsWith("CleanerJava24Linker"), "This probe requires the reported native allocation path");
        switch (args[0]) {
            case "structures" -> memory(false);
            case "control" -> memory(true);
            case "equivalence" -> equivalence();
            case "splitter" -> splitter();
            case "allocator" -> allocatorSemantics();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }
}
