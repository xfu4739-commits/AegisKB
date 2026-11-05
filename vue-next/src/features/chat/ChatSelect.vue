<script setup>
defineProps({ id: { type: String, required: true }, value: { type: String, default: '' }, options: { type: Array, default: () => [] }, placeholder: { type: String, default: '' } })
defineEmits(['change'])
</script>

<template>
  <select :id="id" class="chat-next-select" :value="value" @change="$emit('change', $event.target.value)">
    <option v-if="placeholder" value="" disabled>{{ placeholder }}</option>
    <option v-for="option in options" :key="option.value" :value="option.value" :disabled="option.disabled">{{ option.label }}</option>
  </select>
</template>

<style scoped>
.chat-next-select { width: 100%; min-width: 0; min-height: 36px; padding: 6px 28px 6px 10px; color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius-small); font-family: var(--font-sans); font-size: var(--text-small); cursor: pointer; }
</style>
