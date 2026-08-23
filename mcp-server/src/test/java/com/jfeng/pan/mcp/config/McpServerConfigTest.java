package com.jfeng.pan.mcp.config;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class McpServerConfigTest {

    @Test
    void mcpEndpointPath() {
        assertEquals("/mcp", McpServerConfig.MCP_ENDPOINT);
    }

    @Test
    void transportProviderIsServlet() {
        HttpServletStreamableServerTransportProvider transport = McpServerConfig.transportProvider();
        assertNotNull(transport);
    }

    @Test
    void buildsSyncServerWithToolsCapability() {
        HttpServletStreamableServerTransportProvider transport = McpServerConfig.transportProvider();
        McpSyncServer server = McpServerConfig.buildMcpServer(transport);
        assertNotNull(server.getServerInfo());
        assertEquals("cloud-pan-mcp", server.getServerInfo().name());
        assertNotNull(server.getServerCapabilities().tools());
        server.close();
    }
}
