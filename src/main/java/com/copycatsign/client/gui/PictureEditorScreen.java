package com.copycatsign.client.gui;

import com.copycatsign.CopycatSign;
import com.copycatsign.CopycatSignConfig;
import com.copycatsign.block.PictureThickness;
import com.copycatsign.client.ImageUploader;
import com.copycatsign.client.NativeImageDecoder;
import com.copycatsign.network.payload.ImageUploadResultPayload;
import com.copycatsign.network.payload.PictureEditPayload;
import com.copycatsign.network.payload.PictureThicknessPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/**
 * The picture editor (see ROADMAP.md Feature 3 / F3.4): pick a file via the OS's native file dialog
 * (TinyFileDialogs - vanilla itself has no file-picker UI, only "open this folder externally"), preview
 * it, upload it, and adjust pan/zoom on whatever picture the sign currently has (freshly uploaded or
 * from a previous session). Opened via OpenPictureEditorPayload (server-routed, see
 * AbstractPictureBlock#useItemOn) rather than directly from the block interaction, since that's common
 * code and can't reference this client-only class.
 */
public class PictureEditorScreen extends Screen {

    private static final ResourceLocation PREVIEW_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(CopycatSign.MOD_ID, "editor_preview");

    private final BlockPos pos;
    private String currentHash;
    private float panX;
    private float panY;
    private float zoom;
    private PictureThickness thickness;

    private byte[] pendingFileBytes;
    private ResourceLocation previewTexture;
    private int previewWidth;
    private int previewHeight;
    private boolean uploading;
    private Component statusMessage;

    private Button uploadButton;
    private Button thicknessButton;

    public PictureEditorScreen(BlockPos pos, String currentHash, float panX, float panY, float zoom, String thickness) {
        super(Component.translatable("copycatsign.editor.title"));
        this.pos = pos;
        this.currentHash = currentHash;
        this.panX = panX;
        this.panY = panY;
        this.zoom = zoom;
        this.thickness = PictureThickness.valueOf(thickness);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int previewBottom = 40 + 110;

        addRenderableWidget(Button.builder(Component.translatable("copycatsign.editor.choose_file"), b -> pickFile())
            .bounds(centerX - 100, previewBottom + 10, 200, 20)
            .build());

        uploadButton = Button.builder(Component.translatable("copycatsign.editor.upload"), b -> upload())
            .bounds(centerX - 100, previewBottom + 34, 200, 20)
            .build();
        uploadButton.active = false;
        addRenderableWidget(uploadButton);

        int sliderY = previewBottom + 64;
        // panX/panY are stored as a block-fraction offset (-0.5..0.5 - the picture's center can be
        // pushed up to the block's own edges) and zoom as a plain size multiplier (0.5..2.0), but both
        // sliders work in the SAME -100%..100% display range PanZoomSlider expects, converting to each
        // field's own actual range only in these callbacks: pan is a direct *200/÷200 scale, zoom is
        // exponential (100% = double size, -100% = half), matching "like Paint would" - a linear percent
        // would make -100% zero instead of half.
        addRenderableWidget(new PanZoomSlider(centerX - 100, sliderY, 200, 20,
            "copycatsign.editor.pan_x", panX * 200.0, percent -> updatePan((float) (percent / 200.0), panY)));
        addRenderableWidget(new PanZoomSlider(centerX - 100, sliderY + 24, 200, 20,
            "copycatsign.editor.pan_y", panY * 200.0, percent -> updatePan(panX, (float) (percent / 200.0))));
        addRenderableWidget(new PanZoomSlider(centerX - 100, sliderY + 48, 200, 20,
            "copycatsign.editor.zoom", 100.0 * Math.log(zoom) / Math.log(2.0),
            percent -> updateZoom((float) Math.pow(2.0, percent / 100.0))));

        thicknessButton = Button.builder(thicknessLabel(), b -> cycleThickness())
            .bounds(centerX - 100, sliderY + 78, 200, 20)
            .build();
        addRenderableWidget(thicknessButton);
    }

