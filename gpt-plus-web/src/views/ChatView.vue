<script setup lang="ts">
import { AddIcon, DeleteIcon, EditIcon } from 'tdesign-icons-vue-next'
import hljs from 'highlight.js'
import MarkdownIt from 'markdown-it'
import { MessagePlugin } from 'tdesign-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { CHAT_MODEL_OPTIONS } from '@/config/chat-models'
import type { ChatMessage } from '@/stores/chat'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const draftMessage = ref('')
const messageComments = ref<Record<number, 'good' | 'bad' | ''>>({})
const isTitleDialogVisible = ref(false)
const titleDraft = ref('')
const isDeleteDialogVisible = ref(false)
const messagePanelRef = ref<HTMLElement | null>(null)
const isNearMessageBottom = ref(true)
const hasScrolledForCurrentSession = ref(false)
const codeCopyResetTimers = new WeakMap<HTMLButtonElement, number>()
let messageScrollContainer: HTMLElement | null = null
let messageScrollFrame = 0

const sessionCountText = computed(() => `${chatStore.sessions.length} sessions`)
const canSend = computed(() => draftMessage.value.trim().length > 0 && !chatStore.isResponding)
const canManageCurrentSession = computed(() => Boolean(chatStore.activeSession))
const isSessionActionDisabled = computed(
  () =>
    !chatStore.activeSession ||
    chatStore.isMessagesLoading ||
    chatStore.isSessionsLoading ||
    chatStore.isResponding ||
    chatStore.isCreatingSession,
)
const modeOptions = [
  { label: 'Quick Mode', value: 'quick' },
  { label: 'Expert Mode', value: 'expert' },
]
const modelOptions = CHAT_MODEL_OPTIONS.map((option) => ({
  label: option.label,
  value: option.code,
}))
const skeletonRows = [
  [
    { width: '42px', height: '42px', type: 'circle' },
    { width: '64%', height: '72px' },
  ],
  [
    { width: '42px', height: '42px', type: 'circle' },
    { width: '48%', height: '72px' },
  ],
  [
    { width: '42px', height: '42px', type: 'circle' },
    { width: '58%', height: '72px' },
  ],
]
const actionBarOptions = ['copy', 'replay', 'good', 'bad'] as const
const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  highlight(code: string, language: string) {
    const normalizedLanguage = language && hljs.getLanguage(language) ? language : 'plaintext'
    const highlightedCode = hljs.highlight(code, {
      language: normalizedLanguage,
      ignoreIllegals: true,
    }).value

    return `
      <div class="chat-code-block">
        <div class="chat-code-block__toolbar">
          <span class="chat-code-block__language">${normalizedLanguage}</span>
          <button type="button" class="chat-code-block__copy">Copy code</button>
        </div>
        <pre class="hljs"><code class="language-${normalizedLanguage}">${highlightedCode}</code></pre>
      </div>
    `
  },
})

const chatListData = computed(() =>
  chatStore.messages.map((message) => ({
    id: message.id,
    avatar: message.role === 'assistant' ? 'https://tdesign.gtimg.com/site/chat-avatar.png' : '',
    name: getMessageName(message),
    role: message.status === 'error' ? 'error' : message.role,
    datetime: getMessageDatetime(message),
    content: [
      {
        type: message.role === 'assistant' ? 'markdown' : 'text',
        data: message.content,
      },
    ],
    status: message.status === 'error' ? 'error' : '',
    comment: messageComments.value[message.id] || '',
    sourceMessage: message,
  })),
)
const hasMessages = computed(() => chatListData.value.length > 0)
const hasInlineError = computed(() => Boolean(chatStore.errorMessage))

function getLastMessage() {
  return chatStore.messages[chatStore.messages.length - 1]
}

onMounted(() => {
  void chatStore.bootstrap()
  void nextTick(() => {
    bindMessagePanelEvents()
  })
})

onBeforeUnmount(() => {
  unbindMessagePanelEvents()

  if (messageScrollFrame) {
    cancelAnimationFrame(messageScrollFrame)
  }
})

watch(
  () => chatStore.errorMessage,
  (value) => {
    if (value) {
      MessagePlugin.error(value)
    }
  },
)

watch(
  () => [chatStore.currentSessionId, chatStore.isMessagesLoading] as const,
  ([currentSessionId, isMessagesLoading], [previousSessionId, previousIsMessagesLoading]) => {
    if (currentSessionId !== previousSessionId) {
      hasScrolledForCurrentSession.value = false
    }

    void nextTick(() => {
      bindMessagePanelEvents()
    })

    if (previousIsMessagesLoading && !isMessagesLoading && currentSessionId) {
      scrollMessagePanelToBottom('auto')
      hasScrolledForCurrentSession.value = true
    }
  },
)

