package com.wgxhl.recipe.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

@Component
public class ImageUploadInspector {
    @PostConstruct
    public void verifyImageReaders() {
        if (!ImageIO.getImageReadersByFormatName("webp").hasNext()) {
            throw new IllegalStateException("WebP image reader is unavailable");
        }
    }

    public InspectedImage inspect(byte[] bytes, boolean cover) throws IOException {
        int limit = (cover ? 512 : 1024) * 1024;
        if (bytes.length == 0 || bytes.length > limit) {
            throw new IOException(cover ? "封面需压缩到512KB以内" : "图片需压缩到1MB以内");
        }
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("仅支持有效的JPEG、PNG或WebP图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!("jpeg".equals(format) || "png".equals(format) || "webp".equals(format))) {
                    throw new IOException("仅支持JPEG、PNG或WebP图片");
                }
                int maxEdge = cover ? 1600 : 2048;
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxEdge || height > maxEdge) {
                    throw new IOException("图片边长需控制在" + maxEdge + "像素以内");
                }
                if (reader.read(0) == null) throw new IOException("图片内容无法解码");
                return new InspectedImage("jpeg".equals(format) ? ".jpg" : "." + format, "image/" + format);
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException ex) {
            throw new IOException("图片内容无法解码", ex);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class InspectedImage {
        private final String extension;
        private final String contentType;
    }
}
