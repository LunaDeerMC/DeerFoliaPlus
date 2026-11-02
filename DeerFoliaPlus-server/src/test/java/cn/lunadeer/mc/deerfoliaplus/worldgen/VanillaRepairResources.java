package cn.lunadeer.mc.deerfoliaplus.worldgen;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real bundled vanilla bytes, with independently controllable pack provenance and overrides. */
final class VanillaRepairResources {
    final ResourceManager manager = mock(ResourceManager.class);
    private final Map<Identifier, Resource> overrides = new HashMap<>();
    private final Map<Identifier, Resource> originals = new ConcurrentHashMap<>();

    VanillaRepairResources() {
        when(this.manager.getResource(any(Identifier.class))).thenAnswer(invocation -> {
            Identifier id = invocation.getArgument(0);
            if (this.overrides.containsKey(id)) {
                return Optional.ofNullable(this.overrides.get(id));
            }
            Resource resource = this.originals.get(id);
            if (resource == null) {
                resource = resource(bytes(id), true);
                this.originals.put(id, resource);
            }
            return Optional.of(resource);
        });
    }

    void replace(Identifier id, byte[] content, boolean vanillaPack) throws IOException {
        this.overrides.put(id, resource(content, vanillaPack));
    }

    void remove(Identifier id) {
        this.overrides.put(id, null);
    }

    static byte[] bytes(Identifier id) throws IOException {
        String path = "/data/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream input = VanillaRepairResources.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing real vanilla test resource " + path);
            }
            return input.readAllBytes();
        }
    }

    private static Resource resource(byte[] bytes, boolean vanillaPack) throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.sourcePackId()).thenReturn(vanillaPack ? "vanilla" : "file/custom.zip");
        when(resource.knownPackInfo()).thenReturn(vanillaPack
                ? Optional.of(new KnownPack("minecraft", "core", "26.3")) : Optional.empty());
        when(resource.open()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        return resource;
    }
}
