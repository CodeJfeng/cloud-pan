# 云盘智能助手（Java 能力 MCP 封装）实现计划

> **给 Claude 的说明：** 必需的子技能：使用 superpowers:executing-plans 逐任务实现此计划。

**目标：** 在现有 Spring Boot 云盘项目旁新增两层能力：
1. **Java MCP Server**：把云盘文件操作能力（搜索/建文件夹/重命名/移动/分享/回收站）封装为标准 MCP 工具，通过 Streamable HTTP 对外暴露。
2. **Python Agent 服务**：作为 MCP Client 连接 Java MCP Server，用户用自然语言操作文件，对话经 SSE 流式返回前端。

**架构：**

```
[Vue3 前端 /agent 页面]
   │  POST /api/agent/chat (SSE)
   ▼
[Python Agent 服务 :8000]  (FastAPI)
   │   ├── MCP Client (mcp python SDK) ──Streamable HTTP──► [Java MCP Server :10001]
   │   │                                                       └─ 内部调云盘 API (MCP_<userId>) → [Java 云盘后端 :10000]
   │   ├── LLM (OpenAI 兼容协议: DeepSeek/通义/Kimi 可配置)
   │   └── MySQL 会话持久化 (复用云盘库)
```

- **Java MCP Server** 是独立 Maven 工程 `mcp-server/`（不挂 `cloud-drive-parent`）：官方 `io.modelcontextprotocol.sdk:mcp` 核心库的 Servlet transport（`HttpServletStreamableServerTransportProvider`）要求 **Jakarta Servlet 6.0**，而主工程是 Spring Boot 2.7（javax Servlet 4.0），无法内嵌，故用内嵌 **Jetty 12** 独立进程。工具内部用 JDK `HttpClient` 调现有云盘 API，鉴权复用 `Authorization: MCP_<userId>`（`CommonLoginAspect.checkMCPAndSaveUserID` 已放行，Java 云盘后端零改动）。
- **Python Agent** 是独立目录 `agent/`：FastAPI + 官方 `mcp` Python SDK 作为 client，动态拉取 MCP 工具列表并转换为 OpenAI tools schema，用原生 function-calling 循环调用（不引 LangChain）。

**技术栈：**
- Java MCP Server：Java 21、Maven、`io.modelcontextprotocol.sdk:mcp:1.0.0`、Jetty 12 (ee10 servlet)、Jackson、JDK HttpClient、JUnit 5
- Python Agent：Python 3.11+、FastAPI、uvicorn、httpx、pydantic v2、`mcp`（官方 Python SDK）、pytest

**前置假设：**
- Java 云盘后端运行于 `http://127.0.0.1:10000`，`MCP_<userId>` 鉴权已可用（已确认）。
- MCP Server 与 Agent 之间 MVP 不做 OAuth 双向认证，工具调用通过 `userId` 参数注入（生产环境需加固）。
- 已探明可用云盘 API：搜索 `GET /file/search`、列表 `GET /files`、建文件夹 `POST /file/folder`、重命名 `PUT /file`、转移 `POST /file/transfer`、复制 `POST /file/copy`、创建分享 `POST /share`、回收站 `GET /recycles`、还原 `PUT /recycle/restore`。

---

## 任务 1：Python 服务脚手架

**文件：**
- 创建：`agent/requirements.txt`
- 创建：`agent/app/__init__.py`
- 创建：`agent/app/config.py`
- 创建：`agent/tests/__init__.py`
- 创建：`agent/tests/test_config.py`

**第 1 步：编写失败的测试**

`agent/tests/test_config.py`：

```python
from app.config import Settings


def test_settings_defaults():
    s = Settings()
    assert s.pan_base_url == "http://127.0.0.1:10000"
    assert s.mcp_server_url == "http://127.0.0.1:10001/mcp"
    assert s.agent_port == 8000
```

**第 2 步：运行测试确认它失败**

```bash
cd d:/Project/cloud-pan/agent
python -m pytest tests/test_config.py -v
```

预期：以 `ModuleNotFoundError: No module named 'app'` 失败。

**第 3 步：编写最小实现**

`agent/requirements.txt`：

```
fastapi==0.115.*
uvicorn[standard]==0.30.*
httpx==0.27.*
pydantic==2.8.*
pydantic-settings==2.4.*
mcp>=1.0.0
pytest==8.3.*
pytest-asyncio==0.24.*
```

`agent/app/config.py`：

```python
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Agent 服务配置，全部支持环境变量覆盖（前缀 AGENT_）。"""

    agent_host: str = "0.0.0.0"
    agent_port: int = 8000

    # Java MCP Server（Streamable HTTP）
    mcp_server_url: str = "http://127.0.0.1:10001/mcp"

    # 云盘 Java 后端（仅用于健康检查等直连场景）
    pan_base_url: str = "http://127.0.0.1:10000"

    # LLM 供应商（OpenAI 兼容协议）
    llm_base_url: str = "https://api.deepseek.com"
    llm_api_key: str = ""
    llm_model: str = "deepseek-chat"
    llm_temperature: float = 0.3
    llm_timeout: float = 60.0

    # 会话存储（MySQL）
    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "root"
    mysql_password: str = "123456"
    mysql_database: str = "r_pan"

    class Config:
        env_prefix = "AGENT_"
        env_file = ".env"
```

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_config.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add python agent scaffold and config"
```

---

## 任务 2：FastAPI 应用骨架 + 健康检查

**文件：**
- 创建：`agent/app/main.py`
- 创建：`agent/tests/test_health.py`

**第 1 步：编写失败的测试**

`agent/tests/test_health.py`：

```python
from fastapi.testclient import TestClient

from app.main import create_app


