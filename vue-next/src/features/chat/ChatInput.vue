<script setup>
import { ref } from 'vue'
import { Input } from 'ant-design-vue'

// Sender 1.6 forwards arbitrary attributes to its outer container. Use its
// supported input extension so the real textarea has an accessible name.
defineOptions({ inheritAttrs: false })
const input = ref(null)
function guardComposition(event) {
  if (event.key === 'Enter' && (event.isComposing || event.keyCode === 229)) event.stopImmediatePropagation()
}
defineExpose({
  focus: (options) => input.value?.focus(options),
  blur: () => input.value?.blur()
})
</script>

<template>
  <Input.TextArea ref="input" v-bind="$attrs" aria-label="输入问题" aria-describedby="composer-shortcuts" @keydown.capture="guardComposition" />
  <span id="composer-shortcuts" class="chat-next-sr-only">Enter 发送，Shift + Enter 换行</span>
</template>
