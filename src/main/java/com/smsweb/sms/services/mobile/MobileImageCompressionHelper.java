package com.smsweb.sms.services.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

/**
 * NEW, isolated helper — used ONLY by the mobile student profile-photo
 * self-edit upload. Deliberately does not touch FileHandleHelper.java,
 * which is shared by school/employee/customer image uploads (a 25KB target
 * is far too small for a school logo or staff photo, so this stays
 * completely separate rather than changing shared behaviour).
 *
 * Storage convention is kept identical to the existing web-app flow so
 * nothing downstream needs to change: same folder
 * (student.image.storage.path), same "save filename into Student.pic"
 * contract, same /api/v1/student/pic/{filename} serving endpoint. The only
 * difference is the bytes are resized + re-encoded down to <=25KB before
 * being written to disk, and always saved as a .jpg regardless of the
 * source format.
 */
@Component
public class MobileImageCompressionHelper {

    private static final Logger log = LoggerFactory.getLogger(MobileImageCompressionHelper.class);

    private static final long TARGET_MAX_BYTES = 25 * 1024; // 25KB
    private static final int[] MAX_DIMENSION_ATTEMPTS = {480, 320, 240, 160};
    private static final float[] QUALITY_ATTEMPTS = {0.85f, 0.7f, 0.55f, 0.4f, 0.25f, 0.15f, 0.08f};

    @Value("${student.image.storage.path}")
    private String studentImagePath;

    /**
     * Validates, resizes + compresses to <=25KB, and saves the image.
     * Returns the generated filename (to be stored as-is in Student.pic),
     * or throws IllegalArgumentException / IOException on failure — caller
     * is expected to translate those into a 400 response.
     */
    public String compressAndSave(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No image file provided");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Uploaded file is not an image");
        }

        BufferedImage source = ImageIO.read(file.getInputStream());
        if (source == null) {
            throw new IllegalArgumentException("Unsupported or corrupt image format");
        }

        byte[] compressed = null;
        outer:
        for (int maxDim : MAX_DIMENSION_ATTEMPTS) {
            BufferedImage resized = resize(source, maxDim);
            for (float quality : QUALITY_ATTEMPTS) {
                byte[] candidate = encodeJpeg(resized, quality);
                if (candidate.length <= TARGET_MAX_BYTES) {
                    compressed = candidate;
                    break outer;
                }
            }
        }
        if (compressed == null) {
            // Fall back to the smallest/lowest-quality attempt even if it
            // slightly exceeds the target, rather than failing the upload.
            BufferedImage smallest = resize(source, MAX_DIMENSION_ATTEMPTS[MAX_DIMENSION_ATTEMPTS.length - 1]);
            compressed = encodeJpeg(smallest, QUALITY_ATTEMPTS[QUALITY_ATTEMPTS.length - 1]);
            log.warn("Could not reach {} bytes target, saving best-effort at {} bytes",
                    TARGET_MAX_BYTES, compressed.length);
        }

        String fileName = UUID.randomUUID() + "_profile.jpg";
        Path dir = Paths.get(studentImagePath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path target = dir.resolve(fileName);
        Files.write(target, compressed);
        log.info("Saved compressed profile photo: {} ({} bytes)", fileName, compressed.length);
        return fileName;
    }

    private BufferedImage resize(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        // JPEG has no alpha channel — flatten onto a white background so
        // transparent PNG uploads don't turn black.
        BufferedImage out = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), params);
            }
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
