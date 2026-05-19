<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { UploadFilled, Refresh, Delete, Document } from '@element-plus/icons-vue'
import {
  uploadDocument,
  getDocumentList,
  deleteDocument,
  type DocumentItem,
} from '@/api/document'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'

const documents = ref<DocumentItem[]>([])
const uploading = ref(false)
const loading = ref(false)

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatTime(time: string): string {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fileTypeLabel(mime: string): string {
  const t = mime.split('/').pop() || mime
  return t.toUpperCase()
}

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await getDocumentList()
  } catch {
    ElMessage.error('获取文档列表失败')
  } finally {
    loading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const doc = await uploadDocument(options.file)
    ElMessage.success(`「${doc.title}」上传成功`)
    await loadDocuments()
  } catch (e: any) {
    const msg = e?.message || '上传失败'
    ElMessage.error(msg)
  } finally {
    uploading.value = false
  }
}

function beforeUpload(file: File) {
  const maxSize = 50 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.warning('文件大小不能超过 50MB')
    return false
  }
  return true
}

async function handleDelete(row: DocumentItem) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteDocument(row.id)
    ElMessage.success('删除成功')
    await loadDocuments()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(loadDocuments)
</script>

<template>
  <div class="manage-wrapper">
    <div class="page-header">
      <h2 class="page-title">文档管理</h2>
      <el-button :icon="Refresh" @click="loadDocuments" :loading="loading" text>
        刷新
      </el-button>
    </div>

    <el-upload
      class="upload-zone"
      drag
      :auto-upload="true"
      :show-file-list="false"
      :http-request="handleUpload"
      :before-upload="beforeUpload"
      accept=".pdf,.doc,.docx,.md,.txt,.html"
    >
      <el-icon :size="32" color="#10b981"><UploadFilled /></el-icon>
      <div class="upload-text">
        <p>将文件拖到此处，或<em>点击上传</em></p>
        <p class="upload-hint">PDF、Word、Markdown、TXT（最大 50MB）</p>
      </div>
    </el-upload>

    <div v-if="uploading" class="uploading-bar">
      <el-icon class="is-loading"><Refresh /></el-icon>
      <span>正在解析文档并生成向量...</span>
    </div>

    <div class="doc-list">
      <div v-if="documents.length === 0 && !loading" class="list-empty">
        <el-icon :size="40" color="#d1d5db"><Document /></el-icon>
        <p>暂无文档，请上传</p>
      </div>
      <div v-else class="doc-cards">
        <div v-for="doc in documents" :key="doc.id" class="doc-card">
          <div class="doc-icon">
            <el-icon :size="22"><Document /></el-icon>
          </div>
          <div class="doc-info">
            <div class="doc-title">{{ doc.title }}</div>
            <div class="doc-meta">
              <span class="doc-type">{{ fileTypeLabel(doc.fileType) }}</span>
              <span class="doc-dot">·</span>
              <span>{{ formatFileSize(doc.fileSize) }}</span>
              <span class="doc-dot">·</span>
              <span>{{ formatTime(doc.createdAt) }}</span>
            </div>
          </div>
          <el-popconfirm title="确定删除？" @confirm="handleDelete(doc)">
            <template #reference>
              <el-button type="danger" :icon="Delete" circle size="small" text />
            </template>
          </el-popconfirm>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.manage-wrapper {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
}

.upload-zone {
  border-radius: 12px;
}

.upload-text p {
  margin: 6px 0;
  font-size: 14px;
  color: #606266;
}

.upload-hint {
  color: #909399;
  font-size: 12px;
}

.upload-text em {
  color: #10b981;
  font-style: normal;
}

.uploading-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #10b981;
}

.doc-list {
  margin-top: 20px;
}

.list-empty {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.list-empty p {
  margin-top: 12px;
  font-size: 14px;
}

.doc-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 16px;
  transition: border-color 0.2s;
}

.doc-card:hover {
  border-color: #d1d5db;
}

.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #10b981;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.doc-meta {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
}

.doc-type {
  color: #10b981;
  font-weight: 600;
}

.doc-dot {
  color: #d1d5db;
}
</style>
