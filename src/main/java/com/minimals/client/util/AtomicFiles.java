package com.minimals.client.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe file writes: the content goes to a sibling {@code .tmp} file first and is then
 * moved over the target, so a crash / kill / full disk mid-write can never leave a truncated
 * config or index behind (the old file stays intact until the new one is complete).
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    public static void writeString(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(target.getFileName() + ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // No-op after a successful move; cleans the partial temp file if writing failed.
            Files.deleteIfExists(temp);
        }
    }
}
