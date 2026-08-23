/**
 * 云盘智能助手 Agent API（SSE 流式）
 * Agent 服务独立运行在 127.0.0.1:8000
 */
const AGENT_BASE = 'http://127.0.0.1:8000'

/**
 * 解析 SSE 块，返回 {event, data}；解析失败返回 null
 */
function parseBlock(block) {
    let event = 'message'
    let data = ''
    for (const line of block.split('\n')) {
        if (line.startsWith('event:')) {
            event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
            data += line.slice(5).trim()
        }
    }
    if (!data) return null
    try {
        return {event, data: JSON.parse(data)}
    } catch (e) {
        return null
    }
}

/**
 * 发送对话请求，SSE 流式接收。
 * @param {string} message 用户消息
 * @param {string} userId  用户ID（注入给 MCP 工具）
 * @param {string|null} sessionId 会话ID（续聊）
 * @param {(ev: {event: string, data: object}) => void} onEvent 事件回调
 */
export async function agentChat(message, userId, sessionId, onEvent) {
    const resp = await fetch(`${AGENT_BASE}/api/agent/chat`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({message, userId, sessionId})
    })
    if (!resp.ok || !resp.body) {
        throw new Error(`Agent 服务请求失败: ${resp.status}`)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
        const {done, value} = await reader.read()
        if (done) break
        buffer += decoder.decode(value, {stream: true})
        let sep
        while ((sep = buffer.indexOf('\n\n')) !== -1) {
            const block = buffer.slice(0, sep)
            buffer = buffer.slice(sep + 2)
            const ev = parseBlock(block)
            if (ev) await onEvent(ev) // 允许回调内等待用户批准
        }
    }
}

/**
 * 列出当前用户的历史会话（按更新时间倒序）。
 * @param {string} userId 用户ID
 * @returns {Promise<Array<{sessionId: string, title: string, updatedAt: string|null}>>}
 */
export async function agentSessions(userId) {
    const resp = await fetch(
        `${AGENT_BASE}/api/agent/sessions?userId=${encodeURIComponent(userId)}`,
        {method: 'GET'}
    )
    const json = await resp.json().catch(() => null)
    if (!resp.ok || !json || json.code !== 200) {
        throw new Error(json?.message || `获取会话列表失败: ${resp.status}`)
    }
    return json.data || []
}

/**
 * 查询会话历史消息。
 * @param {string} sessionId 会话ID
 * @param {string} userId    用户ID（校验归属）
 * @returns {Promise<{sessionId: string, title: string, messages: Array}>}
 */
export async function agentHistory(sessionId, userId) {
    const resp = await fetch(
        `${AGENT_BASE}/api/agent/session/${encodeURIComponent(sessionId)}/messages?userId=${encodeURIComponent(userId)}`,
        {method: 'GET'}
    )
    const json = await resp.json().catch(() => null)
    if (!resp.ok || !json || json.code !== 200) {
        throw new Error(json?.message || `获取历史会话失败: ${resp.status}`)
    }
    return json.data
}

/**
 * 查询单条消息处理链路的完整步骤（含归属校验）。
 * @param {string} traceId 链路ID（assistant 消息的 done 事件/历史消息 trace_id）
 * @param {string} userId  用户ID
 * @returns {Promise<{traceId: string, steps: Array}>}
 */
export async function agentTraceDetail(traceId, userId) {
    const resp = await fetch(
        `${AGENT_BASE}/api/agent/trace/${encodeURIComponent(traceId)}?userId=${encodeURIComponent(userId)}`,
        {method: 'GET'}
    )
    const json = await resp.json().catch(() => null)
    if (!resp.ok || !json || json.code !== 200) {
        throw new Error(json?.message || `获取链路详情失败: ${resp.status}`)
    }
    return json.data
}

/**
 * 提交敏感操作（移动/删除）的批准结果。
 * @param {string} requestId 确认请求ID（来自 confirmation_required 事件）
 * @param {boolean} approve  是否批准执行
 * @param {string} userId    用户ID（校验归属）
 */
export async function agentConfirm(requestId, approve, userId) {
    const resp = await fetch(`${AGENT_BASE}/api/agent/confirm`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({requestId, approve, userId})
    })
    const json = await resp.json().catch(() => null)
    if (!resp.ok || !json || json.code !== 200) {
        throw new Error(json?.message || `提交批准结果失败: ${resp.status}`)
    }
    return json.data
}
