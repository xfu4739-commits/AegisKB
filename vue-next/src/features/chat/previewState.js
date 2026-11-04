import { computed, ref } from 'vue'

export function reconcileScope(scope, documents) {
  const knowledgeBaseIds = [...new Set(scope.knowledgeBaseIds.map(String))]
  if (!knowledgeBaseIds.length) return { mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' }
  const mode = scope.mode === 'DOCUMENT' ? 'DOCUMENT' : 'AUTO_DOCUMENT'
  const documentId = documents.some((doc) => doc.id === scope.documentId && knowledgeBaseIds.includes(doc.knowledgeBaseId)) ? scope.documentId : ''
  return { mode, knowledgeBaseIds, documentId }
}

async function requestPreview(scenario) {
  const response = await fetch(`/__preview/chat?scenario=${encodeURIComponent(scenario)}`)
  if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('演示资料暂时无法加载，请重试。')
  return response.json()
}

export function usePreviewState(enabled, request = requestPreview) {
  const sessions = ref([])
  const currentId = ref('')
  const status = ref('ready')
  const error = ref('')
  const knowledgeBases = ref([])
  const documents = ref([])
  const scope = ref({ mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' })
  let sequence = 0
  let localId = 0
  const active = computed(() => sessions.value.find((item) => item.id === currentId.value))
  const messages = computed(() => active.value?.messages || [])

  async function load(scenario) {
    if (!enabled) return
    const token = ++sequence
    status.value = 'loading'
    error.value = ''
    try {
      const data = await request(scenario)
      if (token !== sequence) return
      sessions.value = data.sessions
      knowledgeBases.value = data.knowledgeBases
      documents.value = data.documents
      currentId.value = data.sessions[0]?.id || ''
      scope.value = { mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' }
      status.value = 'ready'
    } catch (failure) {
      if (token !== sequence) return
      error.value = failure.message || '演示资料加载失败。'
      status.value = 'error'
    }
  }
  function reset() {
    sequence++
    currentId.value = ''
    error.value = ''
    status.value = 'ready'
  }
  function select(id) {
    if (!sessions.value.some((item) => item.id === id)) return
    sequence++
    currentId.value = id
    status.value = 'ready'
    error.value = ''
  }
  function remove(id) {
    sessions.value = sessions.value.filter((item) => item.id !== id)
    if (currentId.value === id) reset()
  }
  function send(text) {
    if (!enabled || status.value !== 'ready' || !text.trim()) return false
    if (scope.value.mode === 'DOCUMENT' && !scope.value.documentId) return false
    if (!active.value) {
      currentId.value = `demo-local-${++localId}`
      sessions.value = [{ id: currentId.value, title: text.trim(), group: '今天', messages: [] }, ...sessions.value]
    }
    active.value.messages.push(
      { id: `demo-user-${++localId}`, role: 'user', content: text.trim(), time: '刚刚' },
      { id: `demo-answer-${++localId}`, role: 'assistant', content: '这是一条 **固定演示回答**，用于检查消息排版与交互。\n\n这里没有调用模型，也没有保存到服务器。你可以切换会话、新建对话，或继续检查输入区的回答范围。', time: '刚刚' }
    )
    return true
  }
  function retry(id) {
    const message = messages.value.find((item) => item.id === id)
    if (message?.error) {
      message.error = ''
      message.content = '演示回答已重新展示。此操作只更新本地界面，不调用模型。'
    }
  }
  function updateScope(patch) {
    const next = { ...scope.value, ...patch }
    if (patch.mode === 'OPEN_CHAT') next.knowledgeBaseIds = []
    scope.value = reconcileScope(next, documents.value)
  }
  return { sessions, currentId, status, error, knowledgeBases, documents, scope, active, messages, load, reset, select, remove, send, retry, updateScope, cancel: () => { sequence++ } }
}
