package com.wgxhl.recipe.commute;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves only the official short-link host; it never follows a server-provided redirect. */
public final class AmapShareParser {
    private AmapShareParser() { }

    public static Location resolve(String text) throws Exception {
        URI source = URI.create(text == null ? "" : text.trim());
        if (!"https".equalsIgnoreCase(source.getScheme()) || !"surl.amap.com".equalsIgnoreCase(source.getHost())
                || source.getPort() != -1 || source.getRawUserInfo() != null || source.getRawQuery() != null
                || source.getRawFragment() != null || !source.getRawPath().matches("/[A-Za-z0-9]{1,40}")) {
            throw new IllegalArgumentException("请输入高德地图地点分享链接");
        }
        HttpURLConnection connection = (HttpURLConnection) source.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            int status = connection.getResponseCode();
            if (status < 300 || status >= 400) throw new IllegalArgumentException("无法识别分享链接，请尝试重新分享地点");
            return parseRedirect(connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    static Location parseRedirect(String redirect) throws Exception {
        if (redirect == null || redirect.length() > 8192) throw new IllegalArgumentException("分享链接没有地点信息");
        URI target = URI.create(redirect);
        String host = target.getHost();
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getRawUserInfo() != null || target.getPort() != -1
                || !("wb.amap.com".equalsIgnoreCase(host) || "www.amap.com".equalsIgnoreCase(host))) {
            throw new IllegalArgumentException("分享链接跳转地址无效");
        }
        Map<String, String> params = new LinkedHashMap<>();
        String query = target.getRawQuery();
        if (query != null) for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            if ("p".equals(key) && params.containsKey(key)) throw new IllegalArgumentException("分享链接包含重复的地点信息");
            params.put(key, pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "");
        }
        String value = params.get("p");
        if (value == null) throw new IllegalArgumentException("未能从链接中提取地点坐标");
        String[] fields = value.split(",", -1);
        if (fields.length < 4) throw new IllegalArgumentException("分享链接不包含完整的地点信息");
        double lat;
        double lon;
        try { lat = Double.parseDouble(fields[1]); lon = Double.parseDouble(fields[2]); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("地点坐标无效"); }
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
            throw new IllegalArgumentException("地点坐标无效");
        String name = fields[3].trim();
        if (name.isEmpty() || name.length() > 200) throw new IllegalArgumentException("地点名称无效");
        return new Location(name, lat, lon);
    }

    public static final class Location {
        public final String name;
        public final double latitude;
        public final double longitude;
        Location(String name, double latitude, double longitude) {
            this.name = name; this.latitude = latitude; this.longitude = longitude;
        }
    }
}
