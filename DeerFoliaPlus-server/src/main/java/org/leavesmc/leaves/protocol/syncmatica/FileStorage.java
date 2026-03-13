package org.leavesmc.leaves.protocol.syncmatica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.UUID;

public class FileStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");

    private final File litematicDir;
    private final File tempDir;

    public FileStorage(final File baseDir) {
        this.litematicDir = new File(baseDir, "litematica");
        this.tempDir = new File(baseDir, "temp");

        if (!litematicDir.exists()) {
            litematicDir.mkdirs();
        }
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }

    public File getFile(final ServerPlacement placement) {
        final File file = new File(litematicDir, placement.getHash() + ".litematic");
        if (file.exists()) {
            return file;
        }
        return null;
    }

    public boolean hasFile(final ServerPlacement placement) {
        return new File(litematicDir, placement.getHash() + ".litematic").exists();
    }

    public File createTempFile(final ServerPlacement placement) {
        final File tempFile = new File(tempDir, UUID.randomUUID().toString() + ".tmp");
        try {
            if (tempFile.createNewFile()) {
                return tempFile;
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to create temp file for {}", placement.getId(), e);
        }
        return null;
    }

    public boolean finalizeFile(final ServerPlacement placement, final File tempFile) {
        final File targetFile = new File(litematicDir, placement.getHash() + ".litematic");
        if (targetFile.exists()) {
            tempFile.delete();
            return true;
        }
        return tempFile.renameTo(targetFile);
    }

    public void cleanTemp() {
        final File[] files = tempDir.listFiles();
        if (files != null) {
            for (final File file : files) {
                file.delete();
            }
        }
    }
}
