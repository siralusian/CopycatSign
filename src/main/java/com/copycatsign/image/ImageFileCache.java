package com.copycatsign.image;

import com.copycatsign.CopycatSign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Flat-file store for uploaded custom pictures' encoded PNG bytes, keyed by content hash (see
 * ServerImageStore). Deliberately just raw file I/O, no in-memory cache layer - uploads are rare
 * (player-triggered) and reads only happen when a client requests an image it doesn't have yet
 * (ImageRequestPayload, see F3.3), so there's no hot path here worth caching.
 */
public final class ImageFileCache {

    private final Path directory;

    public ImageFileCache(Path directory) {
        this.directory = directory;
    }

    private Path pathFor(String hash) {
        return directory.resolve(hash + ".png");
    }

    public boolean exists(String hash) {
        return Files.isRegularFile(pathFor(hash));
    }

    public Optional<byte[]> get(String hash) {
        Path path = pathFor(hash);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not read cached image {}", hash, e);
            return Optional.empty();
        }
    }

    public void set(String hash, byte[] data) {
        try {
            Files.createDirectories(directory);
            Files.write(pathFor(hash), data);
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not write cached image {}", hash, e);
        }
    }

    public void delete(String hash) {
        try {
            Files.deleteIfExists(pathFor(hash));
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not delete cached image {}", hash, e);
        }
    }
}
