<template>
  <header class="app-header">
    <div>
      <p>{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
    </div>
    <slot />
  </header>
</template>

<script setup>
defineProps({ title: String, eyebrow: String })
</script>
