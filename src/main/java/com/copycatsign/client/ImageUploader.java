package com.copycatsign.client;

import com.copycatsign.network.ChunkSender;
import com.copycatsign.network.payload.ImageUploadPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only: splits a picked file's raw bytes into ImageUploadPayload segments and sends them. */
public final class ImageUploader {

    private ImageUploader() {
    }

    public static void send(byte[] data) {
        int total = ChunkSender.totalSegments(data);
        for (int segment = 0; segment < total; segment++) {
            PacketDistributor.sendToServer(new ImageUploadPayload(ChunkSender.segment(data, segment), segment, total));
        }
    }
}
