<template>
  <Teleport to="body">
    <Transition name="sheet">
      <div v-if="open" class="sheet-overlay" @click.self="$emit('close')">
        <section class="bottom-sheet" role="dialog" aria-modal="true" :aria-label="title">
          <div class="sheet-handle" aria-hidden="true"></div>
          <header class="sheet-header">
            <div><small>{{ eyebrow }}</small><h2>{{ title }}</h2></div>
            <button type="button" class="icon-button" aria-label="关闭" @click="$emit('close')"><AppIcon name="close" /></button>
          </header>
          <div class="sheet-body"><slot /></div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import AppIcon from './AppIcon.vue'
defineProps({ open: Boolean, title: String, eyebrow: { type: String, default: 'QUICK EDIT' } })
defineEmits(['close'])
</script>
