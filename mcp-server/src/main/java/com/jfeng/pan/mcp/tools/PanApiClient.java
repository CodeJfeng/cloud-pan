package com.jfeng.pan.mcp.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 云盘 Java 后端 HTTP 客户端。
 * <p>
 * 鉴权复用 {@code Authorization: Bearer MCP_<userId>}（主工程
 * CommonLoginAspect.checkMCPAndSaveUserID 已放行 MCP_ 前缀 token，后端零改动）。
 * 所有对外 ID（fileId / parentId / shareId）均为 AES 加密字符串，原样透传，不解密。
 */
public class PanApiClient {

    private static final String BEARER_PREFIX = "Bearer MCP_";

    private final String baseUrl;
    private final HttpClient http;

    public PanApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** 构建 Bearer MCP token 鉴权头。 */
    public String buildAuthHeader(long userId) {
        return BEARER_PREFIX + userId;
    }

    /** 文件搜索 URL（关键词做 URL 编码）。 */
    public String searchUrl(String keyword) {
        return baseUrl + "/file/search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    /** 通用 GET。 */
    public String get(long userId, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .GET()
                .build();
        return send(req);
    }

    /** 通用 POST（JSON body）。 */
    public String post(long userId, String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    /** 通用 PUT（JSON body）。 */
    public String put(long userId, String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    /** 通用 DELETE（JSON body）。 */
    public String delete(long userId, String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(req);
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
