<template>
  <button
    type="button"
    class="xzm-theme-toggle"
    :class="{ 'is-compact': compact }"
    :aria-label="`切换为${nextLabel}主题`"
    :title="`切换为${nextLabel}主题`"
    @click="toggle"
  >
    <span class="xzm-theme-toggle__icon" aria-hidden="true">
      <svg
        v-if="resolvedTheme === 'dark'"
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
      </svg>
      <svg
        v-else
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
    </span>
    <span v-if="!compact" class="xzm-theme-toggle__label">
      {{ resolvedTheme === 'dark' ? '深色' : '浅色' }}
    </span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { useTheme } from '../../composables/useTheme'

defineProps({
  compact: { type: Boolean, default: false },
})

const { resolvedTheme, toggle } = useTheme()

const nextLabel = computed(() => (resolvedTheme.value === 'dark' ? '浅色' : '深色'))
</script>

<style scoped>
.xzm-theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--xzm-radius-md);
  background-color: transparent;
  color: var(--xzm-text-secondary);
  font-family: inherit;
  font-size: var(--xzm-fs-xs);
  font-weight: var(--xzm-fw-medium);
  cursor: pointer;
  transition:
    color var(--xzm-duration-fast) var(--xzm-ease-out),
    background-color var(--xzm-duration-fast) var(--xzm-ease-out),
    border-color var(--xzm-duration-fast) var(--xzm-ease-out);
}

.xzm-theme-toggle:hover {
  background-color: var(--xzm-hover-bg);
  color: var(--xzm-text-primary);
  border-color: transparent;
}

.xzm-theme-toggle:focus-visible {
  outline: 2px solid var(--xzm-focus-ring);
  outline-offset: 2px;
}

.xzm-theme-toggle.is-compact {
  width: 36px;
  padding: 0;
  justify-content: center;
}

.xzm-theme-toggle__icon {
  display: inline-flex;
  align-items: center;
  color: var(--xzm-brand-300);
}

[data-theme="light"] .xzm-theme-toggle__icon { color: var(--xzm-warning); }

.xzm-theme-toggle__label {
  letter-spacing: 0.02em;
}
</style>
