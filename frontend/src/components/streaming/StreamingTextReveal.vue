<template>
  <span
    class="xzm-stream-reveal"
    :class="`xzm-stream-reveal--${mode}`"
    :data-reveal-motion="reducedMotion ? 'off' : 'fade'"
  ><span
    v-for="segment in segments"
    :key="segment.id"
    class="xzm-stream-reveal__phrase"
    v-text="segment.text"
  ></span></span>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { takeRevealPhrase } from '../../utils/streamReveal'

const props = defineProps({
  text: { type: String, default: '' },
  mode: {
    type: String,
    default: 'text',
    validator: (value) => ['text', 'code'].includes(value),
  },
})

const segments = ref([])
const reducedMotion = ref(false)

let observedText = ''
let waitingText = ''
let segmentId = 0
let idleTimer = null
let motionQuery = null

function appendSegment(text) {
  if (!text) return
  segments.value.push({ id: segmentId, text })
  segmentId += 1
}

function flushWaiting(force = false) {
  let next = takeRevealPhrase(waitingText, { mode: props.mode, force })
  while (next) {
    appendSegment(next.phrase)
    waitingText = next.rest
    if (!waitingText) break
    next = takeRevealPhrase(waitingText, { mode: props.mode })
  }
}

function scheduleIdleFlush() {
  if (idleTimer != null) clearTimeout(idleTimer)
  // Long enough to combine slow one-character SSE frames, while keeping the
  // first visible phrase responsive when the model pauses.
  idleTimer = setTimeout(() => {
    idleTimer = null
    flushWaiting(true)
  }, 280)
}

function resetReveal(nextText = '') {
  if (idleTimer != null) clearTimeout(idleTimer)
  idleTimer = null
  segments.value = []
  observedText = ''
  waitingText = ''
  segmentId = 0
  if (nextText) updateText(nextText)
}

function updateText(nextValue) {
  const nextText = String(nextValue || '')

  // A stream reset, retry, or parser correction is not an append. Rebuild the
  // visual buffer so no stale characters survive into the new answer.
  if (!nextText.startsWith(observedText)) {
    resetReveal()
  }

  const delta = nextText.slice(observedText.length)
  observedText = nextText
  if (!delta) return

  waitingText += delta
  flushWaiting(false)
  if (waitingText) scheduleIdleFlush()
}

function handleMotionPreference(event) {
  reducedMotion.value = event.matches
  if (event.matches) flushWaiting(true)
}

watch(() => props.text, updateText, { immediate: true, flush: 'sync' })

onMounted(() => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  if (reducedMotion.value) flushWaiting(true)
  motionQuery.addEventListener?.('change', handleMotionPreference)
})

onBeforeUnmount(() => {
  if (idleTimer != null) clearTimeout(idleTimer)
  motionQuery?.removeEventListener?.('change', handleMotionPreference)
})
</script>

<style scoped>
.xzm-stream-reveal,
.xzm-stream-reveal__phrase {
  display: inline;
}

.xzm-stream-reveal__phrase {
  animation: xzm-stream-phrase-reveal 420ms cubic-bezier(0.22, 0.72, 0.2, 1) both;
}

@keyframes xzm-stream-phrase-reveal {
  0% {
    opacity: 0.08;
    filter: blur(1px);
  }
  48% {
    opacity: 0.58;
    filter: blur(0.35px);
  }
  100% {
    opacity: 1;
    filter: blur(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .xzm-stream-reveal__phrase {
    animation: none;
  }
}
</style>
