<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Promotion, Delete, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import {
  getConversations,
  getMessages,
  deleteConversation,
  type ChatResponse,
  type ConversationItem,
  type MessageItem,
} from '@/api/chat'

interface Message {
  id: number
  question: string
  answer: string
  sources: ChatResponse['sources']
  streaming: boolean
  error: boolean
  renderedHtml: string
}

const conversations = ref<ConversationItem[]>([])
const currentConvId = ref<number | null>(null)
const messages = ref<Message[]>([])
const inputText = ref('')
const sending = ref(false)
const chatBody = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()
const loadingMessages = ref(false)
const sidebarCollapsed = ref(false)
const sidebarWidth = ref(260)
const isDragging = ref(false)
const sidebarRef = ref<HTMLElement>()

onMounted(() => loadConversations())

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function startDrag(e: MouseEvent) {
  isDragging.value = true
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
  e.preventDefault()
}

function onDrag(e: MouseEvent) {
  if (!isDragging.value) return
  const rect = sidebarRef.value?.getBoundingClientRect()
  if (!rect) return
  const newWidth = e.clientX - rect.left
  sidebarWidth.value = Math.min(500, Math.max(200, newWidth))
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBody.value) {
      chatBody.value.scrollTop = chatBody.value.scrollHeight
    }
  })
}

async function loadConversations() {
  try {
    conversations.value = await getConversations()
  } catch {
    // 静默处理
  }
}

async function selectConversation(conv: ConversationItem) {
  currentConvId.value = conv.id
  messages.value = []
  loadingMessages.value = true
  try {
    const history = await getMessages(conv.id)
    messages.value = history.map((m: MessageItem) => {
      const answer = m.answer ?? ''
      return {
        id: m.id,
        question: m.question,
        answer,
        sources: parseSources(m.sources),
        streaming: false,
        error: false,
        renderedHtml: renderMarkdown(answer),
      }
    })
  } catch {
    ElMessage.error('加载历史消息失败')
  } finally {
    loadingMessages.value = false
    scrollToBottom()
  }
}

function parseSources(raw: string): ChatResponse['sources'] {
  if (!raw) return []
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

async function handleNewChat() {
  currentConvId.value = null
  messages.value = []
  nextTick(() => inputRef.value?.focus())
}

async function handleDeleteConv(conv: ConversationItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${conv.title}」吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteConversation(conv.id)
    ElMessage.success('已删除')
    if (currentConvId.value === conv.id) {
      currentConvId.value = null
      messages.value = []
    }
    await loadConversations()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  sending.value = true
  inputText.value = ''

  messages.value.push({
    id: Date.now(),
    question: text,
    answer: '',
    sources: [],
    streaming: true,
    error: false,
    renderedHtml: '',
  })
  // 关键：从响应式数组里取出来的才是 Vue 的 Proxy 代理；
  // 直接改 push 前的局部对象不会触发响应式更新（Vue3 Proxy 机制）
  const msg = messages.value[messages.value.length - 1]!
  scrollToBottom()

  const t0 = performance.now()
  let chunkIdx = 0

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      body: JSON.stringify({ question: text, conversationId: currentConvId.value }),
    })

    if (!response.ok) throw new Error('Request failed')

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventType = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      chunkIdx++
      console.log(`[SSE] chunk#${chunkIdx} +${(performance.now() - t0).toFixed(0)}ms, ${value?.byteLength ?? 0}B`)

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('event:')) {
          eventType = trimmed.slice(6).trim()
        } else if (trimmed.startsWith('data:')) {
          const data = trimmed.slice(5).trim()
          if (eventType === 'conversation') {
            if (currentConvId.value === null) {
              currentConvId.value = Number(data)
              loadConversations()
            }
          } else if (eventType === 'token') {
            msg.answer += data
            scrollToBottom()
          } else if (eventType === 'sources') {
            try { msg.sources = JSON.parse(data) } catch { /* ignore */ }
          }
          eventType = ''
        }
      }
    }
    console.log('[Stream] done, answer length:', msg.answer.length)
    // 先切换 streaming 状态，让 Vue 完成 DOM 切换（pre → div）
    msg.streaming = false
    // 等待 Vue DOM 更新完成后再设置 innerHTML，避免批处理导致 v-html 不生效
    await nextTick()
    msg.renderedHtml = renderMarkdown(msg.answer)
    console.log('[Stream] renderedHtml set, length:', msg.renderedHtml.length)
  } catch {
    if (!msg.answer) {
      msg.error = true
      msg.answer = '请求失败，请检查后端服务是否启动'
    }
    msg.renderedHtml = msg.answer
    msg.streaming = false
  } finally {
    sending.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    const result = marked.parse(text, { async: false })
    console.log('[Markdown] rendered', text.length, 'chars →', result.length, 'chars, preview:', result.slice(0, 80))
    return result
  } catch (e) {
    console.error('[Markdown] render failed:', e)
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}
</script>

