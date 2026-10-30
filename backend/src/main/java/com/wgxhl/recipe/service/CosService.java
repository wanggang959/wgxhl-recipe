package com.wgxhl.recipe.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.wgxhl.recipe.config.CosProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CosService {

    private final COSClient cosClient;
    private final CosProperties cosProperties;
    private final ImageUploadInspector imageUploadInspector;

    public String upload(MultipartFile file, String folder) throws IOException {
        if (!folder.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*") || folder.length() > 80) {
            throw new IOException("上传目录无效");
        }
        byte[] bytes = file.getBytes();
        ImageUploadInspector.InspectedImage inspected = imageUploadInspector.inspect(bytes, "recipe/cover".equals(folder));
        String key = folder + "/" + UUID.randomUUID() + inspected.getExtension();

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(inspected.getContentType());

        PutObjectRequest request = new PutObjectRequest(
                cosProperties.getBucket(),
                key,
                new ByteArrayInputStream(bytes),
                metadata
        );
        cosClient.putObject(request);

        return key;
    }
}
