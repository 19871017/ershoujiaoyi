package com.secondhand.platform.shared.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class MediaPathGuard {
    private MediaPathGuard() {
    }

    public static Path requireRegularFileInside(Path target,
                                                Path allowedRoot,
                                                String invalidMessage,
                                                String notFoundMessage) {
        if (target == null || allowedRoot == null) {
            throw new IllegalArgumentException(invalidMessage);
        }
        Path safeTarget = target.toAbsolutePath().normalize();
        Path safeRoot = allowedRoot.toAbsolutePath().normalize();
        if (!safeTarget.startsWith(safeRoot)) {
            throw new IllegalArgumentException(invalidMessage);
        }
        if (Files.isSymbolicLink(safeTarget) || !Files.isRegularFile(safeTarget, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
        try {
            Path realRoot = safeRoot.toRealPath();
            Path realTarget = safeTarget.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                throw new IllegalArgumentException(invalidMessage);
            }
            return realTarget;
        } catch (IOException exception) {
            throw new IllegalArgumentException(notFoundMessage, exception);
        }
    }
}
