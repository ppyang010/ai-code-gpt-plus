<script setup lang="ts">
import { AddIcon, DeleteIcon, EditIcon, SearchIcon, SendIcon } from 'tdesign-icons-vue-next'
import hljs from 'highlight.js'
import MarkdownIt from 'markdown-it'
import { MessagePlugin } from 'tdesign-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { ChatMessage, SessionPreview } from '@/stores/chat'
import { useChatStore } from '@/stores/chat'
import type { WebSearchMode } from '@/types/chat'

const chatStore = useChatStore()
const draftMessage = ref('')
const imageInputRef = ref<{ click: () => void } | null>(null)
const messageComments = ref<Record<number, 'good' | 'bad' | ''>>({})
const isTitleDialogVisible = ref(false)
const titleDraft = ref('')
const isDeleteDialogVisible = ref(false)
const targetSessionId = ref<number | null>(null)
const messagePanelRef = ref<HTMLElement | null>(null)
const isNearMessageBottom = ref(true)
const hasScrolledForCurrentSession = ref(false)
const codeCopyResetTimers = new WeakMap<HTMLButtonElement, number>()
let messageScrollContainer: HTMLElement | null = null
let messageScrollFrame = 0

const sessionCountText = computed(() => `${chatStore.sessions.length} sessions`)
const sessionPageText = computed(() => `Page ${chatStore.sessionPageNo}`)
const canSend = computed(() => draftMessage.value.trim().length > 0 && !chatStore.isResponding)
const targetSession = computed(() => chatStore.sessions.find((session) => session.id === targetSessionId.value) ?? null)
const groupedSessions = computed(() => {
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const oneDayMs = 24 * 60 * 60 * 1000
  const groups = {
    today: [] as SessionPreview[],
    yesterday: [] as SessionPreview[],
    week: [] as SessionPreview[],
    earlier: [] as SessionPreview[],
  }

  for (const session of chatStore.sessions) {
    const timestamp = new Date(session.updatedAtRaw).getTime()
    if (Number.isNaN(timestamp)) {
      groups.earlier.push(session)
      continue
    }

    const sessionDay = new Date(new Date(timestamp).getFullYear(), new Date(timestamp).getMonth(), new Date(timestamp).getDate()).getTime()
    const diff = startOfToday - sessionDay

    if (diff <= 0) {
      groups.today.push(session)
    } else if (diff <= oneDayMs) {
      groups.yesterday.push(session)
    } else if (diff <= oneDayMs * 7) {
      groups.week.push(session)
    } else {
      groups.earlier.push(session)
    }
  }

  return [
    { label: '今天', items: groups.today },
    { label: '昨天', items: groups.yesterday },
    { label: '7 天内', items: groups.week },
    { label: '更早', items: groups.earlier },
  ].filter((group) => group.items.length > 0)
})
type ModeCode = SessionPreview['modeCode']

