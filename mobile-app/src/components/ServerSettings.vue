<template>
  <BottomSheet :open="open" title="连接后端服务" eyebrow="SERVER CONNECTION" @close="$emit('close')">
    <form class="server-settings" @submit.prevent="save">
      <div class="server-illustration"><span><AppIcon name="server" /></span><i></i><span><b>X</b></span></div>
      <h3>已自动连接 XZM 服务</h3>
      <p>App 默认使用线上服务，无需配置。仅在更换私有后端时修改下方地址。</p>
      <label class="input-field"><span>API 根地址</span><input v-model.trim="draft" type="url" inputmode="url" autocomplete="url" placeholder="http://120.48.47.80:8104/xzm" /></label>
      <div class="server-hints"><span>本机模拟器</span><button type="button" @click="draft = 'http://10.0.2.2:8104/xzm'">使用 10.0.2.2</button></div>
      <p v-if="error" class="form-error">{{ error }}</p>
      <button class="primary-action" type="submit" :disabled="testing"><span v-if="testing" class="mini-spinner"></span>{{ testing ? '正在测试连接…' : '测试并保存' }}</button>
      <small class="settings-note">默认地址：http://120.48.47.80:8104/xzm</small>
    </form>
  </BottomSheet>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getApiUrl, testApiUrl } from '@/lib/api'
import AppIcon from './AppIcon.vue'
import BottomSheet from './BottomSheet.vue'

const props = defineProps({ open: Boolean })
const emit = defineEmits(['close', 'saved'])
const draft = ref('')
const testing = ref(false)
const error = ref('')

watch(() => props.open, (open) => {
  if (open) { draft.value = getApiUrl() || ''; error.value = '' }
}, { immediate: true })

async function save() {
  testing.value = true; error.value = ''
  try { const value = await testApiUrl(draft.value); emit('saved', value) }
  catch (requestError) { error.value = requestError.message }
  finally { testing.value = false }
}
</script>
