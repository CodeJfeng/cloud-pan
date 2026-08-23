package com.jfeng.pan.mcp.tools;

import com.jfeng.pan.mcp.config.McpServerConfig;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PanTools 的注册与工具调用测试：验证 9 个工具全部注册、userId 为 required 首参、
 * 且调用时正确拼装云盘 API 请求。
 */
class PanToolsTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String lastPath;
    private volatile String lastMethod;
    private volatile String lastAuth;
    private volatile String lastBody;
    private volatile String lastQuery;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getRawPath();
            lastQuery = exchange.getRequestURI().getRawQuery();
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
    void registersNineTools() {
        HttpServletStreamableServerTransportProvider transport = McpServerConfig.transportProvider();
        McpSyncServer mcpServer = McpServerConfig.buildMcpServer(transport);
        PanTools tools = new PanTools(baseUrl);
        tools.register(mcpServer);

        List<McpSchema.Tool> registered = mcpServer.listTools();
        assertEquals(9, registered.size());
        List<String> names = registered.stream().map(McpSchema.Tool::name).toList();
        assertTrue(names.containsAll(List.of(
                "list_files", "search_files", "create_folder", "rename_file",
                "delete_file", "transfer_files", "copy_files",
                "restore_file", "create_share")));
        mcpServer.close();
    }

    @Test
    void everyToolHasUserIdRequiredFirstParam() {
        PanTools tools = new PanTools(baseUrl);
        List<McpServerFeatures.SyncToolSpecification> specs = tools.allSpecifications();
        assertEquals(9, specs.size());
        for (McpServerFeatures.SyncToolSpecification spec : specs) {
            McpSchema.JsonSchema schema = spec.tool().inputSchema();
            assertNotNull(schema.required(), "tool " + spec.tool().name() + " should have required list");
            assertTrue(schema.required().contains("userId"), "tool " + spec.tool().name() + " must require userId");
            assertEquals("userId", schema.required().get(0),
                    "tool " + spec.tool().name() + " must have userId as first required param");
        }
    }

    @Test
    void listFilesCallsGetWithParentId() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "list_files");
        McpSchema.CallToolResult result = invoke(spec, Map.of(
                "userId", "123", "parentId", "encParent"));
        assertEquals("Bearer MCP_123", lastAuth);
        assertEquals("GET", lastMethod);
        assertEquals("/files", lastPath);
        assertEquals("parentId=encParent", lastQuery);
        assertFalse(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void userIdAcceptsSnowflakeLong() throws Exception {
        // 回归：雪花 ID 远超 int 范围，必须用 long 解析（曾因 Integer.parseInt 抛 NumberFormatException）
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "list_files");
        McpSchema.CallToolResult result = invoke(spec, Map.of(
                "userId", "2055271612527890400", "parentId", "ALL"));
        assertEquals("Bearer MCP_2055271612527890400", lastAuth);
        assertFalse(Boolean.TRUE.equals(result.isError()));
    }

    @Test
    void searchFilesCallsGetWithKeyword() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "search_files");
        invoke(spec, Map.of("userId", "1", "keyword", "报告"));
        assertEquals("GET", lastMethod);
        assertEquals("/file/search", lastPath);
        assertTrue(lastQuery.startsWith("keyword="));
        assertFalse(lastQuery.contains("keyword=报告"), "keyword should be URL-encoded");
    }

    @Test
    void createFolderPostsFolderBody() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "create_folder");
        invoke(spec, Map.of("userId", "2", "parentId", "encParent", "folderName", "测试目录"));
        assertEquals("POST", lastMethod);
        assertEquals("/file/folder", lastPath);
        assertTrue(lastBody.contains("\"parentId\":\"encParent\""));
        assertTrue(lastBody.contains("\"folderName\":\"测试目录\""));
    }

    @Test
    void renameFilePutsBody() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "rename_file");
        invoke(spec, Map.of("userId", "2", "fileId", "encFile", "newFilename", "新名字.txt"));
        assertEquals("PUT", lastMethod);
        assertEquals("/file", lastPath);
        assertTrue(lastBody.contains("\"fileId\":\"encFile\""));
        assertTrue(lastBody.contains("\"newFilename\":\"新名字.txt\""));
    }

    @Test
    void deleteFileDeletesWithSeparator() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "delete_file");
        invoke(spec, Map.of("userId", "3", "fileIds", List.of("a", "b")));
        assertEquals("DELETE", lastMethod);
        assertEquals("/file", lastPath);
        assertTrue(lastBody.contains("\"fileIds\":\"a__,__b\""));
    }

    @Test
    void transferFilesPostsWithSeparator() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "transfer_files");
        invoke(spec, Map.of("userId", "4", "fileIds", List.of("x", "y"), "targetParentId", "tgt"));
        assertEquals("POST", lastMethod);
        assertEquals("/file/transfer", lastPath);
        assertTrue(lastBody.contains("\"fileIds\":\"x__,__y\""));
        assertTrue(lastBody.contains("\"targetParentId\":\"tgt\""));
    }

    @Test
    void copyFilesPostsWithSeparator() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "copy_files");
        invoke(spec, Map.of("userId", "5", "fileIds", List.of("m", "n"), "targetParentId", "tgt"));
        assertEquals("POST", lastMethod);
        assertEquals("/file/copy", lastPath);
        assertTrue(lastBody.contains("\"fileIds\":\"m__,__n\""));
        assertTrue(lastBody.contains("\"targetParentId\":\"tgt\""));
    }

    @Test
    void restoreFilePutsRecycleRestore() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "restore_file");
        invoke(spec, Map.of("userId", "6", "fileIds", List.of("r1", "r2")));
        assertEquals("PUT", lastMethod);
        assertEquals("/recycle/restore", lastPath);
        assertTrue(lastBody.contains("\"fileIds\":\"r1__,__r2\""));
    }

    @Test
    void createSharePostsShareBody() throws Exception {
        PanTools tools = new PanTools(baseUrl);
        McpServerFeatures.SyncToolSpecification spec = findSpec(tools, "create_share");
        invoke(spec, Map.of("userId", "9", "fileIds", List.of("f1", "f2"),
                "validDay", 7, "shareType", 0));
        assertEquals("POST", lastMethod);
        assertEquals("/share", lastPath);
        assertTrue(lastBody.contains("\"shareFileIds\":\"f1__,__f2\""));
        assertTrue(lastBody.contains("\"shareDayType\":7"));
        assertTrue(lastBody.contains("\"shareType\":0"));
    }

    // ---- helpers ----

    private McpServerFeatures.SyncToolSpecification findSpec(PanTools tools, String name) {
        return tools.allSpecifications().stream()
                .filter(s -> s.tool().name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("tool not found: " + name));
    }

    private McpSchema.CallToolResult invoke(McpServerFeatures.SyncToolSpecification spec, Map<String, Object> args) {
        BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler = spec.callHandler();
        McpSchema.CallToolRequest req = new McpSchema.CallToolRequest(spec.tool().name(), args);
        return handler.apply(null, req);
    }
}
