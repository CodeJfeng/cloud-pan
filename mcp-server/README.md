# Cloud-Pan MCP Server

把云盘（Java Spring Boot 后端）文件操作能力封装为标准 **MCP (Model Context Protocol) 工具**，通过 **Streamable HTTP** 对外暴露，供 Python Agent（MCP Client）调用。

## 架构

```
[Python Agent (MCP Client)]
        │  Streamable HTTP (JSON-RPC over POST)
        ▼
[Java MCP Server :10001]   （本工程，内嵌 Jetty 12 = Jakarta Servlet 6.0）
        │  内部 JDK HttpClient 调云盘 API，鉴权头: Authorization: Bearer MCP_<userId>
        ▼
[Java 云盘后端 :10000]   （Spring Boot 2.7，零改动）
```

- 独立 Maven 工程，**不继承** `cloud-drive-parent`（主工程为 Spring Boot 2.7 / javax Servlet 4.0，本模块需要 Jakarta Servlet 6.0，用内嵌 Jetty 12 独立进程隔离）。
- 所有对外 ID（fileId / parentId / shareId）均为 AES 加密字符串，**原样透传，不解密**。
- 多 ID 拼接分隔符：`__,__`。

## 技术栈

- Java 21（OpenJDK）、Maven 3.9.16
- `io.modelcontextprotocol.sdk:mcp:1.0.0`（核心，含 Servlet 版 Streamable HTTP transport）
- `org.eclipse.jetty.ee10:jetty-ee10-servlet / jetty-ee10-servlets:12.0.15`（Jakarta Servlet 6.0）
- `io.modelcontextprotocol.sdk:mcp-json-jackson3`（Jackson 3 JSON 映射，经 SPI 自动加载）
- `org.slf4j:slf4j-simple`（日志）
- `org.junit.jupiter:junit-jupiter` + `maven-surefire-plugin`（测试，TDD）

## 工程结构

```
mcp-server/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/jfeng/pan/mcp
    │   │   ├── PanMcpServerApplication.java   # 主类：内嵌 Jetty 12 + 注册工具
    │   │   ├── config/McpServerConfig.java    # transport 与 McpSyncServer 构建
    │   │   └── tools
    │   │       ├── PanApiClient.java          # 云盘后端 HTTP 客户端（JDK HttpClient）
    │   │       └── PanTools.java              # 9 个云盘 MCP 工具
    │   └── resources/application.properties
    └── test/java/com/jfeng/pan/mcp
        ├── McpServerMainTest.java             # 端口常量
        ├── config/McpServerConfigTest.java    # transport/server 构建
        └── tools
            ├── PanApiClientTest.java          # HTTP 层（JDK HttpServer 模拟后端）
            └── PanToolsTest.java              # 9 工具注册/调用
```

## 工具清单（9 个）

| 工具 | 入参 | 说明 | 云盘 API |
|------|------|------|----------|
| `list_files` | userId, parentId | 列出目录内容 | `GET /files?parentId=` |
| `search_files` | userId, keyword | 全盘搜索 | `GET /file/search?keyword=` |
| `create_folder` | userId, parentId, folderName | 新建文件夹 | `POST /file/folder` |
| `rename_file` | userId, fileId, newFilename | 重命名 | `PUT /file` |
| `delete_file` | userId, fileIds[] | 删除（进回收站） | `DELETE /file` |
| `transfer_files` | userId, fileIds[], targetParentId | 移动 | `POST /file/transfer` |
| `copy_files` | userId, fileIds[], targetParentId | 复制 | `POST /file/copy` |
| `restore_file` | userId, fileIds[] | 回收站还原 | `PUT /recycle/restore` |
| `create_share` | userId, fileIds[], validDay, shareType, code? | 创建分享 | `POST /share` |

> 每个工具的第一个入参均为 `userId`（String，required，标记为内部注入参数）。LLM 侧不会看到它，Python Agent 调用时会自动注入 `Bearer MCP_<userId>` 鉴权。

## 构建与测试

```bash
cd d:/Project/cloud-pan/mcp-server

# 运行全部单元测试（TDD：HTTP 层用 JDK HttpServer 模拟云盘后端 :10000）
mvn test

# 打包可执行 fat jar（mainClass: com.jfeng.pan.mcp.PanMcpServerApplication）
mvn package
```

测试结果（`mvn test`）：

```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 启动

```powershell
cd d:/Project/cloud-pan/mcp-server
$env:PAN_BASE_URL = "http://127.0.0.1:10000"   # 云盘后端地址（可选，默认即此值）
$env:MCP_SERVER_PORT = "10001"                 # 可选，默认 10001
java -jar target/cloud-drive-mcp-server-1.0.jar
```

启动日志预期：

```
Registered 9 MCP tools.
Cloud-Pan MCP Server listening on http://0.0.0.0:10001/mcp (panBaseUrl=http://127.0.0.1:10000)
```

## 验证（curl，MCP Streamable HTTP 协议）

> 注意：需携带 `Accept: application/json, text/event-stream` 头；`initialize` 返回头中的 `Mcp-Session-Id` 需在后续请求回传。

### 1. initialize 握手

```bash
curl -s -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}'
```

响应摘要（同时返回 `Mcp-Session-Id: <uuid>` 响应头）：

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"logging":{},"tools":{"listChanged":true}},"serverInfo":{"name":"cloud-pan-mcp","version":"1.0.0"}}}
```

### 2. tools/list（9 个工具）

```bash
curl -s -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

响应摘要（SSE，`data:` 中为 JSON-RPC 结果）：返回 **9 个工具**，每个 schema 的 `required` 均以 `userId` 为首参。

工具名：`list_files`、`search_files`、`create_folder`、`rename_file`、`delete_file`、`transfer_files`、`copy_files`、`restore_file`、`create_share`。

### 3. tools/call（调用工具）

```bash
curl -s -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_files","arguments":{"userId":"1","parentId":"<加密parentId>"}}}'
```

响应摘要（工具把云盘响应包成 TextContent）：

```json
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"[list_files] 云盘响应: {\"code\":0,\"message\":\"success\",\"data\":[...]}"}],"isError":false}}
```

## 说明

- 云盘后端 `Authorization` 鉴权为 `Bearer MCP_<userId>`（`CommonLoginAspect` 已放行 `MCP_` 前缀 token，后端零改动）。
- 失败时工具返回 `isError: true`。
- 本工程不修改 cloud-pan 原有任何代码（server / framework / frontend 均不动）。
