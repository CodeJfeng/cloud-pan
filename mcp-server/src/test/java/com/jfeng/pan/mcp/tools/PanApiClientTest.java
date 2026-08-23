package com.jfeng.pan.mcp.tools;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PanApiClient 的 HTTP 层测试：用 JDK 内置轻量 HttpServer 模拟云盘后端 :10000，
 * 返回固定 R JSON，验证请求路径/方法/参数/鉴权头/响应解析。
 */
class PanApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String lastPath;
    private volatile String lastMethod;
    private volatile String lastAuth;
    private volatile String lastBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getRawPath();
            lastMethod = exchange.getRequestMethod();
            lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastBody = new String(body, StandardCharsets.UTF_8);
            String resp = "{\"code\":0,\"message\":\"success\",\"data\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void authHeaderIsBearerMcpToken() {
        PanApiClient client = new PanApiClient(baseUrl);
        assertEquals("Bearer MCP_123", client.buildAuthHeader(123));
        assertEquals("Bearer MCP_0", client.buildAuthHeader(0));
    }

    @Test
    void searchUrlHasKeywordParam() throws Exception {
        PanApiClient client = new PanApiClient(baseUrl);
        String url = client.searchUrl("报告");
        assertTrue(url.contains("/file/search"));
        assertTrue(url.contains("keyword="));
        // 中文关键词应被 URL 编码
        assertTrue(!url.contains("keyword=报告"));
    }

    @Test
    void getSendsAuthAndReturnsBody() throws Exception {
        PanApiClient client = new PanApiClient(baseUrl);
        String result = client.get(42, baseUrl + "/files?parentId=abc");
        assertEquals("{\"code\":0,\"message\":\"success\",\"data\":\"ok\"}", result);
        assertEquals("Bearer MCP_42", lastAuth);
        assertEquals("GET", lastMethod);
        assertEquals("/files", lastPath);
    }

    @Test
    void postSendsJsonBodyAndAuth() throws Exception {
        PanApiClient client = new PanApiClient(baseUrl);
        client.post(7, baseUrl + "/file/folder", "{\"parentId\":\"x\"}");
        assertEquals("POST", lastMethod);
        assertEquals("Bearer MCP_7", lastAuth);
        assertEquals("{\"parentId\":\"x\"}", lastBody);
        assertEquals("/file/folder", lastPath);
    }

    @Test
    void putSendsJsonBodyAndAuth() throws Exception {
        PanApiClient client = new PanApiClient(baseUrl);
        client.put(9, baseUrl + "/file", "{\"fileId\":\"y\"}");
        assertEquals("PUT", lastMethod);
        assertEquals("Bearer MCP_9", lastAuth);
        assertEquals("{\"fileId\":\"y\"}", lastBody);
    }

    @Test
    void deleteSendsJsonBodyAndAuth() throws Exception {
        PanApiClient client = new PanApiClient(baseUrl);
        client.delete(3, baseUrl + "/file", "{\"fileIds\":\"a__,__b\"}");
        assertEquals("DELETE", lastMethod);
        assertEquals("Bearer MCP_3", lastAuth);
        assertEquals("{\"fileIds\":\"a__,__b\"}", lastBody);
        assertEquals("/file", lastPath);
    }
}
