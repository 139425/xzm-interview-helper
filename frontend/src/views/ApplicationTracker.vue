<template>
  <div class="career-page">
    <header class="career-header">
      <router-link to="/chat" class="career-brand"><span>IA</span>投递追踪</router-link>
      <nav><router-link to="/recruitment">秋招信息</router-link><router-link to="/knowledge">个人资料</router-link></nav>
      <button type="button" class="primary" @click="openCreate">＋ 手动录入</button>
    </header>

    <main class="career-main">
      <section class="career-intro">
        <div><p>CAREER PIPELINE</p><h1>每一次投递，都有下一步。</h1></div>
        <dl><div><dt>{{ total }}</dt><dd>全部记录</dd></div><div><dt>{{ summary.upcomingReminders || 0 }}</dt><dd>7 天内提醒</dd></div><div><dt>{{ summary.OFFER || 0 }}</dt><dd>Offer</dd></div></dl>
      </section>

      <div class="career-toolbar">
        <label><span class="sr-only">搜索</span><input v-model.trim="keyword" placeholder="搜索公司、岗位或备注" @keyup.enter="load"></label>
        <button type="button" @click="load">搜索</button>
        <button v-if="keyword" type="button" class="quiet" @click="keyword='';load()">清空</button>
      </div>

      <section class="pipeline" aria-label="投递流程">
        <article v-for="column in columns" :key="column.value" class="pipeline-column">
          <header><span>{{ column.label }}</span><b>{{ grouped[column.value]?.length || 0 }}</b></header>
          <div class="pipeline-list">
            <button v-for="item in grouped[column.value]" :key="item.id" type="button" class="application-card" @click="edit(item)">
              <strong>{{ item.company }}</strong>
              <span>{{ item.roleName }}</span>
              <small v-if="item.location">{{ item.location }}</small>
              <time v-if="item.nextActionAt" :datetime="item.nextActionAt">下一步 · {{ dateTime(item.nextActionAt) }}</time>
              <em v-if="item.deadline">截止 {{ item.deadline }}</em>
            </button>
            <p v-if="!grouped[column.value]?.length" class="empty">暂无</p>
          </div>
        </article>
      </section>
    </main>

    <div v-if="dialogOpen" class="dialog-backdrop" @click.self="dialogOpen=false">
      <form class="application-dialog" @submit.prevent="save">
        <header><div><small>{{ form.id ? 'EDIT APPLICATION' : 'NEW APPLICATION' }}</small><h2>{{ form.id ? '编辑投递' : '手动录入' }}</h2></div><button type="button" @click="dialogOpen=false">×</button></header>
        <div class="form-grid">
          <label>公司<input v-model.trim="form.company" maxlength="200" required></label>
          <label>岗位<input v-model.trim="form.roleName" maxlength="300" required></label>
          <label>状态<select v-model="form.status"><option v-for="column in columns" :key="column.value" :value="column.value">{{ column.label }}</option></select></label>
          <label>城市<input v-model.trim="form.location" maxlength="300"></label>
          <label>截止时间<input v-model="form.deadline" type="date"></label>
          <label>下次提醒<input v-model="form.nextActionAt" type="datetime-local"></label>
          <label class="wide">下一步<input v-model.trim="form.nextAction" maxlength="500" placeholder="例如：准备 MySQL 索引题"></label>
          <label class="wide">投递链接<input v-model.trim="form.applyUrl" type="url" placeholder="https://"></label>
          <label class="wide">备注<textarea v-model.trim="form.notes" maxlength="10000" rows="4"></textarea></label>
        </div>
        <footer><button v-if="form.id" type="button" class="danger" @click="remove">删除</button><span></span><button type="button" class="quiet" @click="dialogOpen=false">取消</button><button type="submit" class="primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button></footer>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi } from '@/api/career'

const columns = [
  { value: 'TO_APPLY', label: '待投递' }, { value: 'APPLIED', label: '已投递' },
  { value: 'ASSESSMENT', label: '笔试' }, { value: 'INTERVIEW_1', label: '一面' },
  { value: 'INTERVIEW_FINAL', label: '二面 / 终面' }, { value: 'OFFER', label: 'Offer' },
  { value: 'REJECTED', label: '拒绝' }, { value: 'WITHDRAWN', label: '放弃' },
]
const items = ref([])
const summary = ref({})
const keyword = ref('')
const dialogOpen = ref(false)
const saving = ref(false)
const emptyForm = () => ({ id: null, company: '', roleName: '', status: 'TO_APPLY', location: '', applyUrl: '', sourceUrl: '', deadline: '', nextAction: '', nextActionAt: '', notes: '' })
const form = reactive(emptyForm())
const grouped = computed(() => Object.fromEntries(columns.map((column) => [column.value, items.value.filter((item) => item.status === column.value)])))
const total = computed(() => items.value.length)

async function load() {
  try {
    const data = await applicationApi.list({ keyword: keyword.value })
    items.value = data.items || []
    summary.value = data.summary || {}
  } catch (error) { ElMessage.error(error.response?.data?.message || '投递记录加载失败') }
}
function openCreate() { Object.assign(form, emptyForm()); dialogOpen.value = true }
function edit(item) { Object.assign(form, emptyForm(), item, { deadline: item.deadline || '', nextActionAt: item.nextActionAt?.slice(0, 16) || '' }); dialogOpen.value = true }
async function save() {
  saving.value = true
  try {
    const payload = { ...form, deadline: form.deadline || null, nextActionAt: form.nextActionAt || null }
    if (form.id) await applicationApi.update(form.id, payload); else await applicationApi.create(payload)
    ElMessage.success('投递记录已保存'); dialogOpen.value = false; await load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') } finally { saving.value = false }
}
async function remove() {
  await ElMessageBox.confirm('删除后不可恢复，确认删除这条投递记录？', '删除投递')
  await applicationApi.remove(form.id); dialogOpen.value = false; await load(); ElMessage.success('已删除')
}
function dateTime(value) { return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) }
onMounted(load)
</script>

