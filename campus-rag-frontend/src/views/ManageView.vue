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
import type { UploadFile } from 'element-plus'

const documents = ref<DocumentItem[]>([])
const uploading = ref(false)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(5)
const total = ref(0)
const uploadProgress = ref({ current: 0, total: 0, fileName: '' })
const pendingFiles = ref<File[]>([])

// Track current upload index separately
let currentUploadIndex = ref(0)

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
    const result = await getDocumentList(currentPage.value, pageSize.value)
    documents.value = result.list
    total.value = result.total
  } catch {
    ElMessage.error('获取文档列表失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadDocuments()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadDocuments()
}

function onFileSelect(file: UploadFile) {
  if (file.raw) pendingFiles.value.push(file.raw)
}

async function startBatchUpload() {
  const files = pendingFiles.value
  if (files.length === 0) return
  pendingFiles.value = []

  uploading.value = true
  let successCount = 0
  let failCount = 0
  currentUploadIndex.value = 0

  for (const file of files) {
    currentUploadIndex.value++
    uploadProgress.value = { current: currentUploadIndex.value, total: files.length, fileName: file.name }

    if (!beforeUpload(file)) {
      failCount++
      continue
    }

    try {
      await uploadDocument(file)
      successCount++
    } catch (e: any) {
      failCount++
      ElMessage.error(`${file.name}: ${e?.message || '上传失败'}`)
    }
  }

  uploading.value = false
  uploadProgress.value = { current: 0, total: 0, fileName: '' }
  ElMessage.success(`上传完成：成功 ${successCount} 个${failCount > 0 ? `，失败 ${failCount} 个` : ''}`)
  await loadDocuments()
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
    if (documents.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
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
      <div>
        <p class="page-kicker">KNOWLEDGE ARCHIVE</p>
        <h2 class="page-title">文档管理</h2>
        <p class="page-description">维护校园知识来源，上传后将自动解析、分块并建立混合检索索引。</p>
      </div>
      <el-button :icon="Refresh" @click="loadDocuments" :loading="loading" text>
        刷新
      </el-button>
    </div>

    <el-upload
      ref="uploadRef"
      class="upload-zone"
      drag
      multiple
      :auto-upload="false"
      :show-file-list="false"
      @change="onFileSelect"
      accept=".pdf,.doc,.docx,.md,.txt,.html"
    >
      <el-icon :size="32" color="#0f766e"><UploadFilled /></el-icon>
      <div class="upload-text">
        <p class="upload-title">将资料收入知识档案</p>
        <p>拖到此处，或<em>点击选择文件</em></p>
        <p class="upload-hint">支持多选 · PDF、Word、Markdown、TXT（最大 50MB）</p>
      </div>
    </el-upload>

    <div v-if="pendingFiles.length > 0 && !uploading" class="batch-actions">
      <span>已选择 {{ pendingFiles.length }} 个文件</span>
      <el-button color="#0f766e" size="small" @click="startBatchUpload">开始上传</el-button>
      <el-button size="small" text @click="pendingFiles = []">取消</el-button>
    </div>

    <div v-if="uploading" class="uploading-bar">
      <el-progress
        :percentage="Math.round((uploadProgress.current / uploadProgress.total) * 100)"
        :stroke-width="6"
        :show-text="false"
        style="max-width:300px"
      />
      <span>正在解析（{{ uploadProgress.current }}/{{ uploadProgress.total }}）：{{ uploadProgress.fileName }}</span>
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
          <button class="doc-delete" @click="handleDelete(doc)">
            <el-icon :size="16"><Delete /></el-icon>
          </button>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      class="doc-pagination"
      background
      layout="prev, pager, next, total, sizes"
      :total="total"
      :page-size="pageSize"
      :current-page="currentPage"
      :page-sizes="[5, 10, 20, 50]"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped>
.manage-wrapper {
  max-width: 960px;
  margin: 0 auto;
  padding: 6px 0 30px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}

.page-title {
  margin: 3px 0 6px;
  font-family: 'Songti SC', 'STSong', serif;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.page-kicker {
  color: var(--green);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.page-description {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.upload-zone :deep(.el-upload-dragger) {
  background: rgba(15, 118, 110, 0.15) !important;
  border: 1px dashed rgba(15, 118, 110, 0.5) !important;
  border-radius: 12px !important;
  min-height: 184px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: background-color 0.25s, border-color 0.25s, transform 0.25s;
}

.upload-zone :deep(.el-upload-dragger:hover) {
  background: rgba(15, 118, 110, 0.25) !important;
  border-color: rgba(15, 118, 110, 0.7) !important;
  transform: translateY(-2px);
}

.upload-zone {
  border-radius: 12px;
}

.upload-text p {
  margin: 6px 0;
  font-size: 14px;
  color: #606266;
}

.upload-text .upload-title {
  margin-top: 12px;
  color: var(--text);
  font-family: 'Songti SC', 'STSong', serif;
  font-size: 17px;
  font-weight: 700;
}

.upload-hint {
  color: #909399;
  font-size: 12px;
}

.upload-text em {
  color: #0f766e;
  font-style: normal;
}

.uploading-bar {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #0f766e;
}

.batch-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  font-size: 14px;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 10px;
  padding: 8px 16px;
}

.batch-actions .el-button {
  border-radius: 6px;
  font-weight: 500;
}

.batch-actions .el-button--primary {
  box-shadow: 0 2px 4px rgba(15, 118, 110, 0.3);
}

.batch-actions .el-button--text {
  color: #6b7280;
}

.batch-actions .el-button--text:hover {
  color: #0f766e;
}

.doc-list {
  margin-top: 22px;
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
  border-radius: 8px;
  padding: 15px 16px;
  box-shadow: 0 5px 18px rgba(41, 37, 36, 0.03);
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}

.doc-card:hover {
  border-color: #b8b4ad;
  transform: translateY(-1px);
  box-shadow: 0 9px 24px rgba(41, 37, 36, 0.06);
}

.doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
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
  color: #0f766e;
  font-weight: 600;
}

.doc-dot {
  color: #d1d5db;
}

.doc-delete {
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
}
.doc-delete:hover {
  color: #ef4444;
  background: #fef2f2;
}

.doc-pagination {
  margin-top: 20px;
  justify-content: center;
  display: flex;
}

@media (max-width: 720px) {
  .manage-wrapper {
    padding-top: 2px;
  }

  .page-header {
    align-items: flex-start;
    gap: 8px;
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .page-description {
    max-width: 270px;
    font-size: 12px;
  }

  .upload-zone :deep(.el-upload-dragger) {
    min-height: 160px;
    padding: 18px 12px;
  }

  .upload-text .upload-title {
    font-size: 16px;
  }

  .upload-hint {
    line-height: 1.55;
  }

  .batch-actions,
  .uploading-bar {
    align-items: stretch;
    flex-direction: column;
    text-align: center;
  }

  .doc-card {
    gap: 10px;
    padding: 12px;
  }

  .doc-meta {
    flex-wrap: wrap;
  }

  .doc-pagination :deep(.el-pagination__total),
  .doc-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
}
</style>
