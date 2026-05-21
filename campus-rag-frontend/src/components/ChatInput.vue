<script setup lang="ts">
import { ref } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

defineProps<{
  modelValue: string
  disabled: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
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
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
  emit('input')
}

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
        @keydown="handleKeydown"
        @input="onInput"
      ></textarea>
      <button class="send-btn" :disabled="disabled || !modelValue.trim()" @click="emit('send')">
        <svg v-if="!disabled" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
        <el-icon v-else class="is-loading"><Promotion /></el-icon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-footer { padding: 12px 24px 16px; border-top: 1px solid var(--border); }

.input-row {
  display: flex; align-items: flex-end; gap: 8px;
  background: var(--white); border: 1px solid var(--border);
  border-radius: 12px; padding: 8px 8px 8px 16px;
  transition: border-color 0.2s;
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
</style>
