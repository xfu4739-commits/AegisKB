import { ref, shallowRef } from 'vue'

export function createReadResource(loader) {
  const data = shallowRef(null), error = ref(''), loading = ref(false), readAt = ref('')
  let generation = 0, controller, currentKey, disposed = false
  async function load(key = '') {
    if (disposed) return
    const owner = ++generation
    controller?.abort(); controller = new AbortController()
    if (key !== currentKey) { data.value = null; readAt.value = '' }
    currentKey = key; loading.value = true; error.value = ''
    try {
      const value = await loader(key, controller.signal)
      if (owner === generation && !disposed) { data.value = value; readAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }
    } catch (failure) {
      if (owner === generation && !disposed && failure.name !== 'AbortError') error.value = failure.message || '读取失败，请重试。'
    } finally { if (owner === generation && !disposed) loading.value = false }
  }
  function dispose() { disposed = true; generation++; controller?.abort() }
  return { data, error, loading, readAt, load, dispose }
}
