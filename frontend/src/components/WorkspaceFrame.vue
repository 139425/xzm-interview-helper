<template>
  <div class="workspace-frame">
    <GeminiSidebar :mode="mode" />

    <section
      class="workspace-frame__body"
      :style="{ marginLeft: `${uiStore.sidebarWidth}px` }"
    >
      <header class="workspace-frame__topbar">
        <div class="workspace-frame__identity">
          <button
            v-if="uiStore.isMobile"
            type="button"
            class="workspace-frame__menu"
            :aria-label="uiStore.sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
            @click="uiStore.toggleSidebar"
          >
            <el-icon :size="20">
              <Fold v-if="uiStore.sidebarExpanded" />
              <Expand v-else />
            </el-icon>
          </button>
          <span class="workspace-frame__mark" aria-hidden="true">{{
            mark
          }}</span>
          <div>
            <small>{{ eyebrow }}</small>
            <strong>{{ title }}</strong>
          </div>
        </div>

        <div class="workspace-frame__meta">
          <slot name="status"></slot>
        </div>

        <div class="workspace-frame__actions">
          <slot name="actions"></slot>
          <UserAvatar />
        </div>
      </header>

      <div class="workspace-frame__content">
        <slot></slot>
      </div>
    </section>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { Expand, Fold } from '@element-plus/icons-vue'
import GeminiSidebar from '@/components/GeminiSidebar.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { useUIStore } from '@/stores/ui'

const props = defineProps({
  mode: {
    type: String,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
  eyebrow: {
    type: String,
    default: 'WORKSPACE',
  },
  mark: {
    type: String,
    default: 'IA',
  },
})

const uiStore = useUIStore()

watch(
  () => props.mode,
  (mode) => uiStore.switchMode(mode),
  { immediate: true },
)
</script>

<style scoped>
.workspace-frame {
  min-height: 100vh;
  color: var(--xzm-text-primary);
  background:
    linear-gradient(rgba(11, 107, 100, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(11, 107, 100, 0.025) 1px, transparent 1px),
    radial-gradient(
      circle at 78% 0%,
      rgba(11, 107, 100, 0.1),
      transparent 31rem
    ),
    var(--xzm-surface-0);
  background-size:
    28px 28px,
    28px 28px,
    auto,
    auto;
  font-family: var(--xzm-font-sans);
}

.workspace-frame__body {
  min-width: 0;
  min-height: 100vh;
  transition: margin-left 220ms cubic-bezier(0.2, 0, 0, 1);
}

.workspace-frame__topbar {
  position: sticky;
  top: 0;
  z-index: var(--xzm-z-raised);
  display: grid;
  grid-template-columns: minmax(200px, 1fr) auto minmax(200px, 1fr);
  gap: 20px;
  align-items: center;
  min-height: 64px;
  padding: 8px 24px;
  border-bottom: 1px solid var(--xzm-border-color);
  background: color-mix(in srgb, var(--xzm-surface-elevated) 93%, transparent);
  box-shadow: 0 1px 0 rgba(7, 72, 66, 0.025);
  backdrop-filter: blur(18px) saturate(130%);
}

.workspace-frame__identity,
.workspace-frame__actions,
.workspace-frame__meta {
  display: flex;
  align-items: center;
}

.workspace-frame__identity {
  min-width: 0;
  gap: 11px;
}

.workspace-frame__identity > div {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.workspace-frame__identity small {
  overflow: hidden;
  color: var(--xzm-text-tertiary);
  font-size: 0.61rem;
  font-weight: 750;
  letter-spacing: 0.13em;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-frame__identity strong {
  overflow: hidden;
  color: var(--xzm-text-primary);
  font-size: 0.91rem;
  font-weight: 720;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-frame__mark {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  place-items: center;
  border: 1px solid
    color-mix(in srgb, var(--xzm-brand) 28%, var(--xzm-border-color));
  border-radius: 8px;
  color: var(--xzm-signal-ink);
  background: var(--xzm-signal);
  box-shadow: 0 6px 16px rgba(93, 117, 0, 0.14);
  font: 800 0.66rem/1 var(--xzm-font-data);
}

.workspace-frame__meta {
  justify-content: center;
  color: var(--xzm-text-secondary);
  font-size: 0.73rem;
}

.workspace-frame__actions {
  min-width: 0;
  justify-content: flex-end;
  gap: 10px;
}

.workspace-frame__menu {
  display: inline-grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border: 0;
  border-radius: 10px;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-2);
  cursor: pointer;
}

.workspace-frame__menu:hover {
  color: var(--xzm-brand);
  background: var(--xzm-brand-soft);
}

.workspace-frame__content {
  min-width: 0;
  min-height: calc(100vh - 64px);
}

@media (max-width: 980px) {
  .workspace-frame__topbar {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .workspace-frame__meta {
    display: none;
  }
}

@media (max-width: 768px) {
  .workspace-frame__body {
    margin-left: 0 !important;
  }

  .workspace-frame__topbar {
    min-height: 60px;
    padding: 7px 12px;
  }

  .workspace-frame__mark {
    display: none;
  }

  .workspace-frame__identity {
    gap: 8px;
  }

  .workspace-frame__identity small {
    display: none;
  }

  .workspace-frame__actions {
    gap: 7px;
  }

  .workspace-frame__content {
    min-height: calc(100vh - 60px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace-frame__body {
    transition-duration: 1ms;
  }
}
</style>
