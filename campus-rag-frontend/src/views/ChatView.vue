<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
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
import ChatSidebar from '@/components/ChatSidebar.vue'
import MessageList from '@/components/MessageList.vue'
import ChatInput from '@/components/ChatInput.vue'

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
const loadingMessages = ref(false)
const sidebarCollapsed = ref(false)
const sidebarWidth = ref(260)

const messageListRef = ref<InstanceType<typeof MessageList>>()
const router = useRouter()

onMounted(async () => {
  await loadConversations()
  const lastConvId = sessionStorage.getItem('lastConvId')
  if (lastConvId) {
    const conv = conversations.value.find(c => c.id === Number(lastConvId))
    if (conv) {
      sessionStorage.removeItem('lastConvId')
      await selectConversation(conv)
    }
  }
})

function scrollToBottom() {
  nextTick(() => {
    messageListRef.value?.scrollToBottom()
  })
}

async function loadConversations() {
  try {
    conversations.value = await getConversations()
  } catch { /* 静默处理 */ }
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
  try { return JSON.parse(raw) } catch { return [] }
}

async function handleNewChat() {
  currentConvId.value = null
  messages.value = []
}

async function handleDeleteConv(conv: ConversationItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${conv.title}」吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch { return }
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
    msg.streaming = false
    // 更新渲染后的 HTML
    msg.renderedHtml = renderMarkdown(msg.answer)
    sessionStorage.setItem('lastConvId', String(currentConvId.value))
    // 使用 forceUpdate 强制重新渲染，而不是销毁组件
    await nextTick()
    scrollToBottom()
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
  <div class="chat-layout">
    <ChatSidebar
      :conversations="conversations"
      :current-conv-id="currentConvId"
      :collapsed="sidebarCollapsed"
      :width="sidebarWidth"
      @update:collapsed="sidebarCollapsed = $event"
      @update:width="sidebarWidth = $event"
      @select="selectConversation"
      @delete="handleDeleteConv"
      @new-chat="handleNewChat"
    />

    <section class="chat-main">
      <MessageList ref="messageListRef" :messages="messages" :loading="loadingMessages" />
      <ChatInput v-model="inputText" :disabled="sending" @send="handleSend" @input="scrollToBottom" />
    </section>
  </div>
</template>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  gap: 0;
}

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
</style>