watch(
  () => {
    const lastMessage = getLastMessage()
    if (!lastMessage) {
      return 'empty'
    }

    return `${chatStore.currentSessionId}:${lastMessage.id}:${lastMessage.status || 'done'}:${lastMessage.content.length}:${chatStore.messages.length}`
  },
  (signature) => {
    if (signature === 'empty') {
      return
    }

    void nextTick(() => {
      bindMessagePanelEvents()
      const lastMessage = getLastMessage()
      if (!lastMessage) {
        return
      }

      const shouldForceScroll =
        !hasScrolledForCurrentSession.value || lastMessage.role === 'user' || lastMessage.status === 'streaming'

      if (shouldForceScroll || isNearMessageBottom.value) {
        scrollMessagePanelToBottom(lastMessage.status === 'streaming' ? 'auto' : 'smooth')
        hasScrolledForCurrentSession.value = true
      }
    })
  },
)

async function handleSendClick(value?: string) {
  const content = (value ?? draftMessage.value).trim()
  if (!content) {
    return
  }

  const success = await chatStore.sendMessage(content)
  if (success) {
    draftMessage.value = ''
  }
}

async function handleRetryClick(content?: string) {
  const success = await chatStore.retryMessage(content)
  if (success) {
    draftMessage.value = ''
  }
  return success
}

function getMessageName(message: ChatMessage) {
  if (message.role === 'assistant') {
    return message.modelName || 'Assistant'
  }

  if (message.role === 'system') {
    return 'System'
  }

  return 'You'
}

function getMessageDatetime(message: ChatMessage) {
  if (message.status === 'streaming') {
    return 'Streaming...'
  }

  if (message.status === 'error') {
    return 'Failed'
  }

  return message.createdAt
}

function getContentRole(item: { sourceMessage: ChatMessage }) {
  return item.sourceMessage.status === 'error' ? 'assistant' : item.sourceMessage.role
}

function getActionContent(item: { content?: Array<{ data?: string }> }) {
  return item.content?.map((content) => content.data || '').join('\n') || ''
}

function renderAssistantContent(value: string) {
  return markdown.render(value || '')
}

function getMessageScrollContainer() {
  return messagePanelRef.value?.querySelector('.message-panel__chat-list') as HTMLElement | null
}

function updateMessageBottomState() {
  if (!messageScrollContainer) {
    return
  }

  const distanceToBottom =
    messageScrollContainer.scrollHeight - messageScrollContainer.scrollTop - messageScrollContainer.clientHeight
  isNearMessageBottom.value = distanceToBottom <= 96
}

function handleMessageScroll() {
  updateMessageBottomState()
}

function bindMessagePanelEvents() {
  const nextScrollContainer = getMessageScrollContainer()
  if (!nextScrollContainer) {
    unbindMessagePanelEvents()
    return
  }

  if (messageScrollContainer === nextScrollContainer) {
    updateMessageBottomState()
    return
  }

  unbindMessagePanelEvents()
  messageScrollContainer = nextScrollContainer
  messageScrollContainer.addEventListener('scroll', handleMessageScroll, { passive: true })
  messageScrollContainer.addEventListener('click', handleMessagePanelClick)
  updateMessageBottomState()
}

function unbindMessagePanelEvents() {
  if (!messageScrollContainer) {
    return
  }

  messageScrollContainer.removeEventListener('scroll', handleMessageScroll)
  messageScrollContainer.removeEventListener('click', handleMessagePanelClick)
  messageScrollContainer = null
}

function scrollMessagePanelToBottom(behavior: ScrollBehavior = 'smooth') {
  if (messageScrollFrame) {
    cancelAnimationFrame(messageScrollFrame)
  }

  messageScrollFrame = requestAnimationFrame(() => {
    const scrollContainer = getMessageScrollContainer()
    if (!scrollContainer) {
      return
    }

    messageScrollContainer = scrollContainer
    scrollContainer.scrollTo({
      top: scrollContainer.scrollHeight,
      behavior,
    })
    isNearMessageBottom.value = true
  })
}

async function handleMessagePanelClick(event: Event) {
  const target = event.target
  if (!(target instanceof HTMLElement)) {
    return
  }

  const copyButton = target.closest('.chat-code-block__copy')
  if (!(copyButton instanceof HTMLButtonElement)) {
    return
  }

  const codeElement = copyButton.closest('.chat-code-block')?.querySelector('code')
  const codeText = codeElement?.textContent || ''
  if (!codeText) {
    MessagePlugin.warning('没有可复制的代码内容')
    return
  }

  const copied = await copyCodeText(codeText)
  if (!copied) {
    MessagePlugin.error('复制代码失败，请稍后重试')
    return
  }

  const previousTimer = codeCopyResetTimers.get(copyButton)
  if (previousTimer) {
    window.clearTimeout(previousTimer)
  }

  copyButton.textContent = 'Copied'
  copyButton.classList.add('is-copied')
  const timer = window.setTimeout(() => {
    copyButton.textContent = 'Copy code'
    copyButton.classList.remove('is-copied')
    codeCopyResetTimers.delete(copyButton)
  }, 1800)
  codeCopyResetTimers.set(copyButton, timer)
}

