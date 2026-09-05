<template>
  <svg class="app-icon" viewBox="0 0 24 24" aria-hidden="true">
    <g fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="name === 'chat'">
        <path d="M5 18.5 3.5 21l4.3-1.4c1.2.6 2.6.9 4.2.9 5 0 9-3.7 9-8.4S17 3.7 12 3.7s-9 3.7-9 8.4c0 2.5.8 4.7 2 6.4Z"/><path d="M8 12h.01M12 12h.01M16 12h.01"/>
      </template>
      <template v-else-if="name === 'calendar'">
        <rect x="3.5" y="5" width="17" height="15" rx="3"/><path d="M7.5 3v4M16.5 3v4M3.5 9.5h17M8 14l2.3 2.3L16 11"/>
      </template>
      <template v-else-if="name === 'pipeline'">
        <rect x="3" y="4" width="18" height="16" rx="3"/><path d="M8 4V2.7M16 4V2.7M7 9h10M7 13h6M7 17h9"/>
      </template>
      <template v-else-if="name === 'jobs'">
        <path d="M8 7V5.5A2.5 2.5 0 0 1 10.5 3h3A2.5 2.5 0 0 1 16 5.5V7"/><rect x="3" y="7" width="18" height="13" rx="3"/><path d="M3 12c5.7 2.7 12.3 2.7 18 0M10 13.5h4"/>
      </template>
      <template v-else-if="name === 'user'">
        <circle cx="12" cy="8" r="4"/><path d="M4.5 21a7.5 7.5 0 0 1 15 0"/>
      </template>
      <template v-else-if="name === 'settings'">
        <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>
      </template>
      <template v-else-if="name === 'send'">
        <path d="m3 11.5 17-8-6.8 17-2.3-6.9L3 11.5Z"/><path d="m10.9 13.6 4.2-4.3"/>
      </template>
      <template v-else-if="name === 'plus'">
        <path d="M12 5v14M5 12h14"/>
      </template>
      <template v-else-if="name === 'check'">
        <path d="m5 12 4.2 4.2L19 6.5"/>
      </template>
      <template v-else-if="name === 'search'">
        <circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/>
      </template>
      <template v-else-if="name === 'refresh'">
        <path d="M20 7v5h-5M4 17v-5h5"/><path d="M18.2 9A7 7 0 0 0 6.5 6.5L4 9m16 6-2.5 2.5A7 7 0 0 1 5.8 15"/>
      </template>
      <template v-else-if="name === 'close'">
        <path d="m6 6 12 12M18 6 6 18"/>
      </template>
      <template v-else-if="name === 'arrow'">
        <path d="M5 12h14M14 7l5 5-5 5"/>
      </template>
      <template v-else-if="name === 'link'">
        <path d="M10 14a4.5 4.5 0 0 0 6.4.1l2-2a4.5 4.5 0 0 0-6.4-6.4l-1.1 1.1M14 10a4.5 4.5 0 0 0-6.4-.1l-2 2a4.5 4.5 0 0 0 6.4 6.4l1.1-1.1"/>
      </template>
      <template v-else-if="name === 'trash'">
        <path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6M14 11v6"/>
      </template>
      <template v-else-if="name === 'edit'">
        <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z"/><path d="m13.5 6.5 4 4M12 20h8"/>
      </template>
      <template v-else-if="name === 'undo'">
        <path d="M9 7 4 12l5 5M5 12h8a6 6 0 0 1 6 6v1"/>
      </template>
      <template v-else-if="name === 'history'">
        <path d="M4 4v5h5M4.8 9A8 8 0 1 1 4 14"/><path d="M12 7v5l3 2"/>
      </template>
      <template v-else-if="name === 'logout'">
        <path d="M14 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2v-3M10 12h11M18 8l4 4-4 4"/>
      </template>
      <template v-else-if="name === 'server'">
        <rect x="3" y="4" width="18" height="6" rx="2"/><rect x="3" y="14" width="18" height="6" rx="2"/><path d="M7 7h.01M7 17h.01M11 7h6M11 17h6"/>
      </template>
      <template v-else-if="name === 'sparkle'">
        <path d="M12 2.5c.7 4.8 2.8 7 7.5 7.5-4.7.5-6.8 2.7-7.5 7.5-.7-4.8-2.8-7-7.5-7.5 4.7-.5 6.8-2.7 7.5-7.5ZM19 16.5c.3 2 1.2 2.9 3 3-1.8.1-2.7 1-3 3-.3-2-1.2-2.9-3-3 1.8-.1 2.7-1 3-3Z"/>
      </template>
    </g>
  </svg>
</template>

<script setup>
defineProps({ name: { type: String, required: true } })
</script>
