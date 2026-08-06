package com.copycatsign.network;

import java.util.Arrays;

/** Shared chunking constant/helper for payloads that split byte[] data across several packets (see
 * ImageUploadPayload and ImageDataResponsePayload) - packets have a size cap well below what an
 * image can reach. */
public final class ChunkSender {

    public static final int MAX_CHUNK_SIZE = 32_000;

    private ChunkSender() {
    }

    public static int totalSegments(byte[] data) {
        return Math.max(1, (data.length + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE);
    }

    public static byte[] segment(byte[] data, int segment) {
        int from = segment * MAX_CHUNK_SIZE;
        int to = Math.min(data.length, from + MAX_CHUNK_SIZE);
        return Arrays.copyOfRange(data, from, to);
    }
}