async function copyCodeText(value: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    // Fall back to document.execCommand('copy') for browsers without clipboard permission.
  }

  const textarea = document.createElement('textarea')
  textarea.value = value
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  textarea.style.pointerEvents = 'none'
  document.body.appendChild(textarea)
  textarea.select()

  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  return copied
}

async function handleMessageAction(type: string, item: { id: number; sourceMessage: ChatMessage }) {
  if (type === 'good' || type === 'bad') {
    messageComments.value[item.id] = messageComments.value[item.id] === type ? '' : type
    return
  }

  if (type === 'replay') {
    const success = await handleRetryClick(item.sourceMessage.retryContent)
    if (!success) {
      MessagePlugin.warning('No retry content available for this message')
    }
  }
}

function handleStopClick() {
  MessagePlugin.info('Stop is not wired yet')
}

function openTitleDialog() {
  if (!chatStore.activeSession) {
    return
  }

  titleDraft.value = chatStore.activeSession.title
  isTitleDialogVisible.value = true
}

function closeTitleDialog() {
  if (chatStore.isUpdatingSessionTitle) {
    return
  }

  isTitleDialogVisible.value = false
}

async function confirmTitleDialog() {
  if (!chatStore.activeSession) {
    return
  }

  const success = await chatStore.updateSessionTitle(chatStore.activeSession.id, titleDraft.value)
  if (success) {
    MessagePlugin.success('会话标题已更新')
    isTitleDialogVisible.value = false
  }
}

function openDeleteDialog() {
  if (!chatStore.activeSession) {
    return
  }

  isDeleteDialogVisible.value = true
}

function closeDeleteDialog() {
  if (chatStore.isDeletingSession) {
    return
  }

  isDeleteDialogVisible.value = false
}

async function confirmDeleteDialog() {
  if (!chatStore.activeSession) {
    return
  }

  const deletedTitle = chatStore.activeSession.title
  const success = await chatStore.deleteSession(chatStore.activeSession.id)
  if (success) {
    MessagePlugin.success(chatStore.sessions.length > 0 ? `已删除「${deletedTitle}」` : '已删除当前会话，列表已清空')
    isDeleteDialogVisible.value = false
  }
}
</script>

