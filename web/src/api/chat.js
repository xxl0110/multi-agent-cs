import request from '@/utils/request'

// 发送消息（非流式，用于历史恢复等）
export function sendMessage(data) {
  return request({
    url: '/api/chat/send-sync',
    method: 'post',
    data
  })
}

// 发送消息（SSE 流式，返回 fetch Response 对象）
export function sendMessageStream(data) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  return fetch(`${baseUrl}/api/chat/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
}

// 创建会话
export function createSession() {
  return request({
    url: '/api/chat/session',
    method: 'post'
  })
}

// 获取历史消息
export function getHistory(sessionId) {
  return request({
    url: `/api/chat/session/${sessionId}/history`,
    method: 'get'
  })
}
