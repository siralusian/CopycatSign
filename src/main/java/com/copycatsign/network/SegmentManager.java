package com.copycatsign.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reassembles a chunked upload (see ImageUploadPayload) per sender, ported from Immersive Paintings'
 * SegmentManager - payloads have a size limit well below what an uncompressed picture can reach, so
 * the client splits an upload into byte[] segments and this glues them back together server-side.
 */
public class SegmentManager {

    private final Map<String, ByteArrayOutputStream> buffer = new HashMap<>();

    public Optional<byte[]> handleSegmentedPayload(String key, byte[] data, int segment, int totalSegments) {
        ByteArrayOutputStream stream = buffer.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        try {
            stream.write(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (segment + 1 == totalSegments) {
            buffer.remove(key);
            return Optional.of(stream.toByteArray());
        }
        return Optional.empty();
    }
}