<style scoped>
.career-page{min-height:100vh;color:#182235;background:#f3f6fa;font-family:"Noto Sans SC","Microsoft YaHei",sans-serif}.career-header{position:sticky;top:0;z-index:10;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;min-height:64px;padding:0 max(24px,calc((100vw - 1540px)/2));border-bottom:1px solid #dfe5ed;background:rgba(255,255,255,.94);backdrop-filter:blur(16px)}.career-brand{display:flex;align-items:center;gap:10px;color:#172033;font-weight:750;text-decoration:none}.career-brand span{display:grid;width:30px;height:30px;place-items:center;border-radius:9px;color:#073246;background:#9ce3ef;font-family:Georgia,serif;font-size:11px}.career-header nav{display:flex;gap:24px}.career-header nav a{color:#657186;font-size:13px;text-decoration:none}.career-header>.primary{justify-self:end}.primary,.career-toolbar button{height:36px;padding:0 15px;border:1px solid #235fe3;border-radius:8px;color:#fff;background:#235fe3;font:inherit;font-size:12px;font-weight:700;cursor:pointer}.career-main{width:min(1540px,calc(100% - 40px));margin:26px auto 48px}.career-intro{display:flex;justify-content:space-between;align-items:end;padding:4px 2px 24px}.career-intro p,.application-dialog header small{margin:0 0 8px;color:#235fe3;font-size:10px;font-weight:800;letter-spacing:.16em}.career-intro h1{margin:0;font:700 clamp(24px,3vw,38px)/1.2 Georgia,"Songti SC",serif}.career-intro dl{display:flex;margin:0}.career-intro dl div{min-width:100px;padding-left:24px;border-left:1px solid #dbe1e9}.career-intro dt{font:700 24px/1 Georgia,serif}.career-intro dd{margin:7px 0 0;color:#7a8597;font-size:10px}.career-toolbar{display:flex;gap:8px;margin-bottom:14px}.career-toolbar label{flex:1;max-width:440px}.career-toolbar input,.form-grid input,.form-grid select,.form-grid textarea{width:100%;border:1px solid #d8dfe9;border-radius:8px;color:#182235;background:#fff;font:inherit}.career-toolbar input{height:38px;padding:0 13px}.career-toolbar .quiet,.application-dialog .quiet{border-color:#d8dfe9;color:#59667a;background:#fff}.pipeline{display:grid;grid-template-columns:repeat(8,minmax(190px,1fr));gap:10px;overflow-x:auto;padding-bottom:12px}.pipeline-column{min-height:550px;border:1px solid #e1e6ed;border-radius:12px;background:#edf1f6}.pipeline-column>header{display:flex;justify-content:space-between;align-items:center;height:48px;padding:0 13px;border-bottom:1px solid #dfe5ec;font-size:12px;font-weight:750}.pipeline-column>header b{display:grid;min-width:22px;height:22px;padding:0 5px;place-items:center;border-radius:999px;color:#667286;background:#fff;font-size:10px}.pipeline-list{display:grid;gap:8px;padding:8px}.application-card{display:grid;gap:6px;padding:13px;border:1px solid #dde3eb;border-radius:9px;color:#172033;background:#fff;text-align:left;box-shadow:0 2px 8px rgba(30,47,72,.04);cursor:pointer}.application-card:hover{border-color:#87aaf4;box-shadow:0 5px 15px rgba(35,95,227,.09)}.application-card strong{font-size:13px}.application-card span{color:#4e5b6e;font-size:11px}.application-card small{color:#8993a2;font-size:9px}.application-card time,.application-card em{font-size:9px;font-style:normal}.application-card time{color:#b06308}.application-card em{color:#8b4a42}.empty{margin:20px 0;color:#a0a9b7;text-align:center;font-size:10px}.dialog-backdrop{position:fixed;inset:0;z-index:50;display:grid;place-items:center;padding:16px;background:rgba(20,29,44,.34);backdrop-filter:blur(4px)}.application-dialog{width:min(680px,100%);max-height:calc(100vh - 32px);overflow:auto;padding:22px;border:1px solid #d9e0e9;border-radius:16px;background:#fff;box-shadow:0 24px 70px rgba(20,33,53,.2)}.application-dialog>header{display:flex;justify-content:space-between}.application-dialog h2{margin:0;font-size:21px}.application-dialog>header button{border:0;background:none;font-size:26px;cursor:pointer}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:22px}.form-grid label{display:grid;gap:7px;color:#677386;font-size:11px}.form-grid input,.form-grid select{height:39px;padding:0 10px}.form-grid textarea{padding:10px;resize:vertical}.form-grid .wide{grid-column:1/-1}.application-dialog footer{display:flex;gap:8px;margin-top:20px}.application-dialog footer span{flex:1}.application-dialog footer button{height:36px;padding:0 15px;border-radius:8px;font:inherit;font-size:12px;cursor:pointer}.danger{border:1px solid #d65e54;color:#b83e37;background:#fff}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@media(max-width:760px){.career-header{grid-template-columns:1fr auto;padding:0 14px}.career-header nav{display:none}.career-main{width:calc(100% - 24px);margin-top:18px}.career-intro{display:block}.career-intro dl{margin-top:22px}.career-intro dl div:first-child{padding-left:0;border-left:0}.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}}
</style>