    private Component thicknessLabel() {
        return Component.translatable("copycatsign.editor.thickness",
            Component.translatable("copycatsign.thickness." + thickness.getSerializedName()));
    }

    private void cycleThickness() {
        PictureThickness[] values = PictureThickness.values();
        thickness = values[(thickness.ordinal() + 1) % values.length];
        thicknessButton.setMessage(thicknessLabel());
        PacketDistributor.sendToServer(new PictureThicknessPayload(pos, thickness.name()));
    }

    @Override
    public void onClose() {
        if (previewTexture != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexture);
            previewTexture = null;
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);

        int boxSize = 100;
        int boxX = width / 2 - boxSize / 2;
        int boxY = 40;
        guiGraphics.fill(boxX - 1, boxY - 1, boxX + boxSize + 1, boxY + boxSize + 1, 0xFF555555);
        if (previewTexture != null) {
            guiGraphics.blit(previewTexture, boxX, boxY, boxSize, boxSize, 0.0F, 0.0F, previewWidth, previewHeight, previewWidth, previewHeight);
        } else {
            guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0xFF222222);
            guiGraphics.drawCenteredString(font, Component.translatable("copycatsign.editor.no_image"), width / 2, boxY + boxSize / 2 - 4, 0xAAAAAA);
        }

        if (statusMessage != null) {
            guiGraphics.drawCenteredString(font, statusMessage, width / 2, boxY + boxSize + 42, 0xFFFF55);
        }
    }

    private void pickFile() {
        Thread thread = new Thread(this::runFileDialog, "copycatsign-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private void runFileDialog() {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.flip();
            path = TinyFileDialogs.tinyfd_openFileDialog(
                "Copycat Sign - " + Component.translatable("copycatsign.editor.choose_file").getString(),
                null, filters, "PNG/JPEG", false);
        } catch (Exception e) {
            CopycatSign.LOGGER.error("file dialog failed", e);
            return;
        }
        if (path == null) {
            return;
        }
        Path filePath = Path.of(path);

        // Reject on file SIZE alone before reading a single byte of it - a multi-hundred-MB (or
        // multi-GB) file picked by mistake must not get Files.readAllBytes'd into a heap array first
        // and rejected only afterwards; that's exactly the kind of avoidable OOM this guards against.
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not stat picked file {}", path, e);
            return;
        }
        if (fileSize > CopycatSignConfig.maxImageBytes()) {
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("copycatsign.upload.too_large"));
            return;
        }

        // Also reject on PIXEL DIMENSIONS before decoding - ImageIO's reader can read just the
        // width/height out of a file's header without decoding its full pixel buffer, so a
        // huge-resolution (but small-file-size, e.g. a giant flat-color PNG) image is still rejected
        // cheaply instead of fully decoding it into a NativeImage first.
        try (var stream = ImageIO.createImageInputStream(filePath.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                reader.setInput(stream);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                reader.dispose();
                int maxDimension = CopycatSignConfig.maxImageDimension();
                if (width > maxDimension || height > maxDimension) {
                    Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("copycatsign.upload.too_big_dimensions"));
                    return;
                }
            }
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not read image header of {}", path, e);
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("copycatsign.upload.invalid_image"));
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            CopycatSign.LOGGER.error("could not read picked file {}", path, e);
            return;
        }

        // Decoding happens on this background thread (not the render thread) so a slow decode never
        // stalls a frame - GL-touching work (DynamicTexture registration) still has to hop back via
        // Minecraft.execute below. Uses NativeImageDecoder rather than NativeImage.read(byte[])
        // directly: that overload copies the whole file onto LWJGL's small per-thread MemoryStack
        // (default 64 KiB) instead of a regular buffer, overflowing it - see NativeImageDecoder's
        // javadoc for the full story (this crashed reproducibly for any file over ~64 KB, on any
        // thread, regardless of JVM heap size).
        NativeImage image;
        try {
            image = NativeImageDecoder.read(bytes);
        } catch (IOException e) {
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("copycatsign.upload.invalid_image"));
            return;
        }

        // Second, unconditional dimension check on the ACTUAL decoded image - the earlier ImageIO
        // header check above silently does nothing when ImageIO has no reader for the file's exact
        // format (some JPEG variants, phone camera formats, ...), letting an oversized image straight
        // through to here since NativeImage's own decoder (STB) reads a broader range of formats than
        // ImageIO does. This check doesn't depend on format detection at all, so it can't be silently
        // skipped the same way - reject and free it immediately, before ever creating the
        // DynamicTexture (the actually expensive GPU upload step).
        int maxDimension = CopycatSignConfig.maxImageDimension();
        if (image.getWidth() > maxDimension || image.getHeight() > maxDimension) {
            image.close();
            Minecraft.getInstance().execute(() -> statusMessage = Component.translatable("copycatsign.upload.too_big_dimensions"));
            return;
        }

        Minecraft.getInstance().execute(() -> onFilePicked(bytes, image));
    }

    private void onFilePicked(byte[] bytes, NativeImage image) {
        if (previewTexture != null) {
            Minecraft.getInstance().getTextureManager().release(previewTexture);
        }
        Minecraft.getInstance().getTextureManager().register(PREVIEW_TEXTURE_ID, new DynamicTexture(image));
        previewTexture = PREVIEW_TEXTURE_ID;
        previewWidth = image.getWidth();
        previewHeight = image.getHeight();
        pendingFileBytes = bytes;
        statusMessage = null;
        uploadButton.active = true;
    }

    private void upload() {
        if (pendingFileBytes == null || uploading) {
            return;
        }
        uploading = true;
        uploadButton.active = false;
        statusMessage = Component.translatable("copycatsign.editor.uploading");
        ImageUploader.send(pendingFileBytes);
    }

    /** Called by CopycatSignClient's ImageUploadResultPayload handler if this screen is still open. */
    public void onUploadResult(ImageUploadResultPayload payload) {
        uploading = false;
        if (!payload.success()) {
            statusMessage = Component.translatable(payload.hashOrErrorKey());
            uploadButton.active = pendingFileBytes != null;
            return;
        }
        currentHash = payload.hashOrErrorKey();
        panX = 0.0F;
        panY = 0.0F;
        zoom = 1.0F;
        statusMessage = Component.translatable("copycatsign.editor.upload_done");
        PacketDistributor.sendToServer(new PictureEditPayload(pos, currentHash, panX, panY, zoom, true));
    }

    private void updatePan(float newPanX, float newPanY) {
        panX = newPanX;
        panY = newPanY;
        sendPanZoomUpdate();
    }

    private void updateZoom(float newZoom) {
        zoom = newZoom;
        sendPanZoomUpdate();
    }

    private void sendPanZoomUpdate() {
        if (currentHash != null && !currentHash.isEmpty()) {
            PacketDistributor.sendToServer(new PictureEditPayload(pos, "", panX, panY, zoom, false));
        }
    }

    /** A slider whose displayed/callback value is always a -100..100 percentage - AbstractSliderButton's
     * own {@code value} field is fixed to the 0..1 range, so this just affinely maps onto/off of that. */
    private static class PanZoomSlider extends AbstractSliderButton {

        private final String translationKey;
        private final DoubleConsumer onChangePercent;

        PanZoomSlider(int x, int y, int width, int height, String translationKey, double initialPercent, DoubleConsumer onChangePercent) {
            super(x, y, width, height, Component.empty(), Math.clamp(initialPercent / 200.0 + 0.5, 0.0, 1.0));
            this.translationKey = translationKey;
            this.onChangePercent = onChangePercent;
            updateMessage();
        }

        private double percent() {
            return (value - 0.5) * 200.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(translationKey, Math.round(percent())));
        }

        @Override
        protected void applyValue() {
            onChangePercent.accept(percent());
        }
    }
}
