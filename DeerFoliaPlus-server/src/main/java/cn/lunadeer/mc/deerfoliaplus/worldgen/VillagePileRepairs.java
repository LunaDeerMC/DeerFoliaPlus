package cn.lunadeer.mc.deerfoliaplus.worldgen;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BlockPileFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Scope the shallow-world correction to the selected, unchanged vanilla village feature. */
public final class VillagePileRepairs {
    private static final Map<String, StructureRepair> REPAIRS = Map.of(
            "pile_hay", StructureRepair.VILLAGE_HAY_PILE,
            "pile_pumpkin", StructureRepair.VILLAGE_PUMPKIN_PILE,
            "pile_ice", StructureRepair.VILLAGE_ICE_PILE,
            "pile_melon", StructureRepair.VILLAGE_MELON_PILE,
            "pile_snow", StructureRepair.VILLAGE_SNOW_PILE);
    private static final ThreadLocal<BlockPileFeature> ACTIVE = new ThreadLocal<>();

    private VillagePileRepairs() {
    }

    public static boolean place(Holder<PlacedFeature> feature, ResourceManager resources,
                                WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return place(feature, resources, level, generator, random, origin, StructureGenerationFixes::enabled);
    }

    static boolean place(Holder<PlacedFeature> feature, ResourceManager resources,
                         WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                         Predicate<StructureRepair> enabled) {
        var key = feature.unwrapKey();
        if (key.isEmpty() || !"minecraft".equals(key.get().identifier().getNamespace())) {
            return feature.value().place(level, generator, random, origin);
        }
        String name = key.get().identifier().getPath();
        StructureRepair repair = REPAIRS.get(name);
        if (repair == null || !enabled.test(repair)
                || !(feature.value().feature().value() instanceof BlockPileFeature pile)
                || !feature.value().placement().isEmpty()
                || feature.value().feature().unwrapKey().isEmpty()
                || !key.get().identifier().equals(feature.value().feature().unwrapKey().orElseThrow().identifier())
                || !StructureGenerationFixes.matchesVanillaResource(resources,
                    Identifier.withDefaultNamespace("worldgen/placed_feature/" + name + ".json"))
                || !StructureGenerationFixes.matchesVanillaResource(resources,
                    Identifier.withDefaultNamespace("worldgen/feature/" + name + ".json"))) {
            return feature.value().place(level, generator, random, origin);
        }
        BlockPileFeature previous = ACTIVE.get();
        ACTIVE.set(pile);
        try {
            return feature.value().place(level, generator, random, origin);
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    /** Keep one support block above minY; the feature writes only at originY and originY + 1. */
    public static int minimumClearance(BlockPileFeature feature) {
        return ACTIVE.get() == feature ? 1 : 5;
    }
}