<template>
  <div class="chat-layout" :class="{ 'is-resizing': isDragging }">
    <!-- 左侧会话列表 -->
    <aside ref="sidebarRef" class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }" :style="{ width: sidebarCollapsed ? '44px' : sidebarWidth + 'px' }">
      <div class="sidebar-header">
        <button v-if="!sidebarCollapsed" class="new-chat-btn" @click="handleNewChat">
          <el-icon :size="16"><Plus /></el-icon>
          <span>新建会话</span>
        </button>
      </div>
      <div v-show="!sidebarCollapsed" class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConvId }"
          @click="selectConversation(conv)"
        >
          <span class="conv-title">{{ conv.title }}</span>
          <button class="conv-delete" @click.stop="handleDeleteConv(conv)">
            <el-icon :size="14"><Delete /></el-icon>
          </button>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">
          暂无会话，开始提问吧
        </div>
      </div>
      <!-- 折叠按钮：位于侧边栏右边缘中间，始终跟随 sidebarWidth 位置 -->
      <button class="collapse-btn" @click="toggleSidebar" :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
              :style="{ left: (sidebarCollapsed ? 44 : sidebarWidth) + 'px' }">
        <el-icon :size="14">
          <DArrowLeft v-if="!sidebarCollapsed" />
          <DArrowRight v-else />
        </el-icon>
      </button>
      <!-- 拖拽手柄 -->
      <div v-if="!sidebarCollapsed" class="resize-handle" @mousedown="startDrag"></div>
    </aside>

    <!-- 右侧问答区 -->
    <section class="chat-main">
      <div class="chat-body" ref="chatBody">
        <div v-if="messages.length === 0 && !loadingMessages" class="chat-empty">
          <div class="empty-icon">
            <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              <line x1="9" y1="10" x2="15" y2="10"/>
              <line x1="12" y1="7" x2="12" y2="13"/>
            </svg>
          </div>
          <p class="empty-title">校园知识问答</p>
          <p class="empty-hint">输入问题，查询校园规章制度、教务流程等信息</p>
        </div>

        <div v-if="loadingMessages" class="loading-history">加载历史消息中...</div>

        <div v-for="msg in messages" :key="msg.id" class="chat-item">
          <div class="chat-question">
            <div class="msg-avatar q-avatar">Q</div>
            <div class="msg-bubble q-bubble">{{ msg.question }}</div>
          </div>
          <div class="chat-answer">
            <div class="msg-avatar a-avatar">A</div>
            <div class="msg-content">
              <div class="answer-text" :class="{ 'error-text': msg.error }">
                <pre v-if="msg.streaming || msg.error">{{ msg.answer }}<span v-if="msg.streaming" class="stream-cursor">|</span></pre>
                <div v-else class="markdown-body" v-html="msg.renderedHtml"></div>
              </div>
              <div v-if="!msg.streaming && !msg.error && msg.sources.length > 0" class="sources-box">
                <div class="sources-title">引用来源</div>
                <div v-for="(src, i) in msg.sources" :key="i" class="source-item">
                  <span class="source-idx">[{{ i + 1 }}]</span>
                  <span class="source-name">{{ src.documentTitle }}</span>
                  <span class="source-score">{{ (src.similarityScore * 100).toFixed(0) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-footer">
        <div class="input-row">
          <textarea
            ref="inputRef"
            v-model="inputText"
            class="chat-input"
            placeholder="输入你的问题，按 Enter 发送..."
            rows="1"
            :disabled="sending"
            @keydown="handleKeydown"
            @input="scrollToBottom"
          ></textarea>
          <button
            class="send-btn"
            :disabled="sending || !inputText.trim()"
            @click="handleSend"
          >
            <svg v-if="!sending" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
            <el-icon v-else class="is-loading"><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  gap: 0;
}

.chat-layout.is-resizing {
  cursor: col-resize;
  user-select: none;
}

/* ---------- 左侧边栏 ---------- */
.chat-sidebar {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--white);
  border-radius: 10px 0 0 10px;
  overflow: visible;
  position: relative;
  transition: width 0.25s ease;
  user-select: none;
}

.chat-sidebar.collapsed {
  border-radius: 10px 0 0 10px;
  min-width: 44px;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid var(--border);
  min-height: 60px;
  border-radius: 10px 0 0 0;
  overflow: hidden;
}

