import request from './request'

export const authApi = {
  login: (username: string, password: string) => request.post('/auth/login', { username, password }),
  register: (username: string, password: string, nickname: string) => request.post('/auth/register', { username, password, nickname }),
  me: (token: string) => request.get('/auth/me', { headers: { satoken: token } })
}

export const knowledgeApi = {
  list: (params: any) => request.get('/knowledge-base/page', { params }),
  create: (data: any) => request.post('/knowledge-base', data),
  upload: (kbId: number, file: File) => { const fd = new FormData(); fd.append('file', file); return request.post(`/knowledge-base/${kbId}/docs/upload`, fd) },
  getDocs: (kbId: number, params: any) => request.get(`/knowledge-base/${kbId}/docs`, { params: { current: params.current || 1, pageSize: params.pageSize || 10 } }),
  deleteDoc: (docId: number) => request.delete(`/knowledge-base/docs/${docId}`)
}

export const dashboardApi = {
  overview: (params: any) => request.get('/dashboard/overview', { params }),
  tokenTrend: (params: any) => request.get('/dashboard/token-trend', { params }),
  messageTrend: (params: any) => request.get('/dashboard/message-trend', { params }),
  retrievalTrend: (params: any) => request.get('/dashboard/retrieval-trend', { params }),
  topKnowledgeBases: (params: any) => request.get('/dashboard/top-knowledge-bases', { params })
}

export const chatApi = {
  stream: (conversationId: string, message: string) => {
    const token = localStorage.getItem('satoken')
    return new EventSource(`/api/chat/stream/smart?message=${encodeURIComponent(message)}&conversationId=${conversationId}&satoken=${token}`)
  }
}