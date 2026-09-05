<template>
  <div class="toast-stack" aria-live="polite" aria-atomic="true">
    <TransitionGroup name="toast">
      <div v-for="item in items" :key="item.id" class="toast" :class="`toast--${item.tone}`">
        <span>{{ item.tone === 'error' ? '!' : '✓' }}</span>
        <p>{{ item.message }}</p>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
defineProps({ items: { type: Array, default: () => [] } })
</script>
