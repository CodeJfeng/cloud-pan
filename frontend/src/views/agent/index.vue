<template>
    <div class="agent-chat">
        <div class="chat-header">
            <div class="chat-title">
                <el-icon :size="22" color="#09AAFF"><ChatDotRound/></el-icon>
                <span>云盘智能助手</span>
            </div>
            <div class="chat-actions">
                <el-button size="small" :disabled="sending" @click="loadHistory">
                    <el-icon><Clock/></el-icon>&nbsp;历史会话
                </el-button>
                <el-button size="small" @click="newSession" :disabled="sending">
                    <el-icon><Plus/></el-icon>&nbsp;新会话
                </el-button>
            </div>
        </div>

        <el-dialog v-model="historyVisible" title="历史会话" width="440px" append-to-body>
            <div v-if="historyLoading" class="history-empty">加载中…</div>
            <div v-else-if="historyList.length === 0" class="history-empty">暂无历史会话</div>
            <div v-else class="history-list">
                <div v-for="s in historyList" :key="s.sessionId" class="history-item" @click="openHistory(s)">
                    <div class="history-title">{{ s.title || '（未命名会话）' }}</div>
                    <div class="history-time">{{ formatTime(s.updatedAt) }}</div>
                </div>
            </div>
        </el-dialog>

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
                    <div v-if="msg.role === 'assistant' && msg.steps && msg.steps.length" class="think-steps">
                        <div v-for="(step, si) in msg.steps" :key="si" class="think-block">
                            <div
                                class="think-step"
                                :class="{running: step.running, done: step.done, error: step.error, rejected: step.rejected}"
                                @click="toggleStep(step)"
                            >
                                <el-icon v-if="step.running" class="is-loading"><Loading/></el-icon>
                                <el-icon v-else-if="step.error || step.rejected"><CircleCloseFilled/></el-icon>
                                <el-icon v-else-if="step.done"><CircleCheckFilled/></el-icon>
                                <el-icon v-else><Right/></el-icon>
                                <span class="think-text">{{ step.text }}</span>
                                <span v-if="step.durationMs != null" class="think-dur">{{ step.durationMs }} ms</span>
                            </div>
                            <div v-if="step.kind === 'tool' && step.expanded" class="think-detail">
                                <div v-if="step.args != null" class="think-label">参数</div>
                                <pre v-if="step.args != null" class="think-code">{{ formatJson(step.args) }}</pre>
                                <div v-if="step.result" class="think-label">结果</div>
                                <pre v-if="step.result" class="think-code">{{ step.result }}</pre>
                            </div>
                        </div>
                    </div>
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

        <div class="chat-dir-tip">
            <el-icon :size="14"><Folder/></el-icon>
            <span class="dir-label">当前目录：</span>
            <span class="dir-name">{{ currentDirName }}</span>
            <el-tooltip content="这是智能助手当前的工作目录，涉及文件操作时会优先在该目录下查找" placement="top">
                <el-icon :size="13" class="dir-help"><QuestionFilled/></el-icon>
            </el-tooltip>
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
import {computed, nextTick, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {agentChat, agentConfirm, agentHistory, agentSessions, agentTraceDetail} from '@/api/agent'
import {getToken} from '@/utils/cookie'
import {useFileStore} from '@/stores/file'
import {useBreadcrumbStore} from '@/stores/breadcrumb'

const fileStore = useFileStore()
const breadcrumbStore = useBreadcrumbStore()

// 会话级"工作目录"：优先取文件管理页当前浏览的文件夹，缺省为根目录
const currentDirId = computed(() => fileStore.paramParentId || '-1')
const currentDirName = computed(() => {
    const crumbs = breadcrumbStore.breadCrumbs
    if (crumbs && crumbs.length) {
        const last = crumbs[crumbs.length - 1]
        if (last && last.name) return last.name
    }
    return fileStore.defaultParentFilename || '全部文件'
})

const messages = ref([])
const input = ref('')
const sending = ref(false)
const bodyRef = ref(null)
const sessionId = ref('')
const lastTooling = ref(false)
const historyVisible = ref(false)
const historyList = ref([])
const historyLoading = ref(false)

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
        const payloadText = atob(base64)
        // 注意：不能对整个 payload 做 JSON.parse —— 云盘用户 ID 是雪花算法生成的大整数，
        // 超过 JS 安全整数范围(2^53)，JSON.parse 会静默丢失精度（例如 2055271612527890400
        // 会被解析成 2055271612527890432），导致历史会话/链路查询查不到数据。
        // 因此直接从原始 JSON 文本中用正则提取字符串形式的 ID，避免精度损失。
        // 后端把用户 ID 存在 LOGIN_USER_ID claim（见 JwtUtil.generateToken），兼容 userId 两种写法
        let m = /"LOGIN_USER_ID"\s*:\s*"?(\d+)"?/.exec(payloadText)
        if (!m) m = /"userId"\s*:\s*"?(\d+)"?/.exec(payloadText)
        return m ? m[1] : null
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

function pushStep(cur, step) {
    if (!messages.value[cur].steps) {
        messages.value[cur].steps = []
    }
    messages.value[cur].steps.push(step)
}

function finishThinking(cur) {
    ;(messages.value[cur].steps || []).forEach(s => {
        if (s.running && s.kind === 'thinking') s.running = false
    })
}

function finishAllRunning(cur) {
    ;(messages.value[cur].steps || []).forEach(s => {
        if (s.running) {
            s.running = false
            s.done = true
        }
    })
}

function lastStepOfTool(cur, tool) {
    const steps = messages.value[cur].steps || []
    for (let i = steps.length - 1; i >= 0; i--) {
        if (steps[i].kind === 'tool' && steps[i].tool === tool) return steps[i]
    }
    return null
}

function toggleStep(step) {
    if (step.kind === 'tool') step.expanded = !step.expanded
}

function newSession() {
    sessionId.value = ''
    localStorage.removeItem(SESSION_KEY)
    messages.value = []
    input.value = ''
}

function formatTime(iso) {
    if (!iso) return ''
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return ''
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadHistory() {
    const userId = getUserIdFromToken()
    if (!userId) {
        ElMessage.error('登录状态失效，请重新登录')
        return
    }
    historyVisible.value = true
    historyLoading.value = true
    try {
        historyList.value = await agentSessions(userId)
    } catch (e) {
        historyList.value = []
        ElMessage.warning(e.message || '获取会话列表失败')
    } finally {
        historyLoading.value = false
    }
}

async function openHistory(s) {
    const userId = getUserIdFromToken()
    if (!userId) {
        ElMessage.error('登录状态失效，请重新登录')
        return
    }
    historyVisible.value = false
    try {
        const data = await agentHistory(s.sessionId, userId)
        const history = data.messages || []
        messages.value = history.length > 0
            ? history.map(m => ({
                role: m.role === 'user' ? 'user' : 'assistant',
                content: m.content || '',
                tooling: false,
                steps: [],
                traceId: m.trace_id || m.traceId || null,
            }))
            : [{role: 'assistant', content: '（该会话暂无消息记录）', steps: []}]
        sessionId.value = s.sessionId
        localStorage.setItem(SESSION_KEY, s.sessionId)
        input.value = ''
        scrollToBottom()
        ElMessage.success('已切换到历史会话，可继续提问')
    } catch (e) {
        ElMessage.error(e.message || '加载历史会话失败')
    }
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
    messages.value.push({role: 'assistant', content: '', tooling: false, steps: []})
    input.value = ''
    sending.value = true
    lastTooling.value = false

    const cur = messages.value.length - 1
    try {
        await agentChat(text, String(userId), sessionId.value || null, async ev => {
            if (ev.event === 'thinking') {
                // 决策等待期：仅保留一条"思考中"，避免重复
                if (!(messages.value[cur].steps || []).some(s => s.running)) {
                    pushStep(cur, {kind: 'thinking', text: ev.data.content || '正在分析…', running: true})
                }
                scrollToBottom()
            } else if (ev.event === 'llm_decision') {
                finishThinking(cur)
                const tools = ev.data.tools || []
                if (tools.length > 0) {
                    pushStep(cur, {kind: 'plan', text: `准备调用工具：${tools.map(t => toolLabel(t)).join('、')}`, tools})
                } else {
                    pushStep(cur, {kind: 'thinking', text: '正在生成回答…', running: true})
                }
                scrollToBottom()
            } else if (ev.event === 'confirmation_required') {
                pushStep(cur, {kind: 'tool', tool: ev.data.tool, name: toolLabel(ev.data.tool), text: `等待你批准「${toolLabel(ev.data.tool)}」操作…`, args: ev.data.arguments, running: true, awaiting: true})
                lastTooling.value = true
                scrollToBottom()
                const approved = await requestConfirm(userId, ev.data.requestId, ev.data.tool, ev.data.arguments)
                const waitStep = lastStepOfTool(cur, ev.data.tool)
                if (waitStep) {
                    waitStep.running = false
                    waitStep.awaiting = false
                    if (approved) {
                        waitStep.done = true
                        waitStep.text = `已批准「${toolLabel(ev.data.tool)}」，继续执行`
                    } else {
                        waitStep.rejected = true
                        waitStep.text = `已拒绝「${toolLabel(ev.data.tool)}」操作`
                    }
                }
                scrollToBottom()
            } else if (ev.event === 'tool_start') {
                pushStep(cur, {kind: 'tool', tool: ev.data.tool, name: toolLabel(ev.data.tool), text: `正在调用「${toolLabel(ev.data.tool)}」…`, args: ev.data.arguments, running: true})
                lastTooling.value = true
                scrollToBottom()
            } else if (ev.event === 'tool_end') {
                const step = lastStepOfTool(cur, ev.data.tool)
                if (step) {
                    step.running = false
                    step.done = true
                    step.error = !!ev.data.error
                    step.rejected = !!ev.data.skipped
                    step.durationMs = ev.data.duration_ms
                    step.result = ev.data.resultPreview || ''
                }
                lastTooling.value = false
                scrollToBottom()
            } else if (ev.event === 'delta') {
                finishThinking(cur)
                messages.value[cur].content += ev.data.content
                scrollToBottom()
            } else if (ev.event === 'done') {
                finishAllRunning(cur)
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
                finishAllRunning(cur)
                messages.value[cur].content = ev.data.content || '出错了'
            }
        }, {
            currentDirId: currentDirId.value,
            currentDirName: currentDirName.value
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
                        steps: [],
                        traceId: m.trace_id || m.traceId || null,
                    }))
                } else {
                    messages.value.push({role: 'assistant', content: '（已恢复上次会话，你可以继续向我提问）', steps: []})
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

/* ---- 思考链路 ---- */
.think-steps {
    margin-bottom: 8px;
    border-left: 2px solid #e4e7ed;
    padding-left: 10px;
}

.think-block {
    margin: 4px 0;
}

.think-step {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: #909399;
    padding: 4px 8px;
    border-radius: 6px;
    background: #f7f8fa;
    cursor: default;
    user-select: none;
}

.think-step.running {
    color: #09AAFF;
    background: #f0f7ff;
}

.think-step.done {
    color: #67c23a;
}

.think-step.error {
    color: #f56c6c;
    background: #fef0f0;
}

.think-step.rejected {
    color: #e6a23c;
    background: #fdf6ec;
}

.think-text {
    flex: 1;
    word-break: break-all;
}

.think-dur {
    font-size: 11px;
    color: #a8abb2;
    flex-shrink: 0;
}

.think-detail {
    margin: 2px 0 4px 20px;
}

.think-label {
    font-size: 11px;
    color: #909399;
    margin: 4px 0 2px;
}

.think-code {
    font-size: 12px;
    line-height: 1.5;
    color: #606266;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    border-radius: 6px;
    padding: 6px 8px;
    margin: 0;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 160px;
    overflow-y: auto;
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

.chat-dir-tip {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 8px 24px 0;
    font-size: 12px;
    color: #909399;
    background: #fff;
}
.chat-dir-tip .dir-label {
    color: #c0c4cc;
}
.chat-dir-tip .dir-name {
    color: #409eff;
    font-weight: 600;
    max-width: 220px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.chat-dir-tip .dir-help {
    cursor: help;
    color: #c0c4cc;
    margin-left: 2px;
}
.chat-input {
    display: flex;
    gap: 12px;
    align-items: flex-end;
    padding: 12px 24px 16px;
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

/* ---- 历史会话 ---- */
.history-list {
    max-height: 420px;
    overflow-y: auto;
}

.history-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 8px;
    cursor: pointer;
    transition: background .15s;
}

.history-item:hover {
    background: #f0f7ff;
}

.history-title {
    flex: 1;
    font-size: 13px;
    color: #333;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.history-time {
    flex-shrink: 0;
    font-size: 12px;
    color: #909399;
}

.history-empty {
    text-align: center;
    color: #909399;
    font-size: 13px;
    padding: 32px 0;
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
