import { computed, ref } from 'vue'
import { chatApi } from './chatApi.js'
import { reconcileScope } from './previewState.js'

const blankScope = () => ({ mode: 'OPEN_CHAT', knowledgeBaseIds: [], documentId: '' })
function displayTime(value) { const date = new Date(value); return value && !Number.isNaN(date.getTime()) ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '' }
function savedScope(data) { return data.knowledgeBaseSelectionMode === 'SELECTED' ? { mode: data.chatMode || 'AUTO_DOCUMENT', knowledgeBaseIds: (data.selectedKnowledgeBaseIds || []).map(String), documentId: data.selectedDocumentId ? String(data.selectedDocumentId) : '' } : blankScope() }
function sessionTitle(item) { return item.latestUserMessage || item.exchanges?.at(-1)?.question || '新的对话' }
function sessionView(item) {
  const date = new Date(item.updatedAt)
  const today = new Date()
  const yesterday = new Date(); yesterday.setDate(today.getDate() - 1)
  const day = (value) => value.toLocaleDateString('zh-CN')
  return { id: String(item.conversationId), title: sessionTitle(item), group: day(date) === day(today) ? '今天' : day(date) === day(yesterday) ? '昨天' : '更早', messages: [] }
}
function projectMessages(exchanges = [], sessionScope = blankScope()) {
  return exchanges.flatMap((exchange) => [
    { id: `${exchange.exchangeId}-user`, role: 'user', content: exchange.question || '', time: displayTime(exchange.createdAt) },
    { id: `${exchange.exchangeId}-assistant`, role: 'assistant', exchangeId: String(exchange.exchangeId), content: exchange.answer || '', error: exchange.errorMessage || '', references: exchange.references || [], statusText: exchange.statusText || '', retryQuestion: exchange.question || '', retryScope: exchange.knowledgeBaseSelectionMode ? savedScope(exchange) : sessionScope, recommendations: (exchange.recommendations || []).map((item) => typeof item === 'string' ? item : item.question).filter(Boolean), time: displayTime(exchange.createdAt) }
  ])
}