.collapsed .sidebar-header {
  padding: 8px 4px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 0;
  border: 1px dashed var(--border);
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.new-chat-btn:hover {
  border-color: var(--green);
  color: var(--green);
  background: var(--green-bg);
}

.collapse-btn {
  position: absolute;
  left: 260px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 0 12px 12px 0;
  background: var(--white);
  color: var(--text-secondary);
  cursor: pointer;
  z-index: 20;
  transition: left 0.25s ease, background 0.2s, color 0.2s, box-shadow 0.2s;
  padding: 0;
  box-shadow: 2px 0 3px -1px rgba(0, 0, 0, 0.07), 0 1px 2px rgba(0, 0, 0, 0.04);
}

.collapse-btn:hover {
  background: var(--green-bg);
  color: var(--green);
}

.collapsed .collapse-btn {
  border-radius: 0 12px 12px 0;
  box-shadow: 2px 0 4px -1px rgba(0, 0, 0, 0.09), 0 1px 3px rgba(0, 0, 0, 0.07);
}

/* 拖拽调整宽度手柄 */
.resize-handle {
  position: absolute;
  top: 0;
  right: 0;
  width: 5px;
  height: 100%;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s;
  z-index: 10;
}

.resize-handle:hover,
.resize-handle:active {
  background: var(--green);
  opacity: 0.3;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s;
}

.conv-item:hover {
  background: var(--green-bg);
}

.conv-item.active {
  background: var(--green-bg);
}

.conv-title {
  flex: 1;
  font-size: 13px;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-delete {
  display: none;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 2px;
  flex-shrink: 0;
}

.conv-item:hover .conv-delete {
  display: block;
}

.conv-delete:hover {
  color: #ef4444;
}

.conv-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}

/* ---------- 右侧问答区 ---------- */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  border-radius: 0 10px 10px 0;
  border-left: 1px solid var(--border);
  margin-left: -1px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 24px 24px;
}

.chat-empty {
  text-align: center;
  padding-top: 80px;
}

.loading-history {
  text-align: center;
  padding: 24px;
  font-size: 14px;
  color: var(--text-secondary);
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  color: var(--text-secondary);
}

.chat-item {
  margin-bottom: 28px;
}

.chat-question,
.chat-answer {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.q-avatar {
  background: var(--green-bg);
  color: var(--green);
}

.a-avatar {
  background: #f0fdf4;
  color: var(--green);
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
}

.q-bubble {
  background: #f3f4f6;
  color: var(--text);
}

.msg-content {
  flex: 1;
  min-width: 0;
}

.answer-text pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;
  margin: 0;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 16px 0 8px;
  font-weight: 600;
  color: var(--text);
}

.markdown-body :deep(h2) {
  font-size: 17px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border);
}

.markdown-body :deep(h3) {
  font-size: 15px;
}

.markdown-body :deep(p) {
  margin: 6px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 14px;
  border-left: 3px solid var(--green);
  background: var(--green-bg);
  color: var(--text-secondary);
}

.markdown-body :deep(code) {
  padding: 1px 5px;
  font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #f3f4f6;
  border-radius: 3px;
}

.markdown-body :deep(pre) {
  margin: 10px 0;
  padding: 12px 16px;
  font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #1e293b;
  color: #e2e8f0;
  border-radius: 6px;
  overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0;
  font-size: inherit;
  background: transparent;
  border-radius: 0;
  color: inherit;
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 10px 0;
  border-collapse: collapse;
  font-size: 13px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 8px 12px;
  border: 1px solid var(--border);
  text-align: left;
}

.markdown-body :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}

.markdown-body :deep(tr:nth-child(even)) {
  background: #f9fafb;
}

.markdown-body :deep(a) {
  color: var(--green);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(hr) {
  margin: 14px 0;
  border: none;
  border-top: 1px solid var(--border);
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: var(--text);
}

.error-text pre {
  color: #ef4444;
}

.sources-box {
  margin-top: 12px;
  padding: 12px 14px;
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.sources-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text);
  margin-bottom: 3px;
}

.source-idx {
  color: var(--green);
  font-weight: 600;
  font-size: 12px;
}

.source-name {
  flex: 1;
}

.source-score {
  color: var(--text-secondary);
  font-size: 11px;
  background: #f3f4f6;
  padding: 1px 6px;
  border-radius: 4px;
}

.chat-footer {
  padding: 12px 24px 16px;
  border-top: 1px solid var(--border);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--white);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 8px 8px 8px 16px;
  transition: border-color 0.2s;
}

.input-row:focus-within {
  border-color: var(--green);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.chat-input {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 1.5;
  font-family: inherit;
  color: var(--text);
  background: transparent;
  min-height: 24px;
  max-height: 120px;
}

.chat-input::placeholder {
  color: #d1d5db;
}

.chat-input:disabled {
  opacity: 0.5;
}

.send-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: var(--green);
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;
}

.send-btn:hover:not(:disabled) {
  background: var(--green-hover);
}

.send-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.stream-cursor {
  color: var(--green);
  font-weight: 400;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
