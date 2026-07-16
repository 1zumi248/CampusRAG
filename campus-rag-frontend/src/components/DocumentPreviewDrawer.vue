<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted, onBeforeUnmount } from 'vue'
import { getDocumentChunks, type DocumentChunk } from '@/api/chat'

const props = defineProps<{
  visible: boolean
  documentId: number | null
  documentTitle: string
  highlightChunkIndex: number | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v)
})

const chunks = ref<DocumentChunk[]>([])
const loading = ref(false)
const error = ref('')
const drawerSize = ref('min(720px, 48vw)')

function updateDrawerSize() {
  drawerSize.value = window.innerWidth <= 720 ? '100%' : 'min(720px, 48vw)'
}

onMounted(() => {
  updateDrawerSize()
  window.addEventListener('resize', updateDrawerSize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateDrawerSize)
})

watch(
  [() => props.documentId, () => props.visible],
  async ([id, vis]) => {
    if (!id || !vis) return
    loading.value = true
    error.value = ''
    chunks.value = []
    try {
      chunks.value = await getDocumentChunks(id)
      await nextTick()
      if (props.highlightChunkIndex != null) {
        const el = document.getElementById('chunk-' + props.highlightChunkIndex)
        el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el?.classList.add('flash')
        setTimeout(() => el?.classList.remove('flash'), 2000)
      }
    } catch {
      error.value = '加载文档内容失败'
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :title="documentTitle || '文档预览'"
    direction="rtl"
    :size="drawerSize"
  >
    <div v-if="loading" class="drawer-status">加载中...</div>
    <div v-else-if="error" class="drawer-status error">{{ error }}</div>
    <div v-else-if="chunks.length === 0" class="drawer-status">该文档暂无分块内容</div>
    <div v-else class="chunks-list">
      <div
        v-for="c in chunks"
        :key="c.id"
        :id="'chunk-' + c.chunkIndex"
        class="chunk-card"
        :class="{ highlight: c.chunkIndex === highlightChunkIndex }"
      >
        <div class="chunk-header">
          <span class="chunk-idx">片段 #{{ c.chunkIndex }}</span>
          <span v-if="c.chunkIndex === highlightChunkIndex" class="chunk-ref-tag">引用来源</span>
        </div>
        <div class="chunk-content">{{ c.content }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-status {
  padding: 40px 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}
.drawer-status.error { color: #ef4444; }

.chunks-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 4px;
}

.chunk-card {
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px 14px;
  background: var(--white);
  transition: background 0.3s, border-color 0.3s;
  scroll-margin-top: 20px;
}
.chunk-card.highlight {
  border-color: var(--green);
  background: var(--green-bg);
}
.chunk-card.flash {
  animation: chunk-flash 2s ease;
}
@keyframes chunk-flash {
  0%, 100% { background: var(--white); border-color: var(--border); }
  10%, 60% { background: var(--green-bg); border-color: var(--green); }
}

.chunk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.chunk-idx {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.chunk-card.highlight .chunk-idx { color: var(--green); }
.chunk-ref-tag {
  font-size: 10px;
  color: var(--green);
  background: var(--white);
  border: 1px solid var(--green);
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
}

.chunk-content {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
