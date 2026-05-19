import api from './index'

export interface DocumentItem {
  id: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  contentHash: string
  createdAt: string
  updatedAt: string
}

export function uploadDocument(file: File): Promise<DocumentItem> {
  const formData = new FormData()
  formData.append('file', file)
  return api
    .post('/documents/upload', formData, { timeout: 300_000 })
    .then((res) => res.data)
}

export function getDocumentList(): Promise<DocumentItem[]> {
  return api.get('/documents').then((res) => res.data)
}

export function getDocumentById(id: number): Promise<DocumentItem> {
  return api.get(`/documents/${id}`).then((res) => res.data)
}

export function deleteDocument(id: number): Promise<void> {
  return api.delete(`/documents/${id}`)
}
