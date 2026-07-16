import api from './index'

export interface ChatResponse {
  conversationId: number
  answer: string
  sources: SourceReference[]
}

export interface SourceReference {
  documentId: number
  documentTitle: string
  chunkContent: string
  chunkIndex: number
  similarityScore: number
  // 调试字段(仅流式 SSE 发送,历史消息无)
  vectorScore?: number
  esScore?: number
  vectorRank?: number
  esRank?: number
}

export interface ConversationItem {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface MessageItem {
  id: number
  conversationId: number
  question: string
  answer: string
  sources: string  // JSON string of SourceReference[]
  createdAt: string
}

export function sendMessage(question: string, conversationId?: number): Promise<ChatResponse> {
  return api.post('/chat', { question, conversationId }).then((res) => res.data)
}

export function getConversations(): Promise<ConversationItem[]> {
  return api.get('/conversations').then((res) => res.data)
}

export function getMessages(conversationId: number): Promise<MessageItem[]> {
  return api.get(`/conversations/${conversationId}/messages`).then((res) => res.data)
}

export interface DocumentChunk {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  metadata: string
  createdAt: string
}

export function deleteConversation(id: number): Promise<void> {
  return api.delete(`/conversations/${id}`)
}

export function getDocumentChunks(documentId: number): Promise<DocumentChunk[]> {
  return api.get(`/documents/${documentId}/chunks`).then((res) => res.data)
}
