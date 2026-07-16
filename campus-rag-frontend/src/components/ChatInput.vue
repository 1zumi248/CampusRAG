<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  disabled: boolean
  streaming: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  stop: []
  input: []
}>()

const inputRef = ref<HTMLTextAreaElement>()

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

function onInput(e: Event) {
  const textarea = e.target as HTMLTextAreaElement
  emit('update:modelValue', textarea.value)
  resizeTextarea(textarea)
  emit('input')
}

function resizeTextarea(textarea = inputRef.value) {
  if (!textarea) return
  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, 120)}px`
}

watch(() => props.modelValue, () => {
  nextTick(() => resizeTextarea())
})

function focus() {
  inputRef.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class="chat-footer">
    <div class="input-row">
      <textarea
        ref="inputRef"
        :value="modelValue"
        class="chat-input"
        placeholder="输入你的问题，按 Enter 发送..."
        rows="1"
        :disabled="disabled"
        aria-label="输入校园知识问题"
        @keydown="handleKeydown"
        @input="onInput"
      ></textarea>
      <button
        class="send-btn"
        :class="{ 'stop-mode': streaming }"
        :disabled="!streaming && !modelValue.trim()"
        :aria-label="streaming ? '停止生成' : '发送问题'"
        :title="streaming ? '停止生成' : '发送问题'"
        @click="streaming ? emit('stop') : emit('send')"
      >
        <svg v-if="!streaming" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <rect x="6" y="6" width="12" height="12" rx="2"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-footer {
  padding: 14px 24px 18px;
  border-top: 1px solid var(--border);
  background: rgba(250, 250, 249, 0.92);
}

.input-row {
  display: flex; align-items: flex-end; gap: 8px;
  background: var(--white); border: 1px solid var(--border);
  border-radius: 12px; padding: 8px 8px 8px 16px;
  width: min(900px, 100%);
  margin: 0 auto;
  box-shadow: 0 8px 24px rgba(41, 37, 36, 0.05);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-row:focus-within { border-color: var(--green); box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1); }

.chat-input {
  flex: 1; border: none; outline: none; resize: none;
  font-size: 14px; line-height: 1.5; font-family: inherit;
  color: var(--text); background: transparent;
  min-height: 24px; max-height: 120px;
}

.chat-input::placeholder { color: #d1d5db; }
.chat-input:disabled { opacity: 0.5; }

.send-btn {
  width: 36px; height: 36px; border: none; border-radius: 8px;
  background: var(--green); color: white; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: all 0.2s;
}

.send-btn:hover:not(:disabled) { background: var(--green-hover); }
.send-btn:disabled { background: #d1d5db; cursor: not-allowed; }
.send-btn.stop-mode { background: #dc2626; }
.send-btn.stop-mode:hover { background: #b91c1c; }
.send-btn:active:not(:disabled) { transform: scale(0.95); }

.send-btn:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: 2px;
}

@media (max-width: 720px) {
  .chat-footer {
    padding: 10px 12px 12px;
  }

  .input-row {
    padding-left: 12px;
    border-radius: 10px;
  }
}
</style>
