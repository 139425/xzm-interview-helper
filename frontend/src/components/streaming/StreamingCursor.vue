<template>
  <span class="xzm-cursor" :class="`xzm-cursor--${variant}`" aria-hidden="true"></span>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'content', // 'content' | 'thinking'
    validator: (v) => ['content', 'thinking'].includes(v),
  },
})
</script>

<style scoped>
.xzm-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background-color: currentColor;
  border-radius: 1px;
  animation: xzm-cursor-blink 1.1s ease-in-out infinite;
  user-select: none;
}

.xzm-cursor--content { color: var(--xzm-brand); }
.xzm-cursor--thinking { color: var(--xzm-text-tertiary); }

@keyframes xzm-cursor-blink {
  0%, 100% { opacity: 0.15; }
  50%      { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .xzm-cursor {
    animation: none;
    opacity: 0.7;
  }
}
</style>