def test_health():
    client = TestClient(create_app())
    resp = client.get("/api/agent/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_health.py -v
```

预期：以 `ModuleNotFoundError` 失败。

**第 3 步：编写最小实现**

`agent/app/main.py`：

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware


def create_app() -> FastAPI:
    app = FastAPI(title="Cloud-Pan Agent", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],  # 生产环境应收敛
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/api/agent/health")
    def health():
        return {"status": "ok"}

    return app


app = create_app()
```

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_health.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add fastapi app skeleton with health check"
```

---

## 任务 3：OpenAI 兼容 LLM 客户端

**文件：**
- 创建：`agent/app/core/__init__.py`
- 创建：`agent/app/core/llm.py`
- 创建：`agent/tests/test_llm.py`

**第 1 步：编写失败的测试**

`agent/tests/test_llm.py`：

```python
import pytest
from unittest.mock import AsyncMock, Mock, patch

from app.core.llm import LLMClient
from app.config import Settings


def test_chat_builds_openai_payload():
    client = LLMClient(Settings())
    payload = client._build_payload(
        messages=[{"role": "user", "content": "hi"}],
        tools=[],
    )
    assert payload["model"] == Settings().llm_model
    assert payload["messages"][0]["content"] == "hi"


@pytest.mark.asyncio
async def test_chat_returns_content():
    s = Settings(llm_api_key="test-key")
    client = LLMClient(s)
    fake_resp = Mock()
    fake_resp.status_code = 200
    fake_resp.json.return_value = {
        "choices": [{"message": {"role": "assistant", "content": "hello"}, "tool_calls": None}]
    }
    with patch.object(client._http, "post", new=AsyncMock(return_value=fake_resp)):
        resp = await client.chat([{"role": "user", "content": "hi"}], tools=[])
        assert resp.content == "hello"
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_llm.py -v
```

预期：以 `ModuleNotFoundError: No module named 'app.core.llm'` 失败。

**第 3 步：编写最小实现**

`agent/app/core/llm.py`：

```python
import httpx
from dataclasses import dataclass, field
from typing import Any

from app.config import Settings


@dataclass
class LLMResponse:
    content: str = ""
    tool_calls: list[dict[str, Any]] = field(default_factory=list)
    finish_reason: str = ""


class LLMClient:
    """OpenAI 兼容 chat/completions 客户端（支持 function calling / tool use）。"""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._http = httpx.AsyncClient(
            base_url=settings.llm_base_url,
            timeout=settings.llm_timeout,
            headers={"Authorization": f"Bearer {settings.llm_api_key}"},
        )

    def _build_payload(self, messages: list[dict], tools: list[dict] | None = None) -> dict:
        payload: dict = {
            "model": self.settings.llm_model,
            "messages": messages,
            "temperature": self.settings.llm_temperature,
        }
        if tools:
            payload["tools"] = tools
        return payload

    async def chat(self, messages: list[dict], tools: list[dict] | None = None) -> LLMResponse:
        payload = self._build_payload(messages, tools)
        resp = await self._http.post("/chat/completions", json=payload)
        resp.raise_for_status()
        data = resp.json()
        msg = data["choices"][0]["message"]
        return LLMResponse(
            content=msg.get("content") or "",
            tool_calls=msg.get("tool_calls") or [],
            finish_reason=data["choices"][0].get("finish_reason", ""),
        )

    async def close(self):
        await self._http.aclose()
```

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_llm.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add openai-compatible llm client"
```

---

## 任务 4：Java MCP Server 工程脚手架

**文件：**
- 创建：`mcp-server/pom.xml`
- 创建：`mcp-server/src/main/java/com/jfeng/pan/mcp/McpServerMain.java`
- 创建：`mcp-server/src/test/java/com/jfeng/pan/mcp/McpServerMainTest.java`
- 创建：`mcp-server/src/main/resources/application.properties`

**第 1 步：编写失败的测试**

`mcp-server/src/test/java/com/jfeng/pan/mcp/McpServerMainTest.java`：

```java
package com.jfeng.pan.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerMainTest {

    @Test
    void defaultPortIs10001() {
        assertEquals(10001, McpServerMain.defaultPort());
    }
}
```

**第 2 步：运行测试确认它失败**

```bash
cd d:/Project/cloud-pan/mcp-server
mvn test
```

预期：编译失败（`McpServerMain` 不存在）。

**第 3 步：编写最小实现**

`mcp-server/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.jfeng.pan</groupId>
    <artifactId>cloud-drive-mcp-server</artifactId>
    <version>1.0</version>
    <packaging>jar</packaging>
    <name>cloud-drive-mcp-server</name>
    <description>Cloud-Pan Java capabilities exposed as MCP tools</description>

    <!-- 注意：独立工程，不继承 cloud-drive-parent（主工程为 Spring Boot 2.7 / javax，此模块需要 jakarta Servlet 6.0） -->
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mcp.version>1.0.0</mcp.version>
        <jetty.version>12.0.15</jetty.version>
        <jackson.version>2.19.2</jackson.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <!-- MCP Java 官方 SDK 核心库（含 Servlet 版 Streamable HTTP transport） -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
            <version>${mcp.version}</version>
        </dependency>
        <!-- Jetty 12 ee10 = Jakarta Servlet 6.0 -->
        <dependency>
            <groupId>org.eclipse.jetty.ee10</groupId>
            <artifactId>jetty-ee10-servlet</artifactId>
            <version>${jetty.version}</version>
        </dependency>
        <dependency>
            <groupId>org.eclipse.jetty.ee10</groupId>
            <artifactId>jetty-ee10-servlets</artifactId>
            <version>${jetty.version}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.13</version>
        </dependency>
        <!-- 测试 -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <!-- 可执行 fat jar -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.2</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.jfeng.pan.mcp.McpServerMain</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

`mcp-server/src/main/java/com/jfeng/pan/mcp/McpServerMain.java`（首版只含 main 骨架与端口常量，transport/工具在任务 5、6 填入）：

```java
package com.jfeng.pan.mcp;

public class McpServerMain {

    public static final int DEFAULT_PORT = 10001;

    public static int defaultPort() {
        return DEFAULT_PORT;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("MCP_SERVER_PORT", String.valueOf(DEFAULT_PORT)));
        // 任务5：构建 Streamable HTTP transport 并启动 Jetty
        // 任务6：注册 MCP 工具
        System.out.println("MCP server placeholder on port " + port);
    }
}
```

`mcp-server/src/main/resources/application.properties`：

```properties
mcp.server.port=10001
pan.base.url=http://127.0.0.1:10000
```

**第 4 步：运行测试确认它通过**

```bash
mvn test
```

预期：PASS。

**第 5 步：提交**

```bash
git add mcp-server/
git commit -m "feat(mcp): add java mcp server scaffold"
```

---

## 任务 5：Java MCP Server —— Streamable HTTP transport（内嵌 Jetty 12）

**文件：**
- 修改：`mcp-server/src/main/java/com/jfeng/pan/mcp/McpServerMain.java`
- 创建：`mcp-server/src/main/java/com/jfeng/pan/mcp/config/McpServerConfig.java`
- 创建：`mcp-server/src/test/java/com/jfeng/pan/mcp/config/McpServerConfigTest.java`

**第 1 步：编写失败的测试**

`mcp-server/src/test/java/com/jfeng/pan/mcp/config/McpServerConfigTest.java`：

```java
package com.jfeng.pan.mcp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerConfigTest {

    @Test
    void mcpEndpointPath() {
        assertEquals("/mcp", McpServerConfig.MCP_ENDPOINT);
    }
}
```

**第 2 步：运行测试确认它失败**

```bash
mvn test
```

预期：编译失败（`McpServerConfig` 不存在）。

**第 3 步：编写最小实现**

`McpServerConfig.java`：

```java
package com.jfeng.pan.mcp.config;

