package com.copycatsign.client;

import com.copycatsign.network.payload.ImageDataResponsePayload;
import com.copycatsign.network.payload.ImageUploadResultPayload;
import com.copycatsign.network.payload.OpenPictureEditorPayload;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Nutzer-Fund (Live-Test auf echtem Dedicated Server, Singleplayer funktionierte): "keine Reaktion"
 * bei leerer-Hand-Rechtsklick. Ursache: OpenPictureEditorPayload/ImageUploadResultPayload/
 * ImageDataResponsePayload wurden bisher NUR in CopycatSignClient (Dist.CLIENT-gated) registriert -
 * auf einem echten Dedicated Server läuft diese Klasse nie, der Server kennt den Kanal für diese 3
 * Typen also gar nicht und PacketDistributor.sendToPlayer(...) verpufft wirkungslos. Im Singleplayer
 * lief unbemerkt alles im selben Prozess (physische Seite ist dort immer CLIENT), deshalb fiel es
 * beim bisherigen Testen nie auf.
 * <p>
 * Fix: die Registrierung wandert nach CopycatSignNetwork (common, läuft auf beiden Seiten), der
 * Handler dort fasst die Payload-Klassen aber nur über diese reine Datenhalter-Klasse an - KEIN
 * net.minecraft.client.*-Import hier, damit sie auch beim Laden auf einem Dedicated Server
 * unbedenklich ist. Die eigentliche Bildschirm-/Textur-Arbeit (braucht Minecraft.getInstance(),
 * PictureEditorScreen, ClientImageManager) passiert weiterhin ausschließlich in
 * CopycatSignClient (Dist.CLIENT), das die hier abgelegten Werte per Client-Tick abholt.
 */
public final class ClientPictureBridge {

    private ClientPictureBridge() {
    }

    private static volatile OpenPictureEditorPayload pendingOpen = null;
    private static volatile ImageUploadResultPayload pendingUploadResult = null;
    private static final Queue<ImageDataResponsePayload> PENDING_IMAGE_DATA = new ConcurrentLinkedQueue<>();

    public static void setPendingOpen(OpenPictureEditorPayload payload) {
        pendingOpen = payload;
    }

    public static OpenPictureEditorPayload takePendingOpen() {
        OpenPictureEditorPayload payload = pendingOpen;
        pendingOpen = null;
        return payload;
    }

    public static void setPendingUploadResult(ImageUploadResultPayload payload) {
        pendingUploadResult = payload;
    }

    public static ImageUploadResultPayload takePendingUploadResult() {
        ImageUploadResultPayload payload = pendingUploadResult;
        pendingUploadResult = null;
        return payload;
    }

    /** Warteschlange statt Einzel-Slot: ein Bild kommt als mehrere Segmente an (siehe ChunkSender) -
     * ein Einzel-Slot würde bei mehreren Segmenten pro Tick alle bis auf das letzte verwerfen. */
    public static void queueImageData(ImageDataResponsePayload payload) {
        PENDING_IMAGE_DATA.add(payload);
    }

    public static ImageDataResponsePayload pollImageData() {
        return PENDING_IMAGE_DATA.poll();
    }
}
