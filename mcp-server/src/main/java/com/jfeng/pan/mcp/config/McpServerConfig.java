package com.jfeng.pan.mcp.config;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP Server 配置：构建 Streamable HTTP transport 与 McpSyncServer。
 * <p>
 * 官方 MCP Java SDK 核心库自带 Servlet 版 transport，要求 Jakarta Servlet 6.0
 * （本项目用内嵌 Jetty 12 提供容器，与主工程 Spring Boot 2.7 / javax 隔离）。
 */
public class McpServerConfig {

    public static final String MCP_ENDPOINT = "/mcp";
    public static final String SERVER_NAME = "cloud-pan-mcp";
    public static final String SERVER_VERSION = "1.0.0";

    private McpServerConfig() {
    }

    /** 获取 JSON 映射器（Jackson 3 实现，经 SPI 提供）。 */
    public static McpJsonMapper jsonMapper() {
        return new JacksonMcpJsonMapperSupplier().get();
    }

    /**
     * 构建 Streamable HTTP transport。transport 本身即 HttpServlet，
     * 可直接注册到 Jetty 12 容器。
     */
    public static HttpServletStreamableServerTransportProvider transportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper())
                .mcpEndpoint(MCP_ENDPOINT)
                .build();
    }

    /** 构建启用了 tools 能力的同步 MCP Server。 */
    public static McpSyncServer buildMcpServer(HttpServletStreamableServerTransportProvider transport) {
        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();
    }
}