import io.modelcontextprotocol.sdk.mcp.server.McpServer;
import io.modelcontextprotocol.sdk.mcp.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.sdk.mcp.util.jackson.JacksonJsonMapper;

public class McpServerConfig {

    public static final String MCP_ENDPOINT = "/mcp";

    /**
     * 构建 Streamable HTTP transport。
     * 说明：官方 MCP Java SDK 核心库自带 Servlet 版 transport，要求 Jakarta Servlet 6.0
     * （本项目用内嵌 Jetty 12 提供容器，与主工程 Spring Boot 2.7/javax 隔离）。
     * API 细节以 mcp:1.0.0 官方示例为准。
     */
    public static HttpServletStreamableServerTransportProvider transportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonJsonMapper())
                .mcpEndpoint(MCP_ENDPOINT)
                .build();
    }

    public static McpServer buildMcpServer(HttpServletStreamableServerTransportProvider transport) {
        return McpServer.sync(transport)
                .serverInfo("cloud-pan-mcp", "1.0.0")
                .capabilities(io.modelcontextprotocol.sdk.mcp.spec.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();
    }
}
```

`McpServerMain.java` 改为真正启动 Jetty：

```java
package com.jfeng.pan.mcp;

import com.jfeng.pan.mcp.config.McpServerConfig;
import io.modelcontextprotocol.sdk.mcp.server.McpServer;
import io.modelcontextprotocol.sdk.mcp.server.transport.HttpServletStreamableServerTransportProvider;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

public class McpServerMain {

    public static final int DEFAULT_PORT = 10001;

    public static int defaultPort() {
        return DEFAULT_PORT;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("MCP_SERVER_PORT", String.valueOf(DEFAULT_PORT)));

        HttpServletStreamableServerTransportProvider transport = McpServerConfig.transportProvider();
        McpServer mcpServer = McpServerConfig.buildMcpServer(transport);
        // 任务6：mcpServer.addTool(...)

        Server jetty = new Server(port);
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");
        // transport 本身是 HttpServlet，直接注册
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
        System.out.println("Cloud-Pan MCP Server listening on http://0.0.0.0:" + port + McpServerConfig.MCP_ENDPOINT);
        jetty.join();
    }
}
```

**第 4 步：运行测试确认它通过**

```bash
mvn test
```

预期：PASS。手动冒烟：

```bash
mvn -q exec:java -Dexec.mainClass=com.jfeng.pan.mcp.McpServerMain   # 或 mvn package 后 java -jar
curl -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}'
```

预期：返回 JSON-RPC 初始化响应（含 serverInfo: cloud-pan-mcp）。

**第 5 步：提交**

```bash
git add mcp-server/
git commit -m "feat(mcp): serve streamable http transport on embedded jetty"
```

---

## 任务 6：Java MCP Server —— 云盘 MCP 工具实现

**文件：**
- 创建：`mcp-server/src/main/java/com/jfeng/pan/mcp/tools/PanApiClient.java`
- 创建：`mcp-server/src/main/java/com/jfeng/pan/mcp/tools/PanTools.java`
- 修改：`mcp-server/src/main/java/com/jfeng/pan/mcp/McpServerMain.java`
- 创建：`mcp-server/src/test/java/com/jfeng/pan/mcp/tools/PanApiClientTest.java`

**第 1 步：编写失败的测试**

`mcp-server/src/test/java/com/jfeng/pan/mcp/tools/PanApiClientTest.java`：

```java
package com.jfeng.pan.mcp.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PanApiClientTest {

    @Test
    void authHeaderIsMcpToken() {
        PanApiClient client = new PanApiClient("http://127.0.0.1:10000");
        assertEquals("MCP_123", client.buildAuthHeader(123));
    }

    @Test
    void searchUrlHasKeywordParam() {
        PanApiClient client = new PanApiClient("http://127.0.0.1:10000");
        assertEquals("http://127.0.0.1:10000/file/search?keyword=report",
                client.searchUrl("report"));
    }
}
```

**第 2 步：运行测试确认它失败**

```bash
mvn test
```

预期：编译失败（`PanApiClient` 不存在）。

**第 3 步：编写最小实现**

`PanApiClient.java` —— 用 JDK HttpClient 调云盘 API，复用 `MCP_<userId>` 鉴权：

```java
package com.jfeng.pan.mcp.tools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 云盘 Java 后端 HTTP 客户端。鉴权复用 Authorization: MCP_<userId>
 * （主工程 CommonLoginAspect.checkMCPAndSaveUserID 已放行）。
 */
