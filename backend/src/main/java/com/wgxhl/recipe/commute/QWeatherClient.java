package com.wgxhl.recipe.commute;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

@Component
public class QWeatherClient {
    private final ObjectMapper json;
    private final String host, developer, project, keyId, privateKeyPath;

    public QWeatherClient(ObjectMapper json, @Value("${app.qweather.host:}") String host,
                          @Value("${app.qweather.developer-id:}") String developer,
                          @Value("${app.qweather.project-id:}") String project,
                          @Value("${app.qweather.key-id:}") String keyId,
                          @Value("${app.qweather.private-key-path:}") String privateKeyPath) {
        this.json = json;
        this.host = host; this.developer = developer; this.project = project;
        this.keyId = keyId; this.privateKeyPath = privateKeyPath;
    }

    public JsonNode hourly(double latitude, double longitude) throws Exception {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        String address = String.format(Locale.ROOT, "https://%s/weather/v1/hourly/%.2f/%.2f?hours=48&localTime=true&lang=zh", host, latitude, longitude);
        JsonNode result = request(address);
        if (!result.path("hours").isArray()) throw new IllegalStateException("Invalid weather response");
        return result;
    }

    public JsonNode daily(double latitude,double longitude) throws Exception {
        if(!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180)
            throw new IllegalArgumentException("Invalid coordinates");
        String address=String.format(Locale.ROOT,"https://%s/weather/v1/daily/%.2f/%.2f?days=3&localTime=true&lang=zh",host,latitude,longitude);
        JsonNode result=request(address);
        if(!result.path("days").isArray()) throw new IllegalStateException("Invalid weather response");
        return result;
    }

    public JsonNode searchCity(String keyword) throws Exception {
        if (keyword == null || keyword.trim().isEmpty() || keyword.length() > 50)
            throw new IllegalArgumentException("请输入城市或区县名称");
        String address = "https://" + host + "/geo/v2/city/lookup?range=cn&number=10&lang=zh&location="
                + URLEncoder.encode(keyword.trim(), "UTF-8");
        JsonNode result = request(address);
        if (!result.path("location").isArray()) throw new IllegalStateException("Invalid city response");
        return result.path("location");
    }

    private JsonNode request(String address) throws Exception {
        if (!host.matches("[a-zA-Z0-9.-]+\\.qweatherapi\\.com") || developer.isEmpty()
                || project.isEmpty() || keyId.isEmpty() || privateKeyPath.isEmpty()) {
            throw new IllegalStateException("Weather service is not configured");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization", "Bearer " + token());
        connection.setRequestProperty("Accept-Encoding", "gzip");
        try {
            int status = connection.getResponseCode();
            if (status == 400 && address.contains("/geo/v2/city/lookup"))
                throw new IllegalArgumentException("未找到地区，请输入城市或区县名称；小区和大厦请使用高德分享链接");
            if (status != 200) throw new IllegalStateException("Weather API HTTP " + status);
            try (InputStream raw = connection.getInputStream();
                 InputStream body = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw;
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read; int total = 0;
                while ((read = body.read(buffer)) != -1) {
                    total += read;
                    if (total > 1024 * 1024) throw new IllegalStateException("Weather response too large");
                    bytes.write(buffer, 0, read);
                }
                return json.readTree(bytes.toByteArray());
            }
        } finally { connection.disconnect(); }
    }

    private String token() throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(privateKeyPath)), StandardCharsets.US_ASCII);
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        PrivateKey key = KeyFactory.getInstance("Ed25519", "BC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        long issued = Instant.now().getEpochSecond() - 30;
        String header = json.writeValueAsString(json.createObjectNode().put("alg", "EdDSA").put("kid", keyId));
        String payload = json.writeValueAsString(json.createObjectNode().put("iss", developer).put("sub", project).put("iat", issued).put("exp", issued + 900));
        String input = base64(header.getBytes(StandardCharsets.UTF_8)) + "." + base64(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("Ed25519", "BC");
        signature.initSign(key); signature.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + base64(signature.sign());
    }

    private static String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
