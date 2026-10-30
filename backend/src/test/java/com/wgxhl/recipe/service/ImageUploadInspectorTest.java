package com.wgxhl.recipe.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.wgxhl.recipe.config.CosProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageUploadInspectorTest {
    private final ImageUploadInspector inspector = new ImageUploadInspector();

    private byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    @Test void identifiesActualEncodingInsteadOfClaimedFileType() throws Exception {
        ImageUploadInspector.InspectedImage png = inspector.inspect(image("png", 48, 48), true);
        assertEquals(".png", png.getExtension());
        assertEquals("image/png", png.getContentType());
        assertEquals("image/jpeg", inspector.inspect(image("jpeg", 48, 48), true).getContentType());
    }

    @Test void validatesRealWebpWithThePackagedImageReader() throws Exception {
        inspector.verifyImageReaders();
        byte[] webp = getClass().getResourceAsStream("/images/upload-small.webp").readAllBytes();
        assertEquals(".webp", inspector.inspect(webp, true).getExtension());
        assertEquals("image/webp", inspector.inspect(webp, true).getContentType());
        assertThrows(IOException.class, () -> inspector.inspect(java.util.Arrays.copyOf(webp, 24), true));
    }

    @Test void rejectsInvalidOversizedAndExcessivelyLargeImages() throws Exception {
        assertThrows(IOException.class, () -> inspector.inspect("not an image".getBytes(), true));
        assertThrows(IOException.class, () -> inspector.inspect(new byte[512 * 1024 + 1], true));
        assertThrows(IOException.class, () -> inspector.inspect(image("png", 1601, 1), true));
        assertThrows(IOException.class, () -> inspector.inspect(image("png", 2049, 1), false));
        assertThrows(IOException.class, () -> inspector.inspect(new byte[0], false));
    }

    @Test void uploadUsesVerifiedSuffixAndContentTypeAndKeepsRelativeKeys() throws Exception {
        COSClient client = mock(COSClient.class);
        CosProperties properties = new CosProperties();
        properties.setBucket("test-bucket");
        CosService service = new CosService(client, properties, inspector);
        byte[] bytes = image("png", 48, 48);
        String key = service.upload(new MockMultipartFile("file", "fake.webp", "image/webp", bytes), "recipe/cover");
        assertTrue(key.startsWith("recipe/cover/"));
        assertTrue(key.endsWith(".png"));
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture());
        assertEquals("image/png", request.getValue().getMetadata().getContentType());
        assertArrayEquals(bytes, request.getValue().getInputStream().readAllBytes());
    }

    @Test void rejectsBadUploadsBeforeAnyCosRequest() {
        COSClient client = mock(COSClient.class);
        CosService service = new CosService(client, new CosProperties(), inspector);
        MockMultipartFile file = new MockMultipartFile("file", "fake.webp", "image/webp", "not an image".getBytes());
        assertThrows(IOException.class, () -> service.upload(file, "recipe/cover"));
        assertThrows(IOException.class, () -> service.upload(file, "../recipe"));
        verifyNoInteractions(client);
    }
}
