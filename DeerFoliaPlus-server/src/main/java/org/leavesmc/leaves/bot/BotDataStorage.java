package org.leavesmc.leaves.bot;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class BotDataStorage implements IPlayerDataStorage {

    private static final LevelResource BOT_DATA_DIR = new LevelResource("fakeplayerdata");
    private static final LevelResource BOT_LIST_FILE = new LevelResource("fakeplayer.dat");

    private static final Logger LOGGER = LogUtils.getLogger();
    private final File botDir;
    private final File botListFile;

    private final Object savedBotListLock = new Object();
    private CompoundTag savedBotList;

    public BotDataStorage(LevelStorageSource.@NotNull LevelStorageAccess session) {
        this.botDir = session.getLevelPath(BOT_DATA_DIR).toFile();
        this.botListFile = session.getLevelPath(BOT_LIST_FILE).toFile();
        this.botDir.mkdirs();

        this.savedBotList = new CompoundTag();
        if (this.botListFile.exists() && this.botListFile.isFile()) {
            try {
                Optional.of(NbtIo.readCompressed(this.botListFile.toPath(), NbtAccounter.unlimitedHeap())).ifPresent(tag -> this.savedBotList = tag);
            } catch (Exception exception) {
                BotDataStorage.LOGGER.warn("Failed to load player data list");
            }
        }
    }

    @Override
    public void save(Player player) {
        this.createSnapshot(player).ifPresent(this::save);
    }

    @Override
    public Optional<SaveSnapshot> createSnapshot(Player player) {
        try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, player.registryAccess());
            player.saveWithoutId(tagValueOutput);

            String listKey = player.getScoreboardName();
            CompoundTag listEntry = null;
            if (player instanceof ServerBot bot) {
                listKey = bot.createState.realName();
                listEntry = new CompoundTag();
                listEntry.putString("name", bot.createState.name());
                listEntry.putString("uuid", bot.getUUID().toString());
                listEntry.putBoolean("resume", bot.resume);
                if (bot.createPlayer != null) {
                    listEntry.putString("creator", bot.createPlayer.toString());
                }
            }

            return Optional.of(new SaveSnapshot(listKey, player.getStringUUID(), tagValueOutput.buildResult(), listEntry));
        } catch (Exception exception) {
            BotDataStorage.LOGGER.warn("Failed to create fakeplayer save snapshot for {}", player.getScoreboardName(), exception);
            return Optional.empty();
        }
    }

    @Override
    public void save(SaveSnapshot snapshot) {
        boolean saved = true;
        File file = new File(this.botDir, snapshot.uuid() + ".dat");
        try {
            if (file.exists() && file.isFile() && !file.delete()) {
                throw new IOException("Failed to delete file: " + file);
            }
            if (!file.createNewFile()) {
                throw new IOException("Failed to create nbt file: " + file);
            }
            NbtIo.writeCompressed(snapshot.playerData(), file.toPath());
        } catch (Exception exception) {
            BotDataStorage.LOGGER.warn("Failed to save fakeplayer data for {}", snapshot.listKey(), exception);
            saved = false;
        }

        if (saved && snapshot.listEntry() != null) {
            synchronized (this.savedBotListLock) {
                this.savedBotList.put(snapshot.listKey(), snapshot.listEntry());
                this.saveBotListLocked();
            }
        }
    }

    @Override
    public Optional<ValueInput> load(Player player, ProblemReporter problemReporter) {
        return this.load(player.getScoreboardName(), player.getStringUUID()).map((nbt) -> {
            ValueInput valueInput = TagValueInput.create(problemReporter, player.registryAccess(), nbt);
            player.load(valueInput);
            return valueInput;
        });
    }

    private Optional<CompoundTag> load(String name, String uuid) {
        File file = new File(this.botDir, uuid + ".dat");

        if (file.exists() && file.isFile()) {
            try {
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap()));
                if (!file.delete()) {
                    throw new IOException("Failed to delete fakeplayer data");
                }
                synchronized (this.savedBotListLock) {
                    this.savedBotList.remove(name);
                    this.saveBotListLocked();
                }
                return optional;
            } catch (Exception exception) {
                BotDataStorage.LOGGER.warn("Failed to load fakeplayer data for {}", name);
            }
        }

        return Optional.empty();
    }

    private void saveBotListLocked() {
        try {
            if (this.botListFile.exists() && this.botListFile.isFile()) {
                if (!this.botListFile.delete()) {
                    throw new IOException("Failed to delete file: " + this.botListFile);
                }
            }
            if (!this.botListFile.createNewFile()) {
                throw new IOException("Failed to create nbt file: " + this.botListFile);
            }
            NbtIo.writeCompressed(this.savedBotList, this.botListFile.toPath());
        } catch (Exception exception) {
            BotDataStorage.LOGGER.warn("Failed to save player data list");
        }
    }

    public CompoundTag getSavedBotList() {
        synchronized (this.savedBotListLock) {
            return this.savedBotList.copy();
        }
    }
}
