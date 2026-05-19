<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { Plus, Promotion, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
}

const conversations = ref<ConversationItem[]>([])
const currentConvId = ref<number | null>(null)
const messages = ref<Message[]>([])
const inputText = ref('')
const sending = ref(false)
const chatBody = ref<HTMLElement>()
const inputRef = ref<HTMLTextAreaElement>()
const loadingMessages = ref(false)

onMounted(() => loadConversations())

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
    messages.value = history.map((m: MessageItem) => ({
      id: m.id,
      question: m.question,
      answer: m.answer ?? '',
      sources: parseSources(m.sources),
      streaming: false,
      error: false,
    }))
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
    msg.streaming = false
  } catch {
    if (!msg.answer) {
      msg.error = true
      msg.answer = '请求失败，请检查后端服务是否启动'
    }
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
</script>

<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <button class="new-chat-btn" @click="handleNewChat">
          <el-icon :size="16"><Plus /></el-icon>
          <span>新建会话</span>
        </button>
      </div>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === currentConvId }"
          @click="selectConversation(conv)"
        >
          <span class="conv-title">{{ conv.title }}</span>
          <el-popconfirm title="确定删除？" @confirm="handleDeleteConv(conv)">
            <template #reference>
              <button class="conv-delete" @click.stop><el-icon :size="14"><Delete /></el-icon></button>
            </template>
          </el-popconfirm>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">
          暂无会话，开始提问吧
        </div>
      </div>
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
                <pre>{{ msg.answer }}<span v-if="msg.streaming" class="stream-cursor">|</span></pre>
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

/* ---------- 左侧边栏 ---------- */
.chat-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border);
  background: var(--white);
  border-radius: 10px 0 0 10px;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
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
}

.new-chat-btn:hover {
  border-color: var(--green);
  color: var(--green);
  background: var(--green-bg);
}

.conv-list {
  flex: 1;
  overflow-y: auto;
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
