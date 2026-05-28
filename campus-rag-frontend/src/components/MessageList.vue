<script setup lang="ts">
import type { ChatResponse } from '@/api/chat'
import { marked } from 'marked'

interface Message {
  id: number
  question: string
  answer: string
  sources: ChatResponse['sources']
  streaming: boolean
  error: boolean
  renderedHtml: string
  toolStatus: string
}

import { ref, nextTick } from 'vue'

const props = defineProps<{
  messages: Message[]
  loading: boolean
}>()

const bodyRef = ref<HTMLElement>()
const forceUpdateKey = ref(0)

function scrollToBottom() {
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight
  }
}

function refresh() {
  forceUpdateKey.value++
  nextTick(() => scrollToBottom())
}

function getHtml(msg: Message): string {
  if (!msg.answer) return ''
  // 优先使用预渲染的 HTML，如果没有则实时渲染
  if (msg.renderedHtml) return msg.renderedHtml
  try {
    return marked.parse(msg.answer, {
      async: false,
      gfm: true,
      breaks: true,
    }) as string
  } catch {
    return msg.answer.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

defineExpose({ scrollToBottom, refresh })
</script>

<template>
  <div ref="bodyRef" class="chat-body">
    <div v-if="messages.length === 0 && !loading" class="chat-empty">
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

    <div v-if="loading" class="loading-history">加载历史消息中...</div>

    <div v-for="msg in messages" :key="msg.id" class="chat-item">
      <div class="chat-question">
        <div class="msg-avatar q-avatar">Q</div>
        <div class="msg-bubble q-bubble">{{ msg.question }}</div>
      </div>
      <div class="chat-answer">
        <div class="msg-avatar a-avatar">A</div>
        <div class="msg-content">
          <div class="answer-text" :class="{ 'error-text': msg.error }">
            <div v-if="msg.error" class="error-content"><pre>{{ msg.answer }}</pre></div>
            <div v-else class="markdown-body" v-html="getHtml(msg)"></div>
            <div v-if="msg.toolStatus && msg.streaming" class="tool-indicator">
              <span class="tool-spinner"></span>
              <span>正在{{ msg.toolStatus }}...</span>
            </div>
            <span v-if="msg.streaming && !msg.toolStatus" class="stream-cursor">|</span>
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
</template>

<style scoped>
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 24px 24px;
}

.chat-empty { text-align: center; padding-top: 80px; }
.loading-history { text-align: center; padding: 24px; font-size: 14px; color: var(--text-secondary); }
.empty-icon { margin-bottom: 16px; }

.empty-title { font-size: 18px; font-weight: 600; color: var(--text); margin-bottom: 8px; }
.empty-hint { font-size: 14px; color: var(--text-secondary); }

.chat-item { margin-bottom: 28px; }

.chat-question, .chat-answer { display: flex; gap: 12px; margin-bottom: 8px; }

.msg-avatar {
  width: 32px; height: 32px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; flex-shrink: 0;
}

.q-avatar { background: var(--green-bg); color: var(--green); }
.a-avatar { background: #f0fdf4; color: var(--green); }

.msg-bubble {
  padding: 10px 14px; border-radius: 10px;
  font-size: 14px; line-height: 1.6;
}

.q-bubble { background: #f3f4f6; color: var(--text); }

.msg-content { flex: 1; min-width: 0; }

.answer-text pre {
  white-space: pre-wrap; word-break: break-word;
  font-family: inherit; font-size: 14px; line-height: 1.7; margin: 0;
}

.error-content pre { color: #ef4444; white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: 14px; line-height: 1.7; margin: 0; }

.markdown-body { font-size: 14px; line-height: 1.7; color: var(--text); }

.markdown-body :deep(h1), .markdown-body :deep(h2),
.markdown-body :deep(h3), .markdown-body :deep(h4) {
  margin: 16px 0 8px; font-weight: 600; color: var(--text);
}

.markdown-body :deep(h2) { font-size: 17px; padding-bottom: 6px; border-bottom: 1px solid var(--border); }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 6px 0; }
.markdown-body :deep(li) { margin-bottom: 4px; }

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 14px;
  border-left: 3px solid var(--green);
  background: var(--green-bg);
  color: var(--text-secondary);
}

/* 移除嵌套 blockquote 的左边框和背景，避免重复显示 */
.markdown-body :deep(blockquote blockquote),
.markdown-body :deep(blockquote > blockquote) {
  border-left: none !important;
  background: transparent !important;
  margin: 0 !important;
  padding: 0 !important;
}

.markdown-body :deep(code) {
  padding: 1px 5px; font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #f3f4f6; border-radius: 3px;
}

.markdown-body :deep(pre) {
  margin: 10px 0; padding: 12px 16px; font-size: 13px;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  background: #1e293b; color: #e2e8f0; border-radius: 6px; overflow-x: auto;
}

.markdown-body :deep(pre code) {
  padding: 0; font-size: inherit; background: transparent; border-radius: 0; color: inherit;
}

.markdown-body :deep(table) { width: 100%; margin: 10px 0; border-collapse: collapse; font-size: 13px; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 8px 12px; border: 1px solid var(--border); text-align: left; }
.markdown-body :deep(th) { background: #f9fafb; font-weight: 600; }
.markdown-body :deep(tr:nth-child(even)) { background: #f9fafb; }
.markdown-body :deep(a) { color: var(--green); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(hr) { margin: 14px 0; border: none; border-top: 1px solid var(--border); }
.markdown-body :deep(strong) { font-weight: 600; color: var(--text); }

.sources-box {
  margin-top: 12px; padding: 12px 14px;
  background: var(--white); border: 1px solid var(--border); border-radius: 8px;
}

.sources-title {
  font-size: 12px; font-weight: 600; color: var(--text-secondary);
  margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px;
}

.source-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text); margin-bottom: 3px; }
.source-idx { color: var(--green); font-weight: 600; font-size: 12px; }
.source-name { flex: 1; }
.source-score { color: var(--text-secondary); font-size: 11px; background: #f3f4f6; padding: 1px 6px; border-radius: 4px; }

.stream-cursor { color: var(--green); font-weight: 400; animation: blink 0.8s infinite; }

.tool-indicator {
  display: inline-flex; align-items: center; gap: 6px;
  margin-top: 8px; padding: 6px 12px;
  background: #f0fdf4; border: 1px solid var(--green); border-radius: 6px;
  font-size: 13px; color: var(--green);
}

.tool-spinner {
  width: 14px; height: 14px;
  border: 2px solid #d1fae5;
  border-top-color: var(--green);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  display: inline-block;
}

@keyframes spin { to { transform: rotate(360deg); } }

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>