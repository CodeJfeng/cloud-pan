<template>
    <div class="agent-chat">
        <div class="chat-header">
            <div class="chat-title">
                <el-icon :size="22" color="#09AAFF"><ChatDotRound/></el-icon>
                <span>云盘智能助手</span>
            </div>
            <div class="chat-actions">
                <el-button size="small" @click="newSession" :disabled="sending">
                    <el-icon><Plus/></el-icon>&nbsp;新会话
                </el-button>
            </div>
        </div>

        <div class="chat-body" ref="bodyRef">
            <div v-if="messages.length === 0" class="chat-empty">
                <div class="empty-icon">
                    <el-icon :size="52"><MagicStick/></el-icon>
                </div>
                <p class="empty-title">我能帮你做什么？</p>
                <p class="empty-sub">试试对我说：帮我查找"合同"文件、新建一个"工作资料"文件夹、把文件移动到回收站…</p>
            </div>

            <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
                <div class="msg-avatar" :class="msg.role">
                    <el-icon v-if="msg.role === 'user'" :size="18"><User/></el-icon>
                    <el-icon v-else :size="18"><MagicStick/></el-icon>
                </div>
                <div class="msg-content">
                    <template v-if="msg.role === 'assistant' && msg.tooling">
                        <div class="tool-tip">
                            <el-icon class="is-loading"><Loading/></el-icon>
                            <span>正在调用 {{ msg.toolName }}…</span>
                        </div>
                    </template>
                    <div class="msg-text" v-html="renderContent(msg.content)"/>
                    <div v-if="msg.role === 'assistant' && msg.traceId" class="trace-entry">
                        <el-button size="small" link type="primary" :disabled="msg.traceLoading" @click="toggleTrace(msg)">
                            <el-icon :class="{'is-loading': msg.traceLoading}"><Connection/></el-icon>
                            {{ msg.showTrace ? '收起链路' : '查看链路' }}
                        </el-button>
                    </div>
                    <div v-if="msg.role === 'assistant' && msg.showTrace" class="trace-panel">
                        <div v-if="msg.traceLoading" class="trace-empty">加载中…</div>
                        <div v-else-if="!msg.traceSteps || msg.traceSteps.length === 0" class="trace-empty">暂无链路数据</div>
                        <div v-else>
                            <div v-for="(step, si) in msg.traceSteps" :key="si" class="trace-step" :class="step.status">
                                <div class="trace-step-head">
                                    <span class="trace-badge" :class="step.stepType">{{ stepLabel(step.stepType) }}</span>
                                    <span v-if="step.stepName" class="trace-tool">{{ step.stepName }}</span>
                                    <span v-if="step.durationMs != null" class="trace-dur">{{ step.durationMs }} ms</span>
                                    <span v-if="step.status === 'error'" class="trace-fail">失败</span>
                                    <span v-else-if="step.status === 'rejected'" class="trace-fail rejected-fail">已拒绝</span>
                                    <span v-else-if="step.status === 'approved'" class="trace-pass">已批准</span>
                                </div>
                                <div v-if="step.stepType === 'tool_call'" class="trace-detail">
                                    <div class="trace-label">参数</div>
                                    <pre class="trace-code">{{ formatJson(step.payload && step.payload.arguments) }}</pre>
                                    <div class="trace-label">结果</div>
                                    <pre class="trace-code">{{ formatJson(step.payload && step.payload.result) }}</pre>
                                </div>
                                <div v-else-if="step.stepType === 'llm_decision'" class="trace-detail">
                                    <div class="trace-label">
                                        {{ step.payload && step.payload.tools && step.payload.tools.length
                                            ? '调用工具：' + step.payload.tools.join('、')
                                            : '直接回答（未调用工具）' }}
                                    </div>
                                </div>
                                <div v-else-if="step.stepType === 'message_in'" class="trace-detail">
                                    <div class="trace-label">{{ step.payload && step.payload.content }}</div>
                                </div>
                                <div v-else-if="step.stepType === 'confirmation'" class="trace-detail">
                                    <div class="trace-label">工具：{{ toolLabel(step.payload && step.payload.tool) }}</div>
                                    <div class="trace-label">结果：{{ step.status === 'approved' ? '已批准' : (step.status === 'rejected' ? '已拒绝' : '等待批复') }}</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div v-if="sending && !lastTooling" class="msg-row assistant">
                <div class="msg-avatar assistant">
                    <el-icon :size="18"><MagicStick/></el-icon>
                </div>
                <div class="msg-content">
                    <div class="typing">
                        <span class="dot"/><span class="dot"/><span class="dot"/>
                    </div>
                </div>
            </div>
        </div>

        <div class="chat-input">
            <el-input
                v-model="input"
                type="textarea"
                :rows="2"
                resize="none"
                placeholder="描述你想对网盘文件做的事…（Enter 发送，Shift+Enter 换行）"
                @keydown.enter.exact.prevent="send"
                :disabled="sending"
            />
            <el-button type="primary" class="send-btn" :loading="sending" @click="send" :disabled="!input.trim()">
                发送
            </el-button>
        </div>
    </div>
