<script setup>
import { computed } from 'vue'
import { Button } from 'ant-design-vue'
import { lineGeometry } from './overviewModel.js'
const props = defineProps({ metrics: { type: Array, required: true }, prefix: { type: String, default: 'overview' } })
defineEmits(['inspect'])
const shapes = computed(() => props.metrics.map((metric) => {
  const values = (metric.history || []).map((p) => p.value), valid = values.filter((v) => v !== null)
  if (!valid.length) return []
  let min = Math.min(...valid), max = Math.max(...valid)
  if (min === max) { min--; max++ }
  return lineGeometry(values, max, 86, 22, min, max).segments
}))
</script>
<template>
  <div class="admin-next-overview-metrics admin-next-overview-panel">
    <article v-for="(metric, index) in metrics" :key="metric.key" class="admin-next-overview-metric" :data-metric="metric.key">
      <span class="admin-next-overview-metric-label" :title="metric.hint">{{ metric.label }}</span>
      <strong :aria-describedby="metric.hint ? `${prefix}-hint-${index}` : undefined">{{ metric.valueText }}</strong>
      <span v-if="metric.hint" :id="`${prefix}-hint-${index}`" class="chat-next-sr-only">{{ metric.hint }}</span>
      <div v-if="metric.deltaText || metric.history?.length" class="admin-next-overview-metric-bottom">
        <span v-if="metric.deltaText" class="admin-next-overview-delta" :class="metric.deltaTone">{{ metric.deltaText }}</span>
        <Button v-if="metric.history?.length" type="text" class="admin-next-overview-spark-button" :aria-label="`查看${metric.label}逐日数据`" @click="$emit('inspect', metric, $event.currentTarget)">
          <svg viewBox="0 0 90 28" aria-hidden="true"><g transform="translate(2,3)"><template v-for="(segment, i) in shapes[index]" :key="i"><polyline v-if="segment.length > 1" :points="segment.map((p) => `${p.x},${p.y}`).join(' ')" /><circle v-else :cx="segment[0].x" :cy="segment[0].y" r="2" /></template></g></svg>
        </Button>
      </div>
    </article>
  </div>
</template>
