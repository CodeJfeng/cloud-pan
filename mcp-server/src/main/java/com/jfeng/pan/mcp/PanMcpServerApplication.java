package com.jfeng.pan.mcp;

import com.jfeng.pan.mcp.config.McpServerConfig;
import com.jfeng.pan.mcp.tools.PanTools;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

/**
 * 云盘 MCP Server 启动入口。
 * <p>
 * 用内嵌 Jetty 12（Jakarta Servlet 6.0）注册 MCP Streamable HTTP transport servlet，
 * 监听 {@code http://0.0.0.0:10001/mcp}。
 */
public class PanMcpServerApplication {

    public static final int DEFAULT_PORT = 10001;
    public static final String DEFAULT_PAN_BASE_URL = "http://127.0.0.1:10000";

    public static int defaultPort() {
        return DEFAULT_PORT;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("MCP_SERVER_PORT", String.valueOf(DEFAULT_PORT)));
        String panBaseUrl = System.getenv().getOrDefault("PAN_BASE_URL", DEFAULT_PAN_BASE_URL);

        HttpServletStreamableServerTransportProvider transport = McpServerConfig.transportProvider();
        McpSyncServer mcpServer = McpServerConfig.buildMcpServer(transport);

        // 注册 9 个云盘 MCP 工具
        PanTools tools = new PanTools(panBaseUrl);
        tools.register(mcpServer);
        System.out.println("Registered " + mcpServer.listTools().size() + " MCP tools.");

        Server jetty = new Server(port);
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");
        // transport 本身是 HttpServlet，直接注册到 /mcp
        ctx.addServlet(new ServletHolder(transport), McpServerConfig.MCP_ENDPOINT);
        jetty.setHandler(ctx);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mcpServer.close();
                jetty.stop();
            } catch (Exception e) {
                // ignore
            }
        }));

        jetty.start();
        System.out.println("Cloud-Pan MCP Server listening on http://0.0.0.0:" + port
                + McpServerConfig.MCP_ENDPOINT + " (panBaseUrl=" + panBaseUrl + ")");
        jetty.join();
    }
}