<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <div class="chat-sidebar__brand">
        <div class="chat-sidebar__badge">GPT+</div>
        <div>
          <div class="chat-sidebar__title">GPT Plus</div>
          <div class="chat-sidebar__subtitle">Multi-model workspace</div>
        </div>
      </div>

      <t-button
        class="chat-sidebar__new"
        theme="primary"
        size="large"
        shape="round"
        block
        :loading="chatStore.isCreatingSession"
        @click="chatStore.createSession"
      >
        <template #icon><AddIcon /></template>
        New Chat
      </t-button>

      <div class="chat-sidebar__meta">
        <span>{{ sessionCountText }}</span>
        <span>stream ready</span>
      </div>

      <div class="chat-sidebar__list">
        <button
          v-for="session in chatStore.sessions"
          :key="session.id"
          class="session-card"
          :class="{ 'session-card--active': session.id === chatStore.currentSessionId }"
          type="button"
          @click="chatStore.selectSession(session.id)"
        >
          <div class="session-card__top">
            <span class="session-card__mode">{{ session.modeCode }}</span>
            <span class="session-card__time">{{ session.updatedAt }}</span>
          </div>
          <div class="session-card__title">{{ session.title }}</div>
        </button>

        <div v-if="!chatStore.isSessionsLoading && chatStore.sessions.length === 0" class="session-empty">
          No sessions yet. Create your first chat to start the thread.
        </div>
      </div>
    </aside>

    <main class="chat-main">
      <header class="chat-header">
        <div>
          <div class="chat-header__eyebrow">Current session</div>
          <h1 class="chat-header__title">{{ chatStore.currentSessionTitle }}</h1>
        </div>

        <div class="chat-header__actions">
          <t-radio-group
            v-model="chatStore.currentMode"
            theme="button"
            variant="primary-filled"
            :options="modeOptions"
          />

          <t-select v-model="chatStore.currentModel" class="chat-header__select" :options="modelOptions" />

          <t-button
            class="chat-header__action-button"
            variant="outline"
            :disabled="isSessionActionDisabled"
            :loading="chatStore.isUpdatingSessionTitle"
            @click="openTitleDialog"
          >
            <template #icon><EditIcon /></template>
            Rename
          </t-button>

          <t-button
            class="chat-header__action-button"
            theme="danger"
            variant="outline"
            :disabled="isSessionActionDisabled"
            :loading="chatStore.isDeletingSession"
            @click="openDeleteDialog"
          >
            <template #icon><DeleteIcon /></template>
            Delete
          </t-button>
        </div>
      </header>

      <section ref="messagePanelRef" class="message-panel">
        <div v-if="chatStore.isBootstrapping || chatStore.isMessagesLoading" class="message-panel__state">
          <t-skeleton :row-col="skeletonRows" animation="gradient" />
        </div>

        <t-chat-list
          v-else-if="hasMessages"
          class="message-panel__chat-list"
          :data="chatListData"
          :clear-history="false"
          :text-loading="chatStore.isResponding"
          animation="gradient"
          layout="both"
          :auto-scroll="true"
          :show-scroll-button="true"
        >
          <template #content="{ item }">
            <template v-for="(content, contentIndex) in item.content" :key="contentIndex">
              <div
                v-if="item.sourceMessage.role === 'assistant'"
                class="chat-markdown"
                :class="{ 'chat-markdown--error': item.sourceMessage.status === 'error' }"
                v-html="renderAssistantContent(content.data)"
              />
              <t-chat-content
                v-else
                :content="content"
                :role="getContentRole(item)"
                :status="item.sourceMessage.status === 'error' ? 'error' : ''"
              />
            </template>
          </template>

          <template #actionbar="{ item }">
            <t-chat-actionbar
              v-if="item.sourceMessage.role === 'assistant'"
              :content="getActionContent(item)"
              :action-bar="actionBarOptions"
              :comment="item.comment"
              :disabled="item.sourceMessage.status === 'streaming'"
              @actions="handleMessageAction($event, item)"
            />
          </template>
        </t-chat-list>

        <div v-else class="message-panel__state message-panel__state--empty">
          <div class="message-panel__empty-title">Your conversation is ready</div>
        </div>
      </section>

      <footer class="composer">
        <t-alert
          v-if="hasInlineError"
          class="composer__alert"
          theme="warning"
          :close="true"
          :message="chatStore.errorMessage"
          @close="chatStore.clearErrorMessage"
        />

        <t-chat-sender
          v-model="draftMessage"
          class="composer__sender"
          :loading="chatStore.isResponding"
          :send-btn-disabled="!canSend"
          :textarea-props="{
            autosize: { minRows: 4, maxRows: 8 },
            placeholder: 'Ask anything, refine prompts, or continue the current thread...',
          }"
          @send="handleSendClick"
          @stop="handleStopClick"
        >
          <template #footer-prefix>
            <span class="composer__hint">Ready</span>
          </template>

          <template #suffix>
            <span class="composer__mode">mode: {{ chatStore.currentMode }}</span>
          </template>
        </t-chat-sender>
      </footer>
    </main>

    <t-dialog
      v-model:visible="isTitleDialogVisible"
      header="修改会话标题"
      width="520px"
      :confirm-loading="chatStore.isUpdatingSessionTitle"
      :close-on-overlay-click="!chatStore.isUpdatingSessionTitle"
      :confirm-btn="{
        theme: 'primary',
        content: '保存标题',
      }"
      :cancel-btn="{
        content: '取消',
      }"
      @confirm="confirmTitleDialog"
      @close="closeTitleDialog"
      @cancel="closeTitleDialog"
    >
      <div class="session-dialog__body">
        <p class="session-dialog__text">修改当前选中会话的标题，保存后左侧列表和顶部标题会同步更新。</p>
        <t-input
          v-model="titleDraft"
          class="session-dialog__input"
          :maxlength="80"
          placeholder="请输入新的会话标题"
          autofocus
        />
      </div>
    </t-dialog>

    <t-dialog
      v-model:visible="isDeleteDialogVisible"
      theme="warning"
      header="删除当前会话"
      width="520px"
      :confirm-loading="chatStore.isDeletingSession"
      :close-on-overlay-click="!chatStore.isDeletingSession"
      :confirm-btn="{
        theme: 'danger',
        content: '确认删除',
      }"
      :cancel-btn="{
        content: '取消',
      }"
      @confirm="confirmDeleteDialog"
      @close="closeDeleteDialog"
      @cancel="closeDeleteDialog"
    >
      <div class="session-dialog__body">
        <p class="session-dialog__text">
          删除后该会话会从当前列表移除，前端会自动切换到剩余会话；如果没有剩余会话，则回到空态。
        </p>
        <div v-if="canManageCurrentSession" class="session-dialog__danger-card">
          <div class="session-dialog__danger-label">当前会话</div>
          <div class="session-dialog__danger-title">{{ chatStore.currentSessionTitle }}</div>
        </div>
      </div>
    </t-dialog>
  </div>
</template>
