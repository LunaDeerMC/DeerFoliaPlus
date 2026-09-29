package org.leavesmc.leaves.bot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface IPlayerDataStorage {

    record SaveSnapshot(String listKey, String uuid, CompoundTag playerData, @Nullable CompoundTag listEntry) {
    }

    void save(Player player);

    default Optional<SaveSnapshot> createSnapshot(Player player) {
        return Optional.empty();
    }

    default void save(SaveSnapshot snapshot) {
        throw new UnsupportedOperationException("Snapshot save is not supported by this storage");
    }

    Optional<ValueInput> load(Player player, ProblemReporter problemReporter);
}
