<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <el-icon size="28"><MagicStick /></el-icon>
          <span>智能客服</span>
        </div>
      </div>
      <div class="sidebar-body">
        <el-button type="primary" class="new-chat-btn" @click="handleNewSession">
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
        <div class="session-list">
          <div
            class="session-item"
            :class="{ active: true }"
          >
            <el-icon><ChatDotSquare /></el-icon>
            <span class="session-title">当前对话</span>
            <el-tag size="small" type="info" effect="plain">{{ messages.length }}条</el-tag>
          </div>
        </div>
      </div>
      <div class="sidebar-footer">
        <div class="agent-status">
          <span class="status-dot"></span>
          <span>多Agent协同</span>
        </div>
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="chat-main">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-info">
          <h2>🤖 智能客服助手</h2>
          <div class="agent-tags">
            <el-tag size="small" round>📦 订单</el-tag>
            <el-tag size="small" round type="success">🛍️ 商品</el-tag>
            <el-tag size="small" round type="warning">🔄 退换货</el-tag>
            <el-tag size="small" round type="danger">📢 投诉</el-tag>
          </div>
        </div>
        <div class="header-actions">
          <el-tooltip content="清空对话" placement="bottom">
            <el-button circle :icon="Delete" @click="handleClear" />
          </el-tooltip>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-area" ref="messageArea" @scroll="onScroll">
        <div v-if="!initialized" class="loading-wrapper">
          <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="messages.length === 0" class="welcome">
          <div class="welcome-icon">👋</div>
          <h3>您好！我是智能客服助手</h3>
          <p class="welcome-desc">我有多个专业助手协同为您服务，请问有什么可以帮您？</p>
          <div class="quick-actions">
            <el-button @click="sendQuick('查一下我的订单')" :icon="Van">
              📦 查订单
            </el-button>
            <el-button @click="sendQuick('推荐一款跑步用的蓝牙耳机')" :icon="Goods" type="success">
              🎧 推荐商品
            </el-button>
            <el-button @click="sendQuick('想退货怎么操作')" :icon="Refresh" type="warning">
              🔄 退货咨询
            </el-button>
            <el-button @click="sendQuick('物流太慢了，我要投诉')" :icon="WarningFilled" type="danger">
              📢 投诉
            </el-button>
          </div>
        </div>

        <div v-else class="message-list">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
          >
            <el-avatar v-if="msg.role === 'assistant'" :size="36" class="msg-avatar">
              🤖
            </el-avatar>
            <el-avatar v-if="msg.role === 'user'" :size="36" class="msg-avatar">
              👤
            </el-avatar>

            <div class="message-bubble" :class="msg.role">
              <div class="message-label" v-if="msg.role === 'assistant'">云小护</div>
              <div class="message-content" v-html="renderContent(msg.content)"></div>
              <div v-if="msg.citations && msg.citations.length" class="citation-tags">
                <el-popover
                  v-for="(cite, ci) in msg.citations"
                  :key="ci"
                  placement="top"
                  :width="300"
                  trigger="hover"
                  :content="cite.snippet || ''"
                >
                  <template #reference>
                    <el-tag size="small" :type="getCitationType(cite)" class="citation-tag">
                      [{{ ci + 1 }}] {{ cite.metadata?.fileName || cite.metadata?.sourceFile || '来源' }}
                      <span class="citation-score">{{ (cite.score * 100).toFixed(0) }}%</span>
                    </el-tag>
                  </template>
                </el-popover>
              </div>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="message-item assistant">
            <el-avatar :size="36" class="msg-avatar">🤖</el-avatar>
            <div class="message-bubble assistant">
              <div class="message-label">云小护</div>
              <div class="graph-progress" v-if="currentNode">
                <span class="graph-node" :class="currentNode">
                  <el-icon :size="14" class="graph-icon"><Loading /></el-icon>
                  {{ currentNode === 'supervisor' ? '🤖 调度中' :
                     currentNode === 'order_agent' ? '📦 查订单' :
                     currentNode === 'product_agent' ? '🛍️ 查商品' :
                     currentNode === 'return_agent' ? '🔄 退换货' :
                     currentNode === 'complaint_agent' ? '📢 投诉处理' :
                     currentNode === 'finish' ? '🏁 完成' :
                     currentNode === 'interceptor' ? '🔒 拦截检查' :
                     currentNode === 'approval' ? '✅ 审批' : currentNode }}
                </span>
              </div>
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :disabled="loading || !initialized"
          placeholder="请输入您的问题，按 Enter 发送..."
          @keydown.enter.prevent="handleSend"
          resize="none"
          maxlength="2000"
          show-word-limit
        />
        <div class="input-actions">
          <span class="input-hint">按 Enter 发送 · Shift+Enter 换行</span>
          <el-button
            type="primary"
            :loading="loading"
            :disabled="!inputText.trim() || !initialized"
            @click="handleSend"
            :icon="Promotion"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick, computed } from 'vue'
import { useChatStore } from '@/store/chat'
import { storeToRefs } from 'pinia'
import {
  Delete, Plus, Promotion, MagicStick, ChatDotSquare,
  Van, Goods, Refresh, WarningFilled, Loading
} from '@element-plus/icons-vue'

const chatStore = useChatStore()
const { messages, loading, initialized, currentNode } = storeToRefs(chatStore)

const inputText = ref('')
const messageArea = ref(null)
const showScrollBtn = ref(false)

const sessionId = computed(() => chatStore.sessionId)

onMounted(async () => {
  await chatStore.initSession()
  scrollToBottom()
})

watch(messages, () => {
  nextTick(() => {
    if (!showScrollBtn.value) {
      scrollToBottom()
    }
  })
}, { deep: true })

function scrollToBottom() {
  nextTick(() => {
    const el = messageArea.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function onScroll() {
  const el = messageArea.value
  if (!el) return
  const threshold = 100
  showScrollBtn.value = el.scrollHeight - el.scrollTop - el.clientHeight > threshold
}

function renderContent(text) {
  if (!text) return ''
  return text
    .replace(/\n/g, '<br>')
    .replace(/- (.+)/g, '• $1')
}

function getCitationType(cite) {
  const cat = cite.metadata?.category
  if (cat === 'product') return 'success'
  if (cat === 'policy') return 'warning'
  if (cat === 'faq') return 'info'
  return ''
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  inputText.value = ''
  await chatStore.sendStream(text)
  scrollToBottom()
}

function sendQuick(text) {
  inputText.value = text
  handleSend()
}

async function handleNewSession() {
  await chatStore.newSession()
  inputText.value = ''
  scrollToBottom()
}

function handleClear() {
  chatStore.clearMessages()
}
</script>