const modeOptions = [
  { label: '快速', value: 'quick' },
  { label: '思考', value: 'expert' },
]
const webSearchModeOptions: Array<{ label: string; value: WebSearchMode }> = [
  { label: '关闭', value: 'disabled' },
  { label: '自动', value: 'auto' },
  { label: '开启', value: 'enabled' },
]
const modelOptions = computed(() =>
  chatStore.availableModels.map((option) => ({
    label: option.label,
    value: option.code,
  })),
)
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
const assistantActionBarOptions = ['copy', 'replay', 'good', 'bad'] as const
const userActionBarOptions = ['copy'] as const
const markdown = new MarkdownIt({
  breaks: true,
  linkify: true,
  highlight(code: string, language: string) {
    // markdown-it 的 highlight 钩子里注入代码块工具栏，便于对单个代码块做复制。
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

function getModeLabel(modeCode: ModeCode, options?: { compact?: boolean }) {
  // 后端仍然使用 quick / expert 作为持久化和接口码值，前端展示层将其解释为快速 / 思考模式。
  if (modeCode === 'expert') {
    return options?.compact ? '思考' : '思考模式'
  }

  return options?.compact ? '快速' : '快速模式'
}

function getWebSearchModeLabel(mode: WebSearchMode) {
  if (mode === 'enabled') {
    return '开启'
  }

  if (mode === 'disabled') {
    return '关闭'
  }

  return '自动判断'
}

// 将后端搜索状态压成短文案，避免消息区展示过多供应商细节。
function getWebSearchBadgeText(message: ChatMessage) {
  if (!message.webSearchEnabled) {
    return ''
  }

  if (message.webSearchExecuted) {
    return '已联网搜索'
  }

  if (message.webSearchStatus === 'failed') {
    return '联网搜索失败'
  }

  if (message.webSearchStatus === 'skipped') {
    return '联网搜索未执行'
  }

  return '联网搜索'
}

/**
 * 开启思考模式后，消息开始生成就先展示“思考中”占位；真正收到 reasoning chunk 后持续追加内容。
 */
function shouldShowReasoningPanel(message: ChatMessage) {
  if (message.role !== 'assistant') {
    return false
  }
  if (message.reasoningContent?.trim()) {
    return true
  }
  return Boolean(message.deepThinkingEnabled && message.status === 'streaming')
}

/**
 * 根据消息状态给思考过程折叠区提供更贴近当前阶段的标题。
 */
function getReasoningSummaryText(message: ChatMessage) {
  if (message.status === 'streaming') {
    return message.reasoningContent?.trim() ? '思考中' : '思考中...'
  }
  if (message.status === 'interrupted') {
    return '已中断的思考过程'
  }
  return '思考过程'
}

/**
 * 思考区在首个 chunk 到来前先显示占位文案，避免用户误以为没有开启思考模式。
 */
function getReasoningBodyText(message: ChatMessage) {
  if (message.reasoningContent?.trim()) {
    return message.reasoningContent
  }
  return message.status === 'streaming' ? '等待模型返回思考过程...' : ''
}

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
      // 切换会话首次加载完成后直接定位到底部，避免长历史停留在旧滚动位置。
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
        // 用户主动滚到上方看历史时不强制置底；只有接近底部或正在流式输出才自动跟随。
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

  draftMessage.value = ''
  // 输入框立即清空，发送失败时由消息区失败态和 retryContent 承接重试。
  await chatStore.sendMessage(content)
}

async function handleRetryClick(content?: string, attachments?: ChatMessage['retryAttachments']) {
  const success = await chatStore.retryMessage(content, attachments || [])
  if (success) {
    draftMessage.value = ''
  }
  return success
}

async function handleContinueClick(messageId: number) {
  return chatStore.continueMessage(messageId)
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

  if (message.status === 'interrupted') {
    return 'Interrupted'
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
  // TDesign Chat 内部滚动容器会随渲染切换，因此这里每次 nextTick 后重新绑定真实 DOM。
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

  // 代码块是 markdown 渲染出来的 HTML，使用事件委托比给每个块挂 Vue 事件更稳定。
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
    const success =
      item.sourceMessage.status === 'interrupted'
        ? await handleContinueClick(item.sourceMessage.id)
        : await handleRetryClick(item.sourceMessage.retryContent, item.sourceMessage.retryAttachments)
    if (!success) {
      MessagePlugin.warning(
        item.sourceMessage.status === 'interrupted'
          ? '当前消息暂时无法继续生成'
          : 'No retry content available for this message',
      )
    }
  }
}

function handleStopClick() {
  const stopped = chatStore.stopStreamingMessage()
  if (stopped) {
    MessagePlugin.info('已停止生成，可稍后继续')
  }
}

function isSessionActionDisabled(sessionId: number) {
  return (
    chatStore.isMessagesLoading ||
    chatStore.isSessionsLoading ||
    chatStore.isResponding ||
    chatStore.isCreatingSession ||
    (chatStore.sessionActionSessionId === sessionId && (chatStore.isUpdatingSessionTitle || chatStore.isDeletingSession))
  )
}

function openTitleDialog(sessionId: number) {
  const session = chatStore.sessions.find((item) => item.id === sessionId)
  if (!session) {
    return
  }

  targetSessionId.value = sessionId
  // 编辑仍读取后端原始标题，避免把“第一问摘要兜底展示”保存成用户标题。
  titleDraft.value = session.title
  isTitleDialogVisible.value = true
}

function closeTitleDialog() {
  if (chatStore.isUpdatingSessionTitle) {
    return
  }

  isTitleDialogVisible.value = false
}

async function confirmTitleDialog() {
  if (!targetSession.value) {
    return
  }

  const success = await chatStore.updateSessionTitle(targetSession.value.id, titleDraft.value)
  if (success) {
    MessagePlugin.success('会话标题已更新')
    isTitleDialogVisible.value = false
    targetSessionId.value = null
  }
}

function openDeleteDialog(sessionId: number) {
  const session = chatStore.sessions.find((item) => item.id === sessionId)
  if (!session) {
    return
  }

  targetSessionId.value = sessionId
  isDeleteDialogVisible.value = true
}

function closeDeleteDialog() {
  if (chatStore.isDeletingSession) {
    return
  }

  isDeleteDialogVisible.value = false
}

async function confirmDeleteDialog() {
  if (!targetSession.value) {
    return
  }

  const deletedTitle = targetSession.value.displayTitle
  const success = await chatStore.deleteSession(targetSession.value.id)
  if (success) {
    MessagePlugin.success(chatStore.sessions.length > 0 ? `已删除「${deletedTitle}」` : '已删除当前会话，列表已清空')
    isDeleteDialogVisible.value = false
    targetSessionId.value = null
  }
}

function openImagePicker() {
  imageInputRef.value?.click()
}

/**
 * 从剪贴板事件中提取图片文件；只有图片才走上传链路，普通文本粘贴保持默认行为。
 */
function extractImageFilesFromClipboard(
  event: Event & {
    clipboardData?: {
      items?: ArrayLike<{
        kind?: string
        type?: string
        getAsFile?: () => unknown
      }>
    } | null
  },
) {
  const clipboardItems = Array.from(event.clipboardData?.items || [])
  return clipboardItems
    .filter((item) => item.kind === 'file' && item.type?.startsWith('image/'))
    .map((item) => item.getAsFile?.())
    .filter(Boolean)
}

async function handleImageSelected(event: Event) {
  const input = event.target as { files?: unknown[] | null; value?: string } | null
  const selectedFiles = Array.from(input?.files || [])

  for (const file of selectedFiles) {
    await chatStore.uploadImage(file as never)
  }

  if (input) {
    input.value = ''
  }
}

/**
 * 输入区支持直接粘贴图片，并复用现有发送前预览与上传逻辑。
 */
async function handleSenderPaste(
  event: Event & {
    clipboardData?: {
      items?: ArrayLike<{
        kind?: string
        type?: string
        getAsFile?: () => unknown
      }>
    } | null
    preventDefault: () => void
  },
) {
  const imageFiles = extractImageFilesFromClipboard(event)
  if (imageFiles.length === 0) {
    return
  }

  event.preventDefault()

  if (chatStore.isResponding) {
    MessagePlugin.warning('当前正在生成回复，请稍后再粘贴图片')
    return
  }
  if (chatStore.isImageUploading) {
    MessagePlugin.warning('图片上传中，请稍后再试')
    return
  }

  for (const file of imageFiles) {
    await chatStore.uploadImage(file as never)
  }
}

function removePendingAttachment(fileId: number) {
  chatStore.removePendingAttachment(fileId)
}
</script>

<template>
  <div class="chat-page">
    <aside class="chat-sidebar">
      <div class="chat-sidebar__brand">
        <div class="chat-sidebar__brand-main">
          <div class="chat-sidebar__badge">GPT+</div>
          <div>
            <div class="chat-sidebar__title">GPT Plus</div>
            <div class="chat-sidebar__subtitle">Multi-model workspace</div>
          </div>
        </div>
        <button class="chat-sidebar__toolbar-button" type="button" aria-label="search sessions">
          <SearchIcon />
        </button>
      </div>

      <t-button
        class="chat-sidebar__new"
        theme="primary"
        size="large"
        :loading="chatStore.isCreatingSession"
        @click="chatStore.createSession"
      >
        <div class="chat-sidebar__new-content">
          <span class="chat-sidebar__new-icon">
            <AddIcon />
          </span>
          <span class="chat-sidebar__new-copy">
            <span class="chat-sidebar__new-title">New Chat</span>
          </span>
        </div>
      </t-button>

      <div class="chat-sidebar__meta">
        <span>{{ sessionCountText }} / {{ chatStore.sessionTotal }}</span>
        <span>{{ sessionPageText }}</span>
      </div>

      <div class="chat-sidebar__list">
        <section v-for="group in groupedSessions" :key="group.label" class="session-group">
          <div class="session-group__label">{{ group.label }}</div>
          <button
            v-for="session in group.items"
            :key="session.id"
            class="session-card"
            :class="{ 'session-card--active': session.id === chatStore.currentSessionId }"
            type="button"
            @click="chatStore.selectSession(session.id)"
          >
            <div class="session-card__header">
              <div class="session-card__title">{{ session.displayTitle }}</div>
              <div class="session-card__time">{{ session.updatedAt }}</div>
            </div>
            <div class="session-card__footer">
              <span class="session-card__mode">{{ getModeLabel(session.modeCode) }}</span>
              <div class="session-card__actions">
                <t-button
                  class="session-card__icon-button"
                  size="small"
                  variant="text"
                  shape="square"
                  aria-label="Rename session"
                  title="Rename"
                  :disabled="isSessionActionDisabled(session.id)"
                  :loading="chatStore.isUpdatingSessionTitle && chatStore.sessionActionSessionId === session.id"
                  @click.stop="openTitleDialog(session.id)"
                >
                  <template #icon><EditIcon /></template>
                </t-button>
                <t-button
                  class="session-card__icon-button"
                  size="small"
                  theme="danger"
                  variant="text"
                  shape="square"
                  aria-label="Delete session"
                  title="Delete"
                  :disabled="isSessionActionDisabled(session.id)"
                  :loading="chatStore.isDeletingSession && chatStore.sessionActionSessionId === session.id"
                  @click.stop="openDeleteDialog(session.id)"
                >
                  <template #icon><DeleteIcon /></template>
                </t-button>
              </div>
            </div>
          </button>
        </section>

        <div v-if="!chatStore.isSessionsLoading && chatStore.sessions.length === 0" class="session-empty">
          No sessions yet. Create your first chat to start the thread.
        </div>
      </div>

      <div class="chat-sidebar__pagination">
        <span class="chat-sidebar__page-text">{{ sessionPageText }}</span>
        <t-button
          v-if="chatStore.sessionHasMore"
          size="small"
          variant="outline"
          :loading="chatStore.isSessionsLoading"
          @click="chatStore.loadMoreSessions"
        >
          下一页
        </t-button>
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
            class="chat-header__mode-toggle"
            theme="button"
            variant="primary-filled"
            :options="modeOptions"
          />

          <t-select v-model="chatStore.currentModel" class="chat-header__select" :options="modelOptions" />
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
          :text-loading="false"
          animation="gradient"
          layout="both"
          :auto-scroll="true"
          :show-scroll-button="true"
        >
          <template #content="{ item }">
            <template v-for="(content, contentIndex) in item.content" :key="contentIndex">
              <!-- 只提示联网状态，完整搜索来源暂时保留在后端 metadata 中。 -->
              <div
                v-if="item.sourceMessage.role === 'assistant' && item.sourceMessage.webSearchEnabled"
                class="chat-message__web-search-badge"
              >
                {{ getWebSearchBadgeText(item.sourceMessage) }}
              </div>
              <details
                v-if="shouldShowReasoningPanel(item.sourceMessage)"
                class="chat-reasoning"
                :open="item.sourceMessage.status === 'streaming'"
              >
                <summary class="chat-reasoning__summary">{{ getReasoningSummaryText(item.sourceMessage) }}</summary>
                <div class="chat-reasoning__content">{{ getReasoningBodyText(item.sourceMessage) }}</div>
              </details>
              <div
                v-if="item.sourceMessage.role === 'assistant'"
                class="chat-markdown"
                :class="{ 'chat-markdown--error': item.sourceMessage.status === 'error' }"
                v-html="renderAssistantContent(content.data)"
              />
              <div v-if="item.sourceMessage.attachments?.length" class="chat-attachment-grid">
                <a
                  v-for="attachment in item.sourceMessage.attachments"
                  :key="attachment.fileId"
                  class="chat-attachment-card"
                  :href="attachment.fileUrl"
                  target="_blank"
                  rel="noreferrer"
                >
                  <img
                    class="chat-attachment-card__image"
                    :src="attachment.thumbnailUrl || attachment.fileUrl"
                    :alt="attachment.fileName"
                  />
                  <div class="chat-attachment-card__name">{{ attachment.fileName }}</div>
                </a>
              </div>
              <div
                v-if="item.sourceMessage.role === 'assistant' && item.sourceMessage.status === 'interrupted'"
                class="chat-message__actions"
              >
                <t-button
                  size="small"
                  theme="primary"
                  variant="text"
                  :disabled="chatStore.isResponding"
                  @click="handleContinueClick(item.sourceMessage.id)"
                >
                  继续生成
                </t-button>
              </div>
              <t-chat-content
                v-if="item.sourceMessage.role !== 'assistant' && item.sourceMessage.content"
                :content="content"
                :role="getContentRole(item)"
                :status="item.sourceMessage.status === 'error' ? 'error' : ''"
              />
            </template>
          </template>

          <template #actionbar="{ item }">
            <t-chat-actionbar
              v-if="item.sourceMessage.role === 'assistant' || item.sourceMessage.role === 'user'"
              :content="getActionContent(item)"
              :action-bar="item.sourceMessage.role === 'assistant' ? assistantActionBarOptions : userActionBarOptions"
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

        <div v-if="chatStore.pendingAttachments.length > 0" class="composer__attachments">
          <div
            v-for="attachment in chatStore.pendingAttachments"
            :key="attachment.fileId"
            class="composer-attachment"
          >
            <img
              class="composer-attachment__image"
              :src="attachment.thumbnailUrl || attachment.fileUrl"
              :alt="attachment.fileName"
            />
            <div class="composer-attachment__meta">
              <div class="composer-attachment__name">{{ attachment.fileName }}</div>
              <div class="composer-attachment__size">{{ Math.max(1, Math.round(attachment.fileSize / 1024)) }} KB</div>
            </div>
            <button class="composer-attachment__remove" type="button" @click="removePendingAttachment(attachment.fileId)">
              移除
            </button>
          </div>
        </div>

        <div class="composer__sender-shell" @paste.capture="handleSenderPaste">
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
              <div class="composer__footer-prefix">
                <button
                  type="button"
                  class="composer__upload-button"
                  :disabled="chatStore.isResponding || chatStore.isImageUploading"
                  @click="openImagePicker"
                >
                  {{ chatStore.isImageUploading ? '上传中...' : '添加图片' }}
                </button>
                <!-- 联网模式保留为紧凑下拉，避免长说明挤占输入区空间。 -->
                <label class="composer__web-search-dropdown">
                  <span class="composer__web-search-label">联网</span>
                  <select
                    v-model="chatStore.currentWebSearchMode"
                    class="composer__web-search-select"
                    :disabled="chatStore.isResponding"
                    aria-label="联网搜索模式"
                  >
                    <option
                      v-for="option in webSearchModeOptions"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </option>
                  </select>
                </label>
              </div>
            </template>

            <template #suffix>
              <div class="composer__suffix-actions">
                <t-button
                  v-if="chatStore.isResponding"
                  size="small"
                  theme="danger"
                  variant="outline"
                  @click="handleStopClick"
                >
                  停止生成
                </t-button>
                <t-button
                  v-else
                  class="composer__send-button"
                  theme="primary"
                  shape="circle"
                  :disabled="!canSend"
                  aria-label="Send message"
                  title="Send"
                  @click="handleSendClick()"
                >
                  <template #icon><SendIcon /></template>
                </t-button>
                <span class="composer__mode">当前模式：{{ getModeLabel(chatStore.currentMode, { compact: true }) }}</span>
                <span class="composer__mode">联网：{{ getWebSearchModeLabel(chatStore.currentWebSearchMode) }}</span>
              </div>
            </template>
          </t-chat-sender>
        </div>

        <input
          ref="imageInputRef"
          class="composer__image-input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          @change="handleImageSelected"
        />
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
        <div v-if="targetSession" class="session-dialog__danger-card">
          <div class="session-dialog__danger-label">当前会话</div>
          <div class="session-dialog__danger-title">{{ targetSession.displayTitle }}</div>
        </div>
      </div>
    </t-dialog>
  </div>
</template>