export function useLiveState(api = chatApi) {
  const sessions = ref([]), currentId = ref(''), status = ref('ready'), error = ref('')
  const knowledgeBases = ref([]), documents = ref([]), scope = ref(blankScope()), scopeReady = ref(false), deleting = ref(false)
  const generating = ref(false), stopping = ref(false), stopRequest = ref(false)
  const busy = computed(() => generating.value || stopping.value)
  const active = computed(() => sessions.value.find((item) => item.id === currentId.value))
  const messages = computed(() => active.value?.messages || [])
  let version = 0, bootstrap = 0, disposed = false
  let retryAction = () => load()
  let optionsError = ''
  let turn = null
  async function select(id) {
    if (deleting.value || busy.value || disposed) return
    const token = ++version
    status.value = 'loading'; error.value = optionsError
    retryAction = () => select(id)
    try {
      const data = await api.detail(String(id))
      if (token !== version || disposed) return
      if (String(data?.conversationId) !== String(id) || !Array.isArray(data.exchanges)) throw new Error('会话详情格式不正确。')
      const restoredScope = savedScope(data)
      const item = { ...sessionView(data), messages: projectMessages(data.exchanges, restoredScope) }
      sessions.value = sessions.value.some((entry) => entry.id === item.id) ? sessions.value.map((entry) => entry.id === item.id ? item : entry) : [item, ...sessions.value]
      currentId.value = item.id
      scope.value = restoredScope
      status.value = 'ready'
    } catch (failure) { if (token === version && !disposed) { error.value = [optionsError, failure.message].filter(Boolean).join('；'); status.value = 'error' } }
  }
  async function load() {
    if (deleting.value || busy.value || disposed) return
    const token = ++bootstrap, intent = version
    status.value = 'loading'; error.value = ''; retryAction = () => load()
    const results = await Promise.allSettled([api.list(), api.knowledgeBases(), api.documents()])
    if (token !== bootstrap || disposed) return
    const errors = []
    scopeReady.value = results[1].status === 'fulfilled' && results[2].status === 'fulfilled'
    for (const result of results) if (result.status === 'rejected') errors.push(result.reason.message)
    optionsError = results.slice(1).filter((result) => result.status === 'rejected').map((result) => result.reason.message).join('；')
    if (results[1].status === 'fulfilled') knowledgeBases.value = results[1].value.map((item) => ({ id: String(item.id), name: item.baseName }))
    if (results[2].status === 'fulfilled') documents.value = results[2].value.map((item) => ({ id: String(item.documentId), knowledgeBaseId: String(item.knowledgeBaseId), name: item.documentName }))
    if (results[0].status === 'fulfilled' && version === intent) {
      const old = new Map(sessions.value.map((item) => [item.id, item]))
      sessions.value = results[0].value.map((item) => ({ ...sessionView(item), messages: old.get(String(item.conversationId))?.messages || [] }))
    }
    if (version !== intent) return
    error.value = errors.join('；'); status.value = errors.length ? 'error' : 'ready'
    if (results[0].status === 'fulfilled' && !currentId.value && sessions.value.length) await select(sessions.value[0].id)
  }
  function reset() { if (deleting.value || busy.value || disposed) return; version++; currentId.value = ''; status.value = 'ready'; error.value = optionsError }
  async function remove(id) {
    if (deleting.value || busy.value || disposed) return
    deleting.value = true; version++
    try {
      await api.remove(String(id))
      if (disposed) return
      sessions.value = sessions.value.filter((item) => item.id !== id)
      if (currentId.value === id) currentId.value = ''
      error.value = optionsError; status.value = 'ready'
    } catch (failure) { if (!disposed) { error.value = failure.message; status.value = 'error' } }
    finally { deleting.value = false }
  }
  function updateScope(patch) {
    if (!scopeReady.value || busy.value || deleting.value || disposed) return
    const next = { ...scope.value, ...patch }
    if (patch.mode === 'OPEN_CHAT') next.knowledgeBaseIds = []
    scope.value = reconcileScope(next, documents.value)
  }
  function send(question, frozenScope = null) {
    const text = String(question || '').trim(), selected = frozenScope || scope.value
    if (!text || disposed || busy.value || deleting.value || status.value === 'loading' || !scopeReady.value) return false
    if (selected.knowledgeBaseIds.some((id) => !knowledgeBases.value.some((kb) => kb.id === id))) { error.value = '历史知识库当前不可用，请重新选择回答范围。'; return false }
    if (selected.mode === 'DOCUMENT' && !documents.value.some((doc) => doc.id === selected.documentId && selected.knowledgeBaseIds.includes(doc.knowledgeBaseId))) { error.value = '请选择当前范围内的有效文档。'; return false }
    const id = currentId.value || `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
    const savedScope = { ...selected, knowledgeBaseIds: [...selected.knowledgeBaseIds] }
    const payload = { question: text, conversationId: id, chatMode: selected.mode, selectedDocumentId: selected.mode === 'DOCUMENT' ? selected.documentId : null, knowledgeBaseSelectionMode: selected.knowledgeBaseIds.length ? 'SELECTED' : 'NONE', selectedKnowledgeBaseIds: [...selected.knowledgeBaseIds] }
    if (!currentId.value) sessions.value.unshift({ id, title: text, group: '今天', messages: [] })
    currentId.value = id
    const previousExchanges = new Set(messages.value.map((item) => item.exchangeId).filter(Boolean))
    const token = ++version, stamp = `${token}-${Date.now()}`
    active.value.messages.push({ id: `${stamp}-user`, role: 'user', content: text, time: displayTime(Date.now()) }, { id: `${stamp}-assistant`, role: 'assistant', content: '', references: [], recommendations: [], statusText: '正在生成…', error: '', streaming: true, retryQuestion: text, retryScope: savedScope, time: displayTime(Date.now()) })
    const messageId = `${stamp}-assistant`
    const message = () => sessions.value.find((item) => item.id === id)?.messages.find((item) => item.id === messageId)
    const owned = () => token === version && !disposed
    generating.value = true; error.value = optionsError
    const run = async () => {
      try {
        turn = { id, message, token, handle: api.stream(payload, (event) => {
          if (!owned()) return
          const target = message()
          if (event.type === 'text') target.content += String(event.content || '')
          if (event.type === 'reference') target.references = Array.isArray(event.content) ? event.content : []
          if (event.type === 'recommend') target.recommendations = (Array.isArray(event.content) ? event.content : []).map((item) => typeof item === 'string' ? item : item.question).filter(Boolean)
          if (event.type === 'status' || event.type === 'thinking') target.statusText = String(event.content || '')
          if (event.type === 'error') target.error = String(event.content || '回答生成失败。')
        }) }
        await turn.handle.done
        if (!owned()) return
        message().streaming = false; message().statusText = message().error ? '生成失败' : '回答完成'
        if (message().error) return
        // Archive is the authority for final references and exchange IDs.
        try {
          const data = await api.detail(id)
          if (!owned()) return
          if (String(data?.conversationId) !== id || !Array.isArray(data.exchanges) || !data.exchanges.length) throw new Error('归档尚未就绪。')
          if (previousExchanges.has(String(data.exchanges.at(-1).exchangeId))) throw new Error('本轮归档尚未就绪。')
          active.value.messages = projectMessages(data.exchanges, savedScope)
          active.value.title = sessionTitle(data)
        } catch (failure) { if (owned()) error.value = `回答已保留，历史同步失败：${failure.message}` }
      } catch (failure) {
        if (owned()) { message().error = failure.message || '连接中断，请重试。'; message().statusText = '生成失败'; message().streaming = false }
      } finally {
        if (owned()) { generating.value = false; turn = null }
      }
    }
    run()
    return true
  }
  async function stop() {
    if (!turn || stopRequest.value || disposed) return
    stopping.value = true; stopRequest.value = true; generating.value = false
    version++; turn.handle.controller.abort()
    const target = turn.message()
    if (target) { target.streaming = false; target.statusText = '浏览器已停止接收，等待服务端确认…' }
    try { await api.stop(turn.id); if (!disposed) { if (target) target.statusText = '已停止生成'; stopping.value = false; turn = null; error.value = optionsError } }
    catch (failure) { if (!disposed) error.value = `停止未确认：${failure.message}。请再次点击停止。` }
    finally { stopRequest.value = false }
  }
  function retryMessage(id) {
    const target = messages.value.find((item) => item.id === id)
    const question = target?.retryQuestion || messages.value[messages.value.indexOf(target) - 1]?.content
    return target?.error && question ? send(question, target.retryScope || scope.value) : false
  }
  function cancel() {
    disposed = true; version++; bootstrap++
    if (turn) { turn.handle.controller.abort(); api.stop(turn.id).catch(() => {}) }
  }
  return { sessions, currentId, status, error, knowledgeBases, documents, scope, active, messages, scopeReady, deleting, busy, generating, stopping, stopRequest, load, select, reset, remove, updateScope, send, stop, retryMessage, retry: () => optionsError ? load() : retryAction(), cancel }
}
