# 启动流程

开启docker
包括RustFS、Redis、Postgres、RocketMQ

# 启动后端
在项目根目录执行：
```base
# mvn clean install -DskipTests      # 先构建 framework 等子模块
cd server
mvn spring-boot:run
```     

# 启动MCP服务
```base
cd mcp-server
mvn package
java -jar target/cloud-drive-mcp-server-1.0.jar
```

# 启动Agent
```base
$env:AGENT_LLM_BASE_URL = "https://api.deepseek.com/v1"
$env:AGENT_LLM_API_KEY  = "你的Key"
$env:AGENT_LLM_MODEL    = "deepseek-chat"

cd agent
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000

```

# 启动前端
```base
 cd .\frontend\
 npm run dev
```
