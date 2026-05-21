<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Delete, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import type { ConversationItem } from '@/api/chat'

defineProps<{
  conversations: ConversationItem[]
  currentConvId: number | null
  collapsed: boolean
  width: number
}>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'update:width': [value: number]
  select: [conv: ConversationItem]
  delete: [conv: ConversationItem]
  'new-chat': []
}>()

const sidebarRef = ref<HTMLElement>()
const isDragging = ref(false)

function toggleSidebar() {
  emit('update:collapsed', !sidebarRef.value?.classList.contains('collapsed'))
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
  emit('update:width', Math.min(500, Math.max(200, newWidth)))
}

function stopDrag() {
  isDragging.value = false
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
}
</script>

<template>
  <aside ref="sidebarRef" class="chat-sidebar" :class="{ collapsed }" :style="{ width: collapsed ? '44px' : width + 'px' }">
    <div class="sidebar-header">
      <button v-if="!collapsed" class="new-chat-btn" @click="emit('new-chat')">
        <el-icon :size="16"><Plus /></el-icon>
        <span>新建会话</span>
      </button>
    </div>
    <div v-show="!collapsed" class="conv-list">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: conv.id === currentConvId }"
        @click="emit('select', conv)"
      >
        <span class="conv-title">{{ conv.title }}</span>
        <button class="conv-delete" @click.stop="emit('delete', conv)">
          <el-icon :size="14"><Delete /></el-icon>
        </button>
      </div>
      <div v-if="conversations.length === 0" class="conv-empty">暂无会话，开始提问吧</div>
    </div>
    <button class="collapse-btn" @click="toggleSidebar" :title="collapsed ? '展开侧栏' : '收起侧栏'"
            :style="{ left: (collapsed ? 44 : width) + 'px' }">
      <el-icon :size="14">
        <DArrowLeft v-if="!collapsed" />
        <DArrowRight v-else />
      </el-icon>
    </button>
    <div v-if="!collapsed" class="resize-handle" @mousedown="startDrag"></div>
  </aside>
</template>

<style scoped>
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

.conv-item:hover { background: var(--green-bg); }
.conv-item.active { background: var(--green-bg); }

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

.conv-item:hover .conv-delete { display: block; }
.conv-delete:hover { color: #ef4444; }

.conv-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