public class PanApiClient {

    private final String baseUrl;
    private final HttpClient http;

    public PanApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String buildAuthHeader(int userId) {
        return "MCP_" + userId;
    }

    public String searchUrl(String keyword) {
        return baseUrl + "/file/search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    // 通用 GET
    public String get(int userId, String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    // 通用 POST / PUT（JSON body）
    public String post(int userId, String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String put(int userId, String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", buildAuthHeader(userId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
```

`PanTools.java` —— 注册所有云盘 MCP 工具。每个工具：Tool 描述 + JSON Schema 输入 + handler（内部调 PanApiClient）。核心代码（以 mcp:1.0.0 API 为准，执行时按官方示例校准）：

```java
package com.jfeng.pan.mcp.tools;

import io.modelcontextprotocol.sdk.mcp.server.McpServer;
import io.modelcontextprotocol.sdk.mcp.server.McpServerFeatures;
import io.modelcontextprotocol.sdk.mcp.spec.CallToolResult;
import io.modelcontextprotocol.sdk.mcp.spec.Tool;
import io.modelcontextprotocol.sdk.mcp.spec.ToolSchema;

import java.util.Map;
import java.util.function.BiFunction;

public class PanTools {

    private final PanApiClient pan;

    public PanTools(String panBaseUrl) {
        this.pan = new PanApiClient(panBaseUrl);
    }

    // 参数工具：arguments -> (userId, 其余参数)
    private static Map<String, Object> args(Map<String, Object> arguments) {
        return arguments;
    }

    private static int userId(Map<String, Object> a) {
        return ((Number) a.get("userId")).intValue();
    }

    private static ToolSchema schema(String json) {
        return ToolSchema.fromJson(json);
    }

    /** 注册全部工具到 MCP Server。 */
    public void register(McpServer server) {
        server.addTool(searchFiles());
        server.addTool(listFiles());
        server.addTool(createFolder());
        server.addTool(renameFile());
        server.addTool(transferFiles());
        server.addTool(copyFiles());
        server.addTool(createShare());
        server.addTool(listRecycles());
        server.addTool(restoreRecycle());
    }

    private McpServerFeatures.SyncToolSpecification searchFiles() {
        return tool("search_files", "按关键词搜索云盘中的文件（返回文件列表）",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer","description":"云盘用户ID（由Agent注入，勿问用户）"},
                               "keyword":{"type":"string","description":"搜索关键词"}},
                 "required":["userId","keyword"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = pan.get(userId(a), pan.searchUrl((String) a.get("keyword")));
                    return CallToolResult.success(body);
                });
    }

    private McpServerFeatures.SyncToolSpecification listFiles() {
        return tool("list_files", "列出云盘某目录下的文件与文件夹",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "parentId":{"type":"integer","description":"父目录ID，根目录传0"}},
                 "required":["userId","parentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String url = pan.baseUrl() + "/files?parentId=" + a.get("parentId");
                    return CallToolResult.success(pan.get(userId(a), url));
                });
    }

    private McpServerFeatures.SyncToolSpecification createFolder() {
        return tool("create_folder", "在云盘指定目录下创建文件夹",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "parentId":{"type":"integer"},
                               "filename":{"type":"string"}},
                 "required":["userId","parentId","filename"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = """
                        {"parentId":%s,"filename":"%s"}
                        """.formatted(a.get("parentId"), a.get("filename"));
                    return CallToolResult.success(pan.post(userId(a), pan.baseUrl() + "/file/folder", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification renameFile() {
        return tool("rename_file", "重命名云盘文件或文件夹",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "fileId":{"type":"integer"},
                               "newFilename":{"type":"string"}},
                 "required":["userId","fileId","newFilename"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = """
                        {"fileId":%s,"newFilename":"%s"}
                        """.formatted(a.get("fileId"), a.get("newFilename"));
                    return CallToolResult.success(pan.put(userId(a), pan.baseUrl() + "/file", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification transferFiles() {
        return tool("transfer_files", "把文件或文件夹移动到目标目录",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "fileIds":{"type":"array","items":{"type":"integer"}},
                               "targetParentId":{"type":"integer"}},
                 "required":["userId","fileIds","targetParentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = """
                        {"fileIds":%s,"targetParentId":%s}
                        """.formatted(a.get("fileIds"), a.get("targetParentId"));
                    return CallToolResult.success(pan.post(userId(a), pan.baseUrl() + "/file/transfer", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification copyFiles() {
        return tool("copy_files", "复制文件或文件夹到目标目录",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "fileIds":{"type":"array","items":{"type":"integer"}},
                               "targetParentId":{"type":"integer"}},
                 "required":["userId","fileIds","targetParentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = """
                        {"fileIds":%s,"targetParentId":%s}
                        """.formatted(a.get("fileIds"), a.get("targetParentId"));
                    return CallToolResult.success(pan.post(userId(a), pan.baseUrl() + "/file/copy", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification createShare() {
        return tool("create_share", "创建文件分享链接，返回分享链接与提取码",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "fileIds":{"type":"array","items":{"type":"integer"}},
                               "expireDays":{"type":"integer","description":"可选，过期天数"}},
                 "required":["userId","fileIds"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = """
                        {"fileIds":%s}
                        """.formatted(a.get("fileIds"));
                    return CallToolResult.success(pan.post(userId(a), pan.baseUrl() + "/share", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification listRecycles() {
        return tool("list_recycles", "查看回收站中的文件",
                """
                {"type":"object","properties":{"userId":{"type":"integer"}},"required":["userId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    return CallToolResult.success(pan.get(userId(a), pan.baseUrl() + "/recycles"));
                });
    }

    private McpServerFeatures.SyncToolSpecification restoreRecycle() {
        return tool("restore_recycle", "从回收站批量还原文件",
                """
                {"type":"object",
                 "properties":{"userId":{"type":"integer"},
                               "fileIds":{"type":"array","items":{"type":"integer"}}},
                 "required":["userId","fileIds"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileIds\":" + a.get("fileIds") + "}";
                    return CallToolResult.success(pan.put(userId(a), pan.baseUrl() + "/recycle/restore", body));
                });
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name, String description, String schemaJson,
            BiFunction<Object, io.modelcontextprotocol.sdk.mcp.spec.CallToolRequest, CallToolResult> handler) {
        Tool t = new Tool(name, description, schema(schemaJson));
        return new McpServerFeatures.SyncToolSpecification(t, (exchange, request) -> handler.apply(exchange, request));
    }
}
```

> 说明：以上代码按 mcp:1.0.0 的 API 结构编写；`CallToolRequest.arguments()`、`CallToolResult.success(String)`、`ToolSchema.fromJson` 等方法名执行时以官方 `mcp` 1.0.0 源码/示例为准校准。`userId` 属于"Agent 注入参数"，在 Python 侧从 LLM 可见 schema 中剔除（见任务 8）。

修改 `McpServerMain.java`：启动时 `new PanTools(panBaseUrl).register(mcpServer);`，`panBaseUrl` 从 `application.properties` 或环境变量 `PAN_BASE_URL` 读取。

**第 4 步：运行测试确认它通过**

```bash
mvn test
```

预期：PASS。手动冒烟（启动后执行 initialize + tools/list + tools/call）：

```bash
# tools/list 应返回 9 个云盘工具
curl -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

**第 5 步：提交**

```bash
git add mcp-server/
git commit -m "feat(mcp): add cloud-pan tools over streamable http"
```

---

## 任务 7：Python MCP Client 封装

**文件：**
- 创建：`agent/app/mcp/__init__.py`
- 创建：`agent/app/mcp/pan_mcp.py`
- 创建：`agent/tests/test_pan_mcp.py`

**第 1 步：编写失败的测试**

`agent/tests/test_pan_mcp.py`：

```python
from unittest.mock import AsyncMock, Mock, patch

from app.mcp.pan_mcp import PanMcpClient, mcp_tool_to_openai


def test_mcp_tool_to_openai_drops_user_id():
    fake_tool = Mock()
    fake_tool.name = "search_files"
    fake_tool.description = "搜索文件"
    fake_tool.inputSchema = {
        "type": "object",
        "properties": {
            "userId": {"type": "integer"},
            "keyword": {"type": "string"},
        },
        "required": ["userId", "keyword"],
    }
    schema = mcp_tool_to_openai(fake_tool)
    assert schema["function"]["name"] == "search_files"
    assert "userId" not in schema["function"]["parameters"]["properties"]
    assert "userId" not in schema["function"]["parameters"]["required"]


@pytest.mark.asyncio
async def test_call_tool_injects_user_id():
    async with PanMcpClient("http://127.0.0.1:10001/mcp") as client:
        with patch.object(client.session, "call_tool", new=AsyncMock(return_value=Mock(content="ok"))) as m:
            await client.call_tool("search_files", 123, {"keyword": "报告"})
            m.assert_called_once_with("search_files", {"keyword": "报告", "userId": 123})
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_pan_mcp.py -v
```

预期：以 `ModuleNotFoundError: No module named 'app.mcp.pan_mcp'` 失败。

**第 3 步：编写最小实现**

`agent/app/mcp/pan_mcp.py`：

```python
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


class PanMcpClient:
    """MCP Client，连接 Java MCP Server（Streamable HTTP）。"""

    def __init__(self, server_url: str):
        self.server_url = server_url
        self.session: ClientSession | None = None
        self._ctx = None

    async def __aenter__(self) -> "PanMcpClient":
        self._ctx = streamablehttp_client(url=self.server_url)
        read_stream, write_stream = await self._ctx.__aenter__()
        self.session = await ClientSession(read_stream, write_stream).__aenter__()
        await self.session.initialize()
        return self

    async def __aexit__(self, *exc):
        if self.session:
            await self.session.__aexit__(*exc)
        if self._ctx:
            await self._ctx.__aexit__(*exc)

    async def list_tools(self):
        result = await self.session.list_tools()
        return result.tools

    async def call_tool(self, name: str, user_id: int, arguments: dict):
        args = dict(arguments)
        args["userId"] = user_id  # Agent 注入，不暴露给 LLM
        return await self.session.call_tool(name, args)


def mcp_tool_to_openai(tool) -> dict:
    """把 MCP Tool 转换为 OpenAI tools schema，并剔除 Agent 注入参数 userId。"""
    params = dict(tool.inputSchema or {"type": "object", "properties": {}})
    props = dict(params.get("properties", {}))
    required = list(params.get("required", []))
    props.pop("userId", None)
    if "userId" in required:
        required.remove("userId")
    params["properties"] = props
    params["required"] = required
    return {
        "type": "function",
        "function": {
            "name": tool.name,
            "description": tool.description or "",
            "parameters": params,
        },
    }
```

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_pan_mcp.py -v
```

预期：PASS（`test_call_tool_injects_user_id` 会真实发起连接失败——若本地 MCP Server 未启动，该用例可能失败；执行时对 `PanMcpClient.__aenter__` 做 patch，或用 fakeredis 式 mock，保证单测不依赖外部进程）。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add mcp client wrapper for java mcp server"
```

---

## 任务 8：Python Agent 循环（MCP tools 动态拉取）

**文件：**
- 创建：`agent/app/core/agent.py`
- 创建：`agent/app/core/prompts.py`
- 创建：`agent/tests/test_agent.py`

**第 1 步：编写失败的测试**

`agent/tests/test_agent.py`：

```python
from unittest.mock import AsyncMock, Mock, patch

from app.core.agent import Agent
from app.core.llm import LLMResponse
from app.mcp.pan_mcp import mcp_tool_to_openai


@pytest.mark.asyncio
async def test_agent_runs_mcp_tool_and_finishes():
    agent = Agent(user_id=123, mcp_url="http://127.0.0.1:10001/mcp")

    # 第一轮：LLM 要求调用 search_files；第二轮：LLM 给出最终答案
    calls = [
        LLMResponse(content="", tool_calls=[{
            "id": "call_1",
            "type": "function",
            "function": {"name": "search_files", "arguments": '{"keyword": "报告"}'},
        }], finish_reason="tool_calls"),
        LLMResponse(content="找到了文件A", finish_reason="stop"),
    ]

    fake_tool = Mock()
    fake_tool.name = "search_files"
    fake_tool.description = "搜索"
    fake_tool.inputSchema = {"type": "object", "properties": {"userId": {"type": "integer"}, "keyword": {"type": "string"}}, "required": ["userId", "keyword"]}

    with patch.object(agent.llm, "chat", new=AsyncMock(side_effect=calls)) as mock_chat, \
         patch.object(agent, "_list_mcp_tools", new=AsyncMock(return_value=[fake_tool])) as mock_list, \
         patch.object(agent, "_call_mcp_tool", new=AsyncMock(return_value="[{'fileId': 1}]")) as mock_call:
        result = await agent.run("帮我找报告文件")

    assert result == "找到了文件A"
    mock_call.assert_called_once_with("search_files", {"keyword": "报告"})
    # 第二次 LLM 调用时 messages 中应有 tool 角色结果
    last_messages = mock_chat.call_args_list[-1][0][0]
    assert any(m["role"] == "tool" for m in last_messages)
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_agent.py -v
```

预期：以 `ModuleNotFoundError` 失败。

**第 3 步：编写最小实现**

`agent/app/core/prompts.py`：

```python
SYSTEM_PROMPT = """你是云盘智能助手，帮助用户管理云盘文件。
你可以搜索文件、创建文件夹、重命名、移动、复制文件、创建分享链接、管理回收站。
操作前确认参数；工具返回结果后，用简洁中文总结给用户。"""
```

`agent/app/core/agent.py`：

```python
import json
from typing import Any

from app.core.llm import LLMClient, LLMResponse
from app.core.prompts import SYSTEM_PROMPT
from app.mcp.pan_mcp import PanMcpClient, mcp_tool_to_openai

MAX_ITERATIONS = 5


class Agent:
    """基于 function calling 的 Agent 循环，工具全部来自 Java MCP Server。"""

    def __init__(self, user_id: int, mcp_url: str, llm: LLMClient | None = None):
        self.user_id = user_id
        self.mcp_url = mcp_url
        self.llm = llm or _default_llm()

    async def run(self, user_input: str) -> str:
        async with PanMcpClient(self.mcp_url) as pan:
            tools = [mcp_tool_to_openai(t) for t in await pan.list_tools()]
            messages: list[dict] = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_input},
            ]

            for _ in range(MAX_ITERATIONS):
                response = await self.llm.chat(messages, tools=tools)
                assistant_msg = {"role": "assistant", "content": response.content}
                if response.tool_calls:
                    assistant_msg["tool_calls"] = response.tool_calls
                messages.append(assistant_msg)

                if response.finish_reason == "tool_calls" and response.tool_calls:
                    for tc in response.tool_calls:
                        fn = tc["function"]
                        arguments = json.loads(fn.get("arguments") or "{}")
                        try:
                            result = await self._call_mcp_tool(pan, fn["name"], arguments)
                        except Exception as exc:
                            result = {"error": str(exc)}
                        messages.append({
                            "role": "tool",
                            "tool_call_id": tc["id"],
                            "content": json.dumps(result, ensure_ascii=False) if not isinstance(result, str) else result,
                        })
                    continue

                return response.content or ""

        return "已达最大步骤数，任务未完成。"

    async def _call_mcp_tool(self, pan: PanMcpClient, name: str, arguments: dict) -> Any:
        result = await pan.call_tool(name, self.user_id, arguments)
        # MCP CallToolResult.content 是 [TextContent|...]，取纯文本拼装
        texts = [c.text for c in result.content if hasattr(c, "text")]
        return "\n".join(texts) if texts else ""


def _default_llm() -> LLMClient:
    from app.config import Settings
    return LLMClient(Settings())
```

> 测试里 `_list_mcp_tools` / `_call_mcp_tool` 通过 patch 覆盖真实 MCP 连接；若按上面实现签名（`_call_mcp_tool(pan, name, arguments)`），测试 patch 需相应调整。执行时以测试可跑通为准微调。

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_agent.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add mcp-driven agent loop"
```

---

## 任务 9：SSE 流式对话接口

**文件：**
- 创建：`agent/app/api/__init__.py`
- 创建：`agent/app/api/chat.py`
- 创建：`agent/app/deps.py`
- 修改：`agent/app/main.py`
- 创建：`agent/tests/test_chat.py`

**第 1 步：编写失败的测试**

`agent/tests/test_chat.py`：

```python
from fastapi.testclient import TestClient

from app.main import create_app


def test_chat_requires_user_id_and_message():
    client = TestClient(create_app())
    resp = client.post("/api/agent/chat", json={})
    assert resp.status_code == 422
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_chat.py -v
```

预期：当前返回 404，断言失败。

**第 3 步：编写最小实现**

`agent/app/deps.py`：

```python
from functools import lru_cache

from app.config import Settings
from app.core.llm import LLMClient


@lru_cache
def get_settings() -> Settings:
    return Settings()


@lru_cache
def get_llm() -> LLMClient:
    return LLMClient(get_settings())
```

`agent/app/api/chat.py`：

```python
import json

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.core.agent import Agent
from app.core.llm import LLMClient
from app.deps import get_llm, get_settings

router = APIRouter(prefix="/api/agent")


class ChatRequest(BaseModel):
    userId: int
    message: str
    conversationId: int | None = None  # 任务10接入持久化


def _sse(data: dict) -> str:
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/chat")
async def chat(req: ChatRequest, llm: LLMClient = Depends(get_llm)):
    settings = get_settings()
    agent = Agent(user_id=req.userId, mcp_url=settings.mcp_server_url, llm=llm)

    async def event_stream():
        yield _sse({"type": "user", "content": req.message})
        try:
            answer = await agent.run(req.message)
            yield _sse({"type": "assistant", "content": answer})
        except Exception as exc:
            yield _sse({"type": "error", "content": str(exc)})
        yield _sse({"type": "done"})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
```

`agent/app/main.py` 注册路由：

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.chat import router as chat_router


def create_app() -> FastAPI:
    app = FastAPI(title="Cloud-Pan Agent", version="0.1.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(chat_router)

    @app.get("/api/agent/health")
    def health():
        return {"status": "ok"}

    return app


app = create_app()
```

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_chat.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add agent/
git commit -m "feat(agent): add sse streaming chat endpoint"
```

---

## 任务 10：会话持久化（MySQL）

**文件：**
- 创建：`distribution/conf/db-agent.sql`
- 创建：`agent/app/storage/__init__.py`
- 创建：`agent/app/storage/conversation.py`
- 创建：`agent/tests/test_storage.py`

**第 1 步：编写失败的测试**

`agent/tests/test_storage.py`：

```python
from app.storage.conversation import ConversationStore


def test_memory_store_roundtrip():
    store = ConversationStore(backend="memory")
    conv = store.create_conversation(user_id=1)
    store.append_message(conv.id, "user", "你好")
    store.append_message(conv.id, "assistant", "你好！")
    messages = store.list_messages(conv.id)
    assert [m["role"] for m in messages] == ["user", "assistant"]
```

**第 2 步：运行测试确认它失败**

```bash
python -m pytest tests/test_storage.py -v
```

预期：以 `ModuleNotFoundError` 失败。

**第 3 步：编写最小实现**

`distribution/conf/db-agent.sql`：

```sql
-- Agent 智能体会话表
CREATE TABLE IF NOT EXISTS `r_pan_agent_conversation` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '云盘用户ID',
    `title` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '会话标题',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

-- Agent 对话消息表
CREATE TABLE IF NOT EXISTS `r_pan_agent_message` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(16) NOT NULL COMMENT 'user/assistant/tool',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息表';
```

`agent/app/storage/conversation.py`：

```python
import time
from dataclasses import dataclass


@dataclass
class Conversation:
    id: int
    user_id: int
    title: str = ""


class ConversationStore:
    """MVP 用内存后端；MySQL 后端按相同接口实现，靠 config 切换。"""

    def __init__(self, backend: str = "memory"):
        self.backend = backend
        self._seq = 0
        self._convs: dict[int, Conversation] = {}
        self._msgs: dict[int, list[dict]] = {}

    def create_conversation(self, user_id: int, title: str = "") -> Conversation:
        self._seq += 1
        conv = Conversation(id=self._seq, user_id=user_id, title=title)
        self._convs[conv.id] = conv
        self._msgs[conv.id] = []
        return conv

    def append_message(self, conversation_id: int, role: str, content: str):
        self._msgs.setdefault(conversation_id, []).append(
            {"role": role, "content": content, "time": int(time.time())}
        )

    def list_messages(self, conversation_id: int) -> list[dict]:
        return self._msgs.get(conversation_id, [])

    def list_conversations(self, user_id: int) -> list[Conversation]:
        return [c for c in self._convs.values() if c.user_id == user_id]
```

> MySQL 后端（`backend="mysql"`）用 SQLAlchemy 或 mysql-connector 实现相同接口，SQL 见 `db-agent.sql`，作为本任务补充子步骤实现。

**第 4 步：运行测试确认它通过**

```bash
python -m pytest tests/test_storage.py -v
```

预期：PASS。

**第 5 步：提交**

```bash
git add distribution/conf/db-agent.sql agent/
git commit -m "feat(agent): add conversation persistence"
```

---

## 任务 11：前端聊天界面（Vue3）

**文件：**
- 创建：`frontend/src/api/agent/index.js`
- 创建：`frontend/src/views/agent/AgentChat.vue`
- 修改：`frontend/src/router/index.js`
- 修改：`frontend/src/utils/common/index.js`（加 `getAgentUrlPrefix()`）

**第 1 步：编写最小实现**

`frontend/src/utils/common/index.js` 增加：

```js
getAgentUrlPrefix() { return 'http://127.0.0.1:8000' }
```

> 生产环境建议 nginx 把 `/agent` 前缀转发到 Python 服务 :8000，聊天即可与业务同域。

`frontend/src/api/agent/index.js`：

```js
import { getAgentUrlPrefix, getToken } from '@/utils/common/index.js'

const AGENT_BASE = getAgentUrlPrefix()

export function chatStream(userId, message, conversationId = null, onEvent) {
  const body = JSON.stringify({ userId, message, conversationId })
  fetch(`${AGENT_BASE}/api/agent/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: getToken() },
    body,
  })
    .then((resp) => resp.body.getReader())
    .then(async (reader) => {
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop()
        for (const part of parts) {
          if (!part.startsWith('data: ')) continue
          onEvent(JSON.parse(part.slice(6)))
        }
      }
    })
}
```

`frontend/src/views/agent/AgentChat.vue`：聊天面板（消息列表 + 输入框 + Enter 发送 + SSE 实时追加），风格对齐现有 `src/views/file`：

```vue
<template>
  <div class="agent-chat">
    <el-scrollbar ref="scrollRef" class="msg-list">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ m.content }}</div>
      </div>
    </el-scrollbar>
    <div class="input-bar">
      <el-input v-model="input" placeholder="问我：帮我找上周的报告…" @keyup.enter="send" />
      <el-button type="primary" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { chatStream } from '@/api/agent/index.js'
import { useUserStore } from '@/stores/user' // 按项目实际 store 路径

const messages = ref([])
const input = ref('')
const userId = useUserStore().userId

async function send() {
  const text = input.value.trim()
  if (!text) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  const assistant = { role: 'assistant', content: '' }
  messages.value.push(assistant)
  await chatStream(userId, text, null, (ev) => {
    if (ev.type === 'assistant') assistant.content = ev.content
    if (ev.type === 'error') assistant.content = `错误：${ev.content}`
  })
}
</script>
```

**第 2 步：路由接入**

`frontend/src/router/index.js` 增加：

```js
{
  path: '/agent',
  name: 'AgentChat',
  component: () => import('@/views/agent/AgentChat.vue'),
}
```

并在主导航菜单加"智能助手"入口（参考现有菜单实现）。

**第 3 步：人工验证**

```bash
cd d:/Project/cloud-pan/frontend && npm run dev
```

预期：打开 `/agent` 页面，输入"帮我创建一个名为 测试归档 的文件夹"，看到 SSE 流式回复。

**第 4 步：提交**

```bash
git add frontend/
git commit -m "feat(frontend): add agent chat page"
```

---

## 任务 12：本地联调验证

**文件：** 无（验证步骤）

**第 1 步：启动 Java 云盘后端**

```bash
cd d:/Project/cloud-pan
mvn -pl server -am clean package -DskipTests
java -jar server/target/*.jar
```

预期：端口 10000 起来。

**第 2 步：启动 Java MCP Server**

```bash
cd d:/Project/cloud-pan/mcp-server
set PAN_BASE_URL=http://127.0.0.1:10000   # PowerShell: $env:PAN_BASE_URL="http://127.0.0.1:10000"
mvn package -DskipTests
java -jar target/cloud-drive-mcp-server-1.0.jar
```

预期：控制台输出 `Cloud-Pan MCP Server listening on http://0.0.0.0:10001/mcp`。冒烟：

```bash
curl -N -X POST http://127.0.0.1:10001/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

预期：返回 9 个工具。

**第 3 步：启动 Python Agent**

```bash
cd d:/Project/cloud-pan/agent
set AGENT_LLM_API_KEY=<你的key>            # PowerShell: $env:AGENT_LLM_API_KEY="<key>"
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

预期：`http://127.0.0.1:8000/docs` 可打开。

**第 4 步：端到端验证**

```bash
curl -N -X POST http://127.0.0.1:8000/api/agent/chat -H "Content-Type: application/json" \
  -d '{"userId":1,"message":"帮我创建一个名为 测试归档 的文件夹"}'
```

预期：SSE 事件流，最终返回创建成功；MCP Server 日志出现 `tools/call create_folder`；云盘后端日志出现 `MCP_1` 请求。

**第 5 步：提交（如有修正）**

```bash
git add -A
git commit -m "fix(agent): tune integration after end-to-end verification"
```

---

## 后续增强（不在本计划范围）

- MCP Server 双向认证（OAuth / 自定义 token），防止越权调用
- Agent 与 MCP Server 之间接入会话鉴权（`userId` 从登录态派生，而非前端直传）
- RAG 文档问答（LlamaIndex + Qdrant + bge-m3）、报表生成
- 逐 token 流式（LLM `stream=true` + 工具判定）
- 多轮上下文管理（历史消息灌入 prompt）
- 异步长任务（批量归档）接入 RocketMQ / celery

## 验收清单

- [x] `mcp-server`：`mvn test` 通过（21 个测试），`:10001/mcp` 能 `initialize` + `tools/list`（9 个工具）+ `tools/call`
- [x] `agent`：`pytest` 全部通过（18 个测试）
- [x] 端到端：curl 对话接口返回 SSE（`tool_start → tool_end → delta → done`），实际通过 MCP 调用云盘后端（`code:0 SUCCESS`）
- [x] 前端 `/agent` 页面可自然语言操作文件（路由 + 导航入口 + SSE 流式渲染已接入）
- [x] Java 云盘后端零改动（仅复用 `MCP_` 鉴权）
- [x] 会话持久化可落库（SQLite 测试通过；生产 MySQL 表 `r_pan_agent_session` / `r_pan_agent_message` 由 SQLAlchemy 自动建表）

## 执行状态（2026-08-12 完成）

| 任务 | 状态 | 说明 |
|------|------|------|
| 1 Python 服务脚手架 | ✅ | `agent/` + venv + config |
| 2 FastAPI 骨架 | ✅ | CORS + 健康检查 + `R<T>` 统一响应 |
| 3 LLM 客户端 | ✅ | httpx 手写 OpenAI 兼容，非流式 + 流式 + 工具调用 |
| 4 Java MCP Server 工程 | ✅ | 独立 Maven 工程（Jetty 12 + mcp 1.0.0），不继承 cloud-drive-parent |
| 5 Streamable HTTP transport | ✅ | `HttpServletStreamableServerTransportProvider` 注册到 Jetty，端口 10001 |
| 6 云盘 MCP 工具 | ✅ | 9 个工具（list/search/create_folder/rename/delete/transfer/copy/restore/create_share），`userId` 为首参 |
| 7 Python MCP Client | ✅ | **手写 Streamable HTTP 协议**（环境内 mcp SDK 被裁剪，弃用 SDK，httpx 直连 JSON-RPC/SSE） |
| 8 Agent 循环 | ✅ | 动态拉取 MCP 工具 → 剥离 userId → function calling 循环 |
| 9 SSE 对话接口 | ✅ | `POST /api/agent/chat`，事件 `tool_start/tool_end/delta/done/error` |
| 10 会话持久化 | ✅ | SQLAlchemy async，测试 SQLite / 生产 MySQL，sessionId 续聊 |
| 11 前端聊天页 | ✅ | `views/agent/index.vue` + 路由 + 导航"智能助手"入口，JWT payload 解析 userId |
| 12 端到端联调 | ✅ | 全链路打通：前端 → Agent → LLM(mock) → MCP → 云盘后端 |

**已知取舍**：当前 LLM 工具决策为非流式（一次额外调用），最终答案流式输出；真实 LLM 需配置 `AGENT_LLM_API_KEY` 后验证。