</template>

<script setup>
import {nextTick, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {agentChat, agentConfirm, agentHistory, agentTraceDetail} from '@/api/agent'
import {getToken} from '@/utils/cookie'

const messages = ref([])
const input = ref('')
const sending = ref(false)
const bodyRef = ref(null)
const sessionId = ref('')
const lastTooling = ref(false)

const SESSION_KEY = 'agent_session_id'

function getUserIdFromToken() {
    const token = getToken()
    if (!token) return null
    const parts = token.split('.')
    if (parts.length < 2) return null
    try {
        // JWT payload 是 base64url，转回标准 base64 并补齐 padding
        let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        base64 += '='.repeat((4 - (base64.length % 4)) % 4)
        const json = JSON.parse(atob(base64))
        // 后端把用户 ID 存在 LOGIN_USER_ID claim（见 JwtUtil.generateToken），兼容 userId 两种写法
        const uid = json.LOGIN_USER_ID ?? json.userId
        return uid != null ? String(uid) : null
    } catch (e) {
        return null
    }
}

function renderContent(text) {
    return (text || '').replace(/\n/g, '<br/>')
}

function scrollToBottom() {
    nextTick(() => {
        if (bodyRef.value) {
            bodyRef.value.scrollTop = bodyRef.value.scrollHeight
        }
    })
}

async function toggleTrace(msg) {
    msg.showTrace = !msg.showTrace
    if (!msg.showTrace || msg.traceSteps) return
    const userId = getUserIdFromToken()
    if (!userId || !msg.traceId) {
        msg.traceSteps = []
        return
    }
    msg.traceLoading = true
    try {
        const data = await agentTraceDetail(msg.traceId, userId)
        msg.traceSteps = data.steps || []
    } catch (e) {
        msg.traceSteps = []
        ElMessage.warning(e.message || '链路查询失败')
    } finally {
        msg.traceLoading = false
        scrollToBottom()
    }
}

function stepLabel(type) {
    return {
        message_in: '消息进入',
        llm_decision: 'LLM 决策',
        tool_call: '工具调用',
        confirmation: '操作审批',
        answer: '生成回答',
        error: '异常',
    }[type] || type
}

function toolLabel(tool) {
    return {
        delete_file: '删除文件',
        transfer_files: '移动文件',
    }[tool] || tool
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[c]))
}

function buildConfirmText(tool, args) {
    return `<div style="line-height:1.6">助手准备执行「${escapeHtml(toolLabel(tool))}」操作，请确认是否允许：</div>` +
        `<pre style="background:#f5f7fa;padding:8px;border-radius:6px;margin:8px 0 0;font-size:12px;white-space:pre-wrap;word-break:break-all;">${escapeHtml(formatJson(args))}</pre>`
}

async function requestConfirm(userId, requestId, tool, args) {
    let approved = false
    try {
        await ElMessageBox.confirm(buildConfirmText(tool, args), '需要你的批准', {
            confirmButtonText: '允许执行',
            cancelButtonText: '拒绝',
            type: 'warning',
            dangerouslyUseHTMLString: true,
            autofocus: false,
        })
        approved = true
    } catch (e) {
        approved = false // 关闭弹窗/按 ESC 均视为拒绝
    }
    try {
        await agentConfirm(requestId, approved, String(userId))
        if (approved) {
            ElMessage.success('已批准，继续执行')
        } else {
            ElMessage.warning('已拒绝该操作')
        }
    } catch (e) {
        ElMessage.error(e.message || '提交批准结果失败')
    }
    return approved
}

