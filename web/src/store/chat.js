import { defineStore } from 'pinia'
import { sendMessage, sendMessageStream, createSession, getHistory } from '@/api/chat'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', {
  state: () => ({
    sessionId: localStorage.getItem('cs_session_id') || '',
    messages: [],
    loading: false,
    initialized: false,
    pendingCitations: null,
    currentNode: ''
  }),

  getters: {
    messageList: (state) => state.messages
  },

  actions: {
    async initSession() {
      if (this.sessionId) {
        try {
          const res = await getHistory(this.sessionId)
          this.messages = res.messages || []
        } catch {
          this.sessionId = ''
        }
      }
      if (!this.sessionId) {
        await this.newSession()
      }
      this.initialized = true
    },

    async newSession() {
      try {
        const res = await createSession()
        this.sessionId = res.sessionId
        localStorage.setItem('cs_session_id', this.sessionId)
        this.messages = []
      } catch (e) {
        ElMessage.error('创建会话失败')
      }
    },

    addMessage(role, content, citations) {
      const msg = { role, content }
      if (citations) msg.citations = citations
      this.messages.push(msg)
    },

    /** 追加文本到最后一条 assistant 消息（SSE 流式更新） */
    appendToLastAssistant(text) {
      const last = this.messages[this.messages.length - 1]
      if (last && last.role === 'assistant') {
        last.content += text
      } else {
        this.messages.push({ role: 'assistant', content: text })
      }
    },

    /** 覆盖最后一条 assistant 消息内容（用于 SSE 累积更新） */
    updateLastAssistant(text) {
      const last = this.messages[this.messages.length - 1]
      if (last && last.role === 'assistant') {
        last.content = text
        if (this.pendingCitations) { last.citations = this.pendingCitations; this.pendingCitations = null }
      } else {
        const msg = { role: 'assistant', content: text }
        if (this.pendingCitations) { msg.citations = this.pendingCitations; this.pendingCitations = null }
        this.messages.push(msg)
      }
    },

    /** SSE 流式发送 */
    async sendStream(text) {
      if (!text.trim() || this.loading) return

      this.addMessage('user', text.trim())
      this.loading = true

      try {
        const resp = await sendMessageStream({
          sessionId: this.sessionId,
          message: text.trim()
        })

        if (!resp.ok) throw new Error(`请求失败: ${resp.status}`)

        const reader = resp.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let lastEvent = ''
        let accumData = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          while (buffer.includes('\n')) {
            const idx = buffer.indexOf('\n')
            const line = buffer.slice(0, idx).trimRight()
            buffer = buffer.slice(idx + 1)

            if (line.startsWith('event:')) {
              if (lastEvent === 'message' && accumData) {
                this.updateLastAssistant(accumData)
                accumData = ''
              }
              lastEvent = line.replace('event:', '').trim()
            } else if (line.startsWith('data:')) {
              const data = line.replace('data:', '').trim()
              if (lastEvent === 'sessionId') {
                this.sessionId = data
                localStorage.setItem('cs_session_id', this.sessionId)
              } else if (lastEvent === 'message') {
                accumData += data
                if (!this.messages.some(m => m.role === 'assistant')) {
                  this.addMessage('assistant', accumData)
                }
              } else if (lastEvent === 'error') {
                ElMessage.error(data)
              } else if (lastEvent === 'node') {
                this.currentNode = data
              } else if (lastEvent === 'citations') {
                try { this.pendingCitations = JSON.parse(data) } catch (e) {}
              }
            } else if (!line && lastEvent === 'message' && accumData) {
              this.updateLastAssistant(accumData)
            }
          }
        }
        if (accumData) this.updateLastAssistant(accumData)
      } catch (e) {
        if (!this.messages.some(m => m.role === 'assistant')) {
          this.addMessage('assistant', '抱歉，系统暂时无法处理您的请求，请稍后再试。')
        }
      } finally {
        this.loading = false
      }
    },

    /** 非流式发送（备用） */
    async send(text) {
      if (!text.trim() || this.loading) return

      this.addMessage('user', text.trim())
      this.loading = true

      try {
        const res = await sendMessage({
          sessionId: this.sessionId,
          message: text.trim()
        })

        if (res.sessionId && res.sessionId !== this.sessionId) {
          this.sessionId = res.sessionId
          localStorage.setItem('cs_session_id', this.sessionId)
        }

        this.addMessage('assistant', res.reply)
      } catch (e) {
        this.addMessage('system', '抱歉，系统暂时无法处理您的请求，请稍后再试。')
      } finally {
        this.loading = false
      }
    },

    clearMessages() {
      this.messages = []
    }
  }
})
