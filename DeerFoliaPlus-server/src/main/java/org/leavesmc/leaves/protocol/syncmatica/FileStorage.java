package org.leavesmc.leaves.protocol.syncmatica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class FileStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("Syncmatica");
    private static final Pattern SAFE_HASH_PATTERN = Pattern.compile("^[a-f0-9]{32}$");

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
        final File file = resolveTargetFile(placement.getHash());
        if (file == null) {
            return null;
        }
        if (file.exists()) {
            return file;
        }
        return null;
    }

    public boolean hasFile(final ServerPlacement placement) {
        final File file = resolveTargetFile(placement.getHash());
        return file != null && file.exists();
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
        final File targetFile = resolveTargetFile(placement.getHash());
        if (targetFile == null) {
            tempFile.delete();
            return false;
        }
        if (targetFile.exists()) {
            tempFile.delete();
            return true;
        }
        return tempFile.renameTo(targetFile);
    }

    private File resolveTargetFile(final String hash) {
        if (!isValidHash(hash)) {
            LOGGER.warn("Rejected invalid hash format: {}", hash);
            return null;
        }
        final File file = new File(litematicDir, hash + ".litematic");
        if (!isPathSafe(file)) {
            LOGGER.warn("Path traversal attempt blocked for hash: {}", hash);
            return null;
        }
        return file;
    }

    static boolean isValidHash(final String hash) {
        return hash != null && SAFE_HASH_PATTERN.matcher(hash).matches();
    }

    private boolean isPathSafe(final File file) {
        try {
            final String canonicalPath = file.getCanonicalPath();
            final String baseCanonicalPath = litematicDir.getCanonicalPath();
            return canonicalPath.startsWith(baseCanonicalPath + File.separator)
                || canonicalPath.equals(baseCanonicalPath);
        } catch (final IOException e) {
            LOGGER.error("Failed to resolve canonical path", e);
            return false;
        }
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