function formatJson(v) {
    if (v == null) return '-'
    if (typeof v === 'string') return v || '-'
    try {
        return JSON.stringify(v, null, 2)
    } catch (e) {
        return String(v)
    }
}

function newSession() {
    sessionId.value = ''
    localStorage.removeItem(SESSION_KEY)
    messages.value = []
    input.value = ''
}

async function send() {
    const text = input.value.trim()
    if (!text || sending.value) return
    const userId = getUserIdFromToken()
    if (!userId) {
        ElMessage.error('登录状态失效，请重新登录')
        return
    }

    messages.value.push({role: 'user', content: text})
    messages.value.push({role: 'assistant', content: '', tooling: false})
    input.value = ''
    sending.value = true
    lastTooling.value = false

    const cur = messages.value.length - 1
    try {
        await agentChat(text, String(userId), sessionId.value || null, async ev => {
            if (ev.event === 'confirmation_required') {
                messages.value[cur].tooling = true
                messages.value[cur].toolName = `等待你批准：${toolLabel(ev.data.tool)}`
                lastTooling.value = true
                scrollToBottom()
                await requestConfirm(userId, ev.data.requestId, ev.data.tool, ev.data.arguments)
            } else if (ev.event === 'tool_start') {
                messages.value[cur].tooling = true
                messages.value[cur].toolName = ev.data.tool
                lastTooling.value = true
                scrollToBottom()
            } else if (ev.event === 'tool_end') {
                messages.value[cur].tooling = false
                lastTooling.value = false
                scrollToBottom()
            } else if (ev.event === 'delta') {
                messages.value[cur].content += ev.data.content
                scrollToBottom()
            } else if (ev.event === 'done') {
                if (ev.data.sessionId) {
                    sessionId.value = ev.data.sessionId
                    localStorage.setItem(SESSION_KEY, sessionId.value)
                }
                if (ev.data.traceId) {
                    messages.value[cur].traceId = ev.data.traceId
                }
                if (!messages.value[cur].content && ev.data.content) {
                    messages.value[cur].content = ev.data.content
                }
                scrollToBottom()
            } else if (ev.event === 'error') {
                messages.value[cur].content = ev.data.content || '出错了'
            }
        })
    } catch (e) {
        messages.value[cur].content = e.message || '请求失败，请确认 Agent 服务已启动'
    } finally {
        sending.value = false
        lastTooling.value = false
        scrollToBottom()
    }
}

// 恢复历史会话：从后端拉取该会话的消息记录并渲染
const saved = localStorage.getItem(SESSION_KEY)
if (saved) {
    sessionId.value = saved
    const userId = getUserIdFromToken()
    if (userId) {
        agentHistory(saved, userId)
            .then(data => {
                const history = data.messages || []
                if (history.length > 0) {
                    messages.value = history.map(m => ({
                        role: m.role === 'user' ? 'user' : 'assistant',
                        content: m.content || '',
                        tooling: false,
                        traceId: m.trace_id || m.traceId || null,
                    }))
                } else {
                    messages.value.push({role: 'assistant', content: '（已恢复上次会话，你可以继续向我提问）'})
                }
                scrollToBottom()
            })
            .catch(() => {
                messages.value.push({role: 'assistant', content: '（已恢复上次会话，你可以继续向我提问）'})
            })
    } else {
        messages.value.push({role: 'assistant', content: '（已恢复上次会话，你可以继续向我提问）'})
    }
}
</script>

<style scoped>
.agent-chat {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-width: 0;
    background: #fff;
}

.chat-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 24px;
    border-bottom: 1px solid #f0f0f0;
}

.chat-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 18px;
    font-weight: bold;
    color: #333;
}

.chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: #fafbfc;
}

.chat-empty {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #909399;
}

