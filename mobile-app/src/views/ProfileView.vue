<template>
  <main class="page scroll-page profile-page">
    <AppHeader title="我的" eyebrow="ACCOUNT & SETTINGS"><button class="icon-button" type="button" @click="$emit('open-settings')"><AppIcon name="settings" /></button></AppHeader>
    <section class="profile-card">
      <div class="profile-avatar">{{ initials(user?.username, 'X') }}<i></i></div>
      <div><small>{{ user?.userType || '普通用户' }}</small><h2>{{ user?.username || 'XZM 用户' }}</h2><p>保持节奏，Offer 会在路上。</p></div>
    </section>

    <section class="profile-focus">
      <small>TODAY'S FOCUS</small><h3>每一次准备，都算数。</h3><p>用待办安排节奏，用投递追踪结果。</p>
      <div><button type="button" @click="$emit('navigate', 'schedule')"><AppIcon name="calendar" /><span><b>查看待办</b><small>下一场安排</small></span><AppIcon name="arrow" /></button><button type="button" @click="$emit('navigate', 'tracker')"><AppIcon name="pipeline" /><span><b>推进投递</b><small>更新求职进度</small></span><AppIcon name="arrow" /></button></div>
    </section>

    <section class="settings-list">
      <h3>应用设置</h3>
      <button type="button" @click="$emit('open-settings')"><span class="settings-icon"><AppIcon name="server" /></span><span><b>后端服务</b><small>{{ serverLabel }}</small></span><AppIcon name="arrow" /></button>
      <button type="button" @click="$emit('navigate', 'chat')"><span class="settings-icon settings-icon--purple"><AppIcon name="chat" /></span><span><b>AI 对话</b><small>DeepSeek · 专业求职模式</small></span><AppIcon name="arrow" /></button>
      <button class="logout-row" type="button" @click="$emit('logout')"><span class="settings-icon settings-icon--red"><AppIcon name="logout" /></span><span><b>退出登录</b><small>本机登录凭证会被清除</small></span><AppIcon name="arrow" /></button>
    </section>
    <footer class="app-version"><span class="brand-mark brand-mark--small">X</span><p>XZM 面试助手<br><small>Android · Version 1.1.0</small></p></footer>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { getApiUrl } from '@/lib/api'
import { initials } from '@/lib/format'
import AppHeader from '@/components/AppHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
const props = defineProps({ user: Object })
defineEmits(['open-settings', 'logout', 'navigate'])
const serverLabel = computed(() => { try { return new URL(getApiUrl()).host || '未配置' } catch { return '未配置' } })
</script>
