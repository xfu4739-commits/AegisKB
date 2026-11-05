<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { lineGeometry } from './overviewModel.js'
const props = defineProps({ series: { type: Array, required: true }, labels: { type: Array, required: true }, axis: { type: String, default: 'rate' }, kind: { type: String, default: 'line' }, label: { type: String, required: true } })
const container = ref(null), width = ref(640)
let observer
onMounted(() => { observer = new ResizeObserver(([entry]) => { width.value = Math.max(240, Math.floor(entry.contentRect.width)) }); observer.observe(container.value) })
onBeforeUnmount(() => observer?.disconnect())
const layout = computed(() => {
  const values = props.series.flatMap((s) => s.values).filter((v) => v !== null && Number.isFinite(v))
  const max = Math.max(props.axis === 'confidence' ? 1 : props.axis === 'duration' ? 1 : 100, ...values)
  const min = Math.min(0, ...values), plotWidth = width.value - 54
  return { min, max, plotWidth, height: 170, shapes: props.series.map((s) => lineGeometry(s.values, max, plotWidth, 170, min, max)) }
})
const tick = (v) => props.axis === 'confidence' ? v.toFixed(2) : props.axis === 'duration' ? layout.value.max >= 1000 ? `${Math.round(v / 1000)} s` : `${Math.round(v)} ms` : `${Math.round(v)}%`
const ticks = computed(() => [0, .25, .5, .75, 1].map((p) => ({ y: 170 - p * 170, text: tick(layout.value.min + p * (layout.value.max - layout.value.min)) })))
const dateIndexes = computed(() => {
  const count = props.labels.length, max = width.value < 450 ? 3 : 6
  return [...new Set(Array.from({ length: Math.min(max, count) }, (_, i) => count <= max ? i : Math.round(i * (count - 1) / (max - 1))))]
})
const x = (i) => props.labels.length > 1 ? i * layout.value.plotWidth / (props.labels.length - 1) : layout.value.plotWidth / 2
const barWidth = computed(() => Math.max(2, Math.min(32, layout.value.plotWidth / Math.max(1, props.labels.length) / 3)))
</script>
<template>
  <div ref="container" class="admin-next-overview-chart">
    <div class="admin-next-overview-legend"><span v-for="(series, index) in props.series" :key="series.name"><i :class="{ secondary: index === 1 }"></i>{{ series.name }}</span></div>
    <svg :viewBox="`0 0 ${width} 214`" role="img" :aria-label="`${label}图，详细数值见下方数据表`">
      <g transform="translate(44,12)">
        <g v-for="t in ticks" :key="t.y"><line x1="0" :x2="layout.plotWidth" :y1="t.y" :y2="t.y" class="admin-next-overview-grid-line" /><text x="-8" :y="t.y + 4" text-anchor="end">{{ t.text }}</text></g>
        <g v-for="(shape, seriesIndex) in layout.shapes" :key="seriesIndex" :class="{ 'admin-next-overview-secondary-series': seriesIndex === 1 }" class="admin-next-overview-chart-series">
          <template v-for="(segment, i) in shape.segments" :key="i">
            <template v-if="kind === 'line'"><polyline v-if="segment.length > 1" :points="segment.map((p) => `${p.x},${p.y}`).join(' ')" /><circle v-for="p in segment" :key="p.index" :cx="p.x" :cy="p.y" r="2.5" /></template>
            <rect v-else v-for="p in segment" :key="p.index" :x="(props.labels.length > 1 ? (p.index + .5) * layout.plotWidth / props.labels.length : layout.plotWidth / 2) + (seriesIndex - 1) * barWidth" :y="p.y" :width="barWidth" :height="Math.max(0, 170 - p.y)" />
          </template>
        </g>
        <text v-for="i in dateIndexes" :key="i" :x="kind === 'bar' ? (i + .5) * layout.plotWidth / Math.max(1, props.labels.length) : x(i)" y="198" :text-anchor="kind === 'bar' ? 'middle' : i === 0 ? 'start' : i === props.labels.length - 1 ? 'end' : 'middle'">{{ labels[i] }}</text>
      </g>
    </svg>
  </div>
</template>