.empty-icon {
    margin-bottom: 16px;
    color: #09AAFF;
    opacity: .35;
}

.empty-title {
    font-size: 20px;
    font-weight: bold;
    color: #333;
    margin: 0 0 8px;
}

.empty-sub {
    font-size: 13px;
    color: #909399;
    max-width: 480px;
    text-align: center;
    line-height: 1.8;
}

.msg-row {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
}

.msg-row.user {
    flex-direction: row-reverse;
}

.msg-avatar {
    width: 34px;
    height: 34px;
    border-radius: 8px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
}

.msg-avatar.user {
    background: #09AAFF;
    color: #fff;
}

.msg-avatar.assistant {
    background: #e8f4ff;
    color: #09AAFF;
}

.msg-content {
    max-width: 70%;
}

.msg-row.user .msg-content {
    background: #09AAFF;
    color: #fff;
    padding: 10px 14px;
    border-radius: 10px 2px 10px 10px;
}

.msg-row.assistant .msg-content {
    background: #fff;
    color: #333;
    padding: 10px 14px;
    border-radius: 2px 10px 10px 10px;
    border: 1px solid #f0f0f0;
    box-shadow: 0 1px 3px rgba(0, 0, 0, .04);
    line-height: 1.7;
    font-size: 14px;
    word-break: break-word;
}

.tool-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #09AAFF;
    font-size: 13px;
}

.typing {
    display: flex;
    gap: 4px;
    padding: 4px 0;
}

.dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #09AAFF;
    animation: blink 1.2s infinite;
}

.dot:nth-child(2) {animation-delay: .2s}
.dot:nth-child(3) {animation-delay: .4s}

@keyframes blink {
    0%, 80%, 100% {opacity: .2}
    40% {opacity: 1}
}

.chat-input {
    display: flex;
    gap: 12px;
    align-items: flex-end;
    padding: 16px 24px;
    border-top: 1px solid #f0f0f0;
    background: #fff;
}

.chat-input :deep(.el-textarea__inner) {
    font-size: 14px;
    border-radius: 8px;
}

.send-btn {
    height: 56px;
    border-radius: 8px;
    padding: 0 28px;
}

/* ---- 链路追踪 ---- */
.trace-entry {
    margin-top: 8px;
    border-top: 1px dashed #eee;
    padding-top: 6px;
}

.trace-panel {
    margin-top: 8px;
    border: 1px solid #e6e8eb;
    border-radius: 8px;
    background: #fafbfc;
    padding: 8px 10px;
    max-height: 320px;
    overflow-y: auto;
}

.trace-empty {
    color: #909399;
    font-size: 12px;
    text-align: center;
    padding: 8px 0;
}

.trace-step {
    padding: 8px 0;
    border-bottom: 1px dashed #eee;
}

.trace-step:last-child {
    border-bottom: none;
}

.trace-step.error .trace-step-head {
    color: #f56c6c;
}

.trace-step-head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.trace-badge {
    font-size: 11px;
    padding: 1px 8px;
    border-radius: 10px;
    background: #e8f4ff;
    color: #09AAFF;
}

.trace-badge.tool_call {
    background: #f0f9eb;
    color: #67c23a;
}

.trace-badge.confirmation {
    background: #fdf6ec;
    color: #e6a23c;
}

.trace-badge.error {
    background: #fef0f0;
    color: #f56c6c;
}

.trace-tool {
    font-size: 12px;
    font-weight: bold;
    color: #333;
}

.trace-dur {
    font-size: 11px;
    color: #909399;
}

.trace-fail {
    font-size: 11px;
    color: #f56c6c;
}

.trace-fail.rejected-fail {
    color: #e6a23c;
}

.trace-pass {
    font-size: 11px;
    color: #67c23a;
}

.trace-detail {
    margin-top: 6px;
}

.trace-label {
    font-size: 11px;
    color: #909399;
    margin-bottom: 2px;
}

.trace-code {
    margin: 0 0 6px;
    padding: 6px 8px;
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 4px;
    font-size: 11px;
    line-height: 1.5;
    color: #555;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 120px;
    overflow-y: auto;
}
</style>
