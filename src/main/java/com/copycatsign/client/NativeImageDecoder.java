package com.copycatsign.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * NativeImage.read(byte[]) is unsafe for anything but tiny files: it copies the WHOLE array onto
 * LWJGL's MemoryStack (default 64 KiB per thread, see MemoryStack.DEFAULT_STACK_SIZE), so any picture
 * larger than that overflows it with "OutOfMemoryError: Out of stack space" - reproducible on any
 * thread, unrelated to JVM heap size. Vanilla's own read(InputStream) overload avoids this by copying
 * into a regular MemoryUtil-allocated (off-heap, not stack) buffer instead; this does the same for the
 * byte[] we already have in hand from disk/network.
 */
public final class NativeImageDecoder {

    private NativeImageDecoder() {
    }

    public static NativeImage read(byte[] bytes) throws IOException {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes);
            buffer.rewind();
            return NativeImage.read(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
