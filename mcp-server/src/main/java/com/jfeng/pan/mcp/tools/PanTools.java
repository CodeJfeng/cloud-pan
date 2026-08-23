package com.jfeng.pan.mcp.tools;

import com.jfeng.pan.mcp.config.McpServerConfig;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 云盘 MCP 工具集合。每个工具：
 * <ul>
 *   <li>第一个入参为 {@code userId}（String，required，内部注入参数，Python Agent 自动填充）。</li>
 *   <li>内部用 userId 拼 {@code Bearer MCP_<userId>} 调云盘 API。</li>
 *   <li>返回 TextContent 可读文本，失败返回 isError(true)。</li>
 * </ul>
 */
public class PanTools {

    /** 云盘多 ID 拼接分隔符。 */
    public static final String ID_SEPARATOR = "__,__";

    private final PanApiClient pan;
    private final McpJsonMapper json;

    public PanTools(String panBaseUrl) {
        this.pan = new PanApiClient(panBaseUrl);
        this.json = McpServerConfig.jsonMapper();
    }

    /** 把多 ID 列表拼成云盘要求的 `__,__` 分隔字符串（原样透传加密 ID）。 */
    private String joinIds(List<?> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(ID_SEPARATOR);
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private int intVal(Map<String, Object> a, String key, int def) {
        Object v = a.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private long userId(Map<String, Object> a) {
        // 用户 ID 为雪花 ID（Long），不能用 int 解析，否则溢出抛 NumberFormatException
        return Long.parseLong(String.valueOf(a.get("userId")));
    }

    /** 构建入参 JSON Schema（描述可读、userId 为 required 首参）。 */
    private McpSchema.JsonSchema schema(String jsonStr) {
        try {
            return json.readValue(jsonStr, McpSchema.JsonSchema.class);
        } catch (Exception e) {
            throw new IllegalStateException("invalid tool schema JSON: " + jsonStr, e);
        }
    }

    /** 构建一个同步工具规范。 */
    private McpServerFeatures.SyncToolSpecification tool(
            String name, String description, String schemaJson,
            BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        McpSchema.Tool t = McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(schema(schemaJson))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(t)
                .callHandler(handler)
                .build();
    }

    /** 成功结果。 */
    private McpSchema.CallToolResult ok(String text) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .build();
    }

    /** 失败结果。 */
    private McpSchema.CallToolResult error(String text) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .isError(true)
                .build();
    }

    /** 执行云盘调用并把响应包装为文本结果（code=0 视为成功）。 */
    private McpSchema.CallToolResult callPan(
            McpSchema.CallToolRequest request, String op, java.util.function.Supplier<String> doCall) {
        try {
            String body = doCall.get();
            return ok("[" + op + "] 云盘响应: " + body);
        } catch (Exception e) {
            return error("[" + op + "] 调用云盘接口失败: " + e.getMessage());
        }
    }

    /** 注册全部 9 个工具到 MCP Server。 */
    public void register(McpSyncServer server) {
        for (McpServerFeatures.SyncToolSpecification spec : allSpecifications()) {
            server.addTool(spec);
        }
    }

    /** 返回全部工具规范（测试与注册共用）。共 9 个工具。 */
    public List<McpServerFeatures.SyncToolSpecification> allSpecifications() {
        return List.of(
                listFiles(),
                searchFiles(),
                createFolder(),
                renameFile(),
                deleteFile(),
                transferFiles(),
                copyFiles(),
                restoreFile(),
                createShare());
    }

    // ---------- 工具定义 ----------

    private McpServerFeatures.SyncToolSpecification listFiles() {
        return tool("list_files", "列出云盘某目录下的文件与文件夹（返回文件列表 JSON）",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "parentId":{"type":"string","description":"父目录ID（加密串，根目录传-1）"}},
                 "required":["userId","parentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    // 兼容历史约定的 "ALL"/空值，映射为云盘根目录标识 -1，避免解密异常
                    String parentId = str(a, "parentId");
                    if (parentId.isBlank() || "ALL".equalsIgnoreCase(parentId)) {
                        parentId = "-1";
                    }
                    String url = pan.baseUrl() + "/files?parentId=" + parentId;
                    return callPan(request, "list_files", () -> {
                        try {
                            return pan.get(userId(a), url);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification searchFiles() {
        return tool("search_files", "按关键词全盘搜索云盘中的文件（返回文件列表 JSON）",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "keyword":{"type":"string","description":"搜索关键词"}},
                 "required":["userId","keyword"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String url = pan.searchUrl(str(a, "keyword"));
                    return callPan(request, "search_files", () -> {
                        try {
                            return pan.get(userId(a), url);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification createFolder() {
        return tool("create_folder", "在云盘指定目录下创建文件夹",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "parentId":{"type":"string","description":"父目录ID（加密串）"},
                   "folderName":{"type":"string","description":"新文件夹名称"}},
                 "required":["userId","parentId","folderName"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"parentId\":" + quote(str(a, "parentId"))
                            + ",\"folderName\":" + quote(str(a, "folderName")) + "}";
                    return callPan(request, "create_folder", () -> {
                        try {
                            return pan.post(userId(a), pan.baseUrl() + "/file/folder", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification renameFile() {
        return tool("rename_file", "重命名云盘文件或文件夹",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileId":{"type":"string","description":"要重命名的文件/文件夹ID（加密串）"},
                   "newFilename":{"type":"string","description":"新的名称"}},
                 "required":["userId","fileId","newFilename"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileId\":" + quote(str(a, "fileId"))
                            + ",\"newFilename\":" + quote(str(a, "newFilename")) + "}";
                    return callPan(request, "rename_file", () -> {
                        try {
                            return pan.put(userId(a), pan.baseUrl() + "/file", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification deleteFile() {
        return tool("delete_file", "删除云盘文件/文件夹（移入回收站）",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileIds":{"type":"array","items":{"type":"string"},"description":"要删除的文件ID列表（加密串）"},
                   "deleteFlag":{"type":"integer","description":"可选，删除标记"}},
                 "required":["userId","fileIds"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileIds\":" + quote(joinIds(listOf(a.get("fileIds")))) + "}";
                    return callPan(request, "delete_file", () -> {
                        try {
                            return pan.delete(userId(a), pan.baseUrl() + "/file", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification transferFiles() {
        return tool("transfer_files", "把文件/文件夹移动到目标目录",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileIds":{"type":"array","items":{"type":"string"},"description":"要移动的文件ID列表（加密串）"},
                   "targetParentId":{"type":"string","description":"目标父目录ID（加密串）"}},
                 "required":["userId","fileIds","targetParentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileIds\":" + quote(joinIds(listOf(a.get("fileIds"))))
                            + ",\"targetParentId\":" + quote(str(a, "targetParentId")) + "}";
                    return callPan(request, "transfer_files", () -> {
                        try {
                            return pan.post(userId(a), pan.baseUrl() + "/file/transfer", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification copyFiles() {
        return tool("copy_files", "复制文件/文件夹到目标目录",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileIds":{"type":"array","items":{"type":"string"},"description":"要复制的文件ID列表（加密串）"},
                   "targetParentId":{"type":"string","description":"目标父目录ID（加密串）"}},
                 "required":["userId","fileIds","targetParentId"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileIds\":" + quote(joinIds(listOf(a.get("fileIds"))))
                            + ",\"targetParentId\":" + quote(str(a, "targetParentId")) + "}";
                    return callPan(request, "copy_files", () -> {
                        try {
                            return pan.post(userId(a), pan.baseUrl() + "/file/copy", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification restoreFile() {
        return tool("restore_file", "从回收站批量还原文件",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileIds":{"type":"array","items":{"type":"string"},"description":"要还原的文件ID列表（加密串）"}},
                 "required":["userId","fileIds"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"fileIds\":" + quote(joinIds(listOf(a.get("fileIds")))) + "}";
                    return callPan(request, "restore_file", () -> {
                        try {
                            return pan.put(userId(a), pan.baseUrl() + "/recycle/restore", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    private McpServerFeatures.SyncToolSpecification createShare() {
        return tool("create_share", "创建文件分享链接，返回分享链接与提取码",
                """
                {"type":"object",
                 "properties":{
                   "userId":{"type":"string","description":"云盘用户ID（内部注入参数，勿询问用户）"},
                   "fileIds":{"type":"array","items":{"type":"string"},"description":"要分享的文件ID列表（加密串）"},
                   "validDay":{"type":"integer","description":"有效天数（shareDayType：1天/7天/30天/永久等）"},
                   "shareType":{"type":"integer","description":"分享类型（0=有提取码，1=无提取码）"},
                   "code":{"type":"string","description":"可选，自定义提取码"}},
                 "required":["userId","fileIds","validDay","shareType"]}
                """,
                (exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String body = "{\"shareName\":" + quote("share")
                            + ",\"shareType\":" + intVal(a, "shareType", 0)
                            + ",\"shareDayType\":" + intVal(a, "validDay", 7)
                            + ",\"shareFileIds\":" + quote(joinIds(listOf(a.get("fileIds")))) + "}";
                    return callPan(request, "create_share", () -> {
                        try {
                            return pan.post(userId(a), pan.baseUrl() + "/share", body);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
    }

    // ---------- 工具函数 ----------

    @SuppressWarnings("unchecked")
    private List<Object> listOf(Object v) {
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        if (v == null) {
            return List.of();
        }
        return List.of(v);
    }

    private String quote(String s) {
        // 简单转义双引号与反斜杠，保证 JSON 字符串合法
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
