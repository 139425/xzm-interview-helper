<template>
  <div class="knowledge-page">
    <header class="knowledge-header">
      <router-link to="/chat" class="knowledge-brand"><span>IA</span>个人资料</router-link>
      <nav><router-link to="/recruitment">秋招信息</router-link><router-link to="/applications">投递追踪</router-link></nav>
      <button type="button" @click="fileInput?.click()">＋ 上传资料</button>
      <input ref="fileInput" class="sr-only" type="file" accept=".pdf,.doc,.docx,.docs,.md,.txt" @change="upload">
    </header>

    <main>
      <section class="knowledge-hero">
        <p>PRIVATE RAG · USER ISOLATED</p>
        <h1>只让 AI 读取你明确交给它的资料。</h1>
        <p class="lead">每个账号完全隔离。公共知识只用于技术事实，不会被当成你的公司、项目或实习经历。</p>
      </section>

      <section class="knowledge-grid">
        <form class="context-card" @submit.prevent="createText">
          <header><div><small>当前求职上下文</small><h2>公司与岗位信息</h2></div><span>可随时删除</span></header>
          <label>资料标题<input v-model.trim="draft.title" maxlength="255" placeholder="例如：字节跳动 Java 后端 JD" required></label>
          <label>内容<textarea v-model.trim="draft.content" maxlength="60000" rows="10" placeholder="粘贴岗位职责、技术栈、你想强调的项目范围……" required></textarea></label>
          <footer><span>{{ draft.content.length.toLocaleString() }} / 60,000</span><button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存到个人知识库' }}</button></footer>
        </form>

        <section class="documents-card">
          <header><div><small>已隔离保存</small><h2>我的资料</h2></div><b>{{ documents.length }} / 30</b></header>
          <div v-if="loading" class="state">正在加载…</div>
          <div v-else-if="!documents.length" class="state">还没有资料。上传文件或录入当前岗位信息。</div>
          <article v-for="document in documents" v-else :key="document.id">
            <span>{{ document.sourceType === 'CAREER_CONTEXT' ? 'JD' : fileMark(document.originalFilename) }}</span>
            <div><strong>{{ document.title }}</strong><small>{{ number(document.contentChars) }} 字 · {{ time(document.updatedAt) }}</small></div>
            <button type="button" :aria-label="`删除 ${document.title}`" @click="remove(document)">删除</button>
          </article>
          <button type="button" class="dropzone" @click="fileInput?.click()" @dragover.prevent @drop.prevent="drop">
            <strong>拖拽或点击上传</strong><span>PDF / Word / Markdown / TXT，单份不超过 10MB</span>
          </button>
        </section>
      </section>

      <section class="boundary-note">
        <div><b>01</b><strong>检索</strong><span>只检索当前账号的资料与项目公共技术知识</span></div>
        <div><b>02</b><strong>筛选</strong><span>过滤不相关内容，公共资料不作为你的履历证据</span></div>
        <div><b>03</b><strong>生成</strong><span>回答中以“个人资料：标题”标注实际使用的内容</span></div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { knowledgeApi } from '@/api/career'

const fileInput = ref(null)
const documents = ref([])
const loading = ref(true)
const saving = ref(false)
const draft = reactive({ title: '', content: '' })
async function load() { loading.value = true; try { documents.value = await knowledgeApi.list() } catch { ElMessage.error('个人资料加载失败') } finally { loading.value = false } }
async function createText() { saving.value = true; try { await knowledgeApi.createText({ ...draft, sourceType: 'CAREER_CONTEXT' }); draft.title='';draft.content='';await load();ElMessage.success('已保存，仅当前账号可检索') } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') } finally { saving.value=false } }
async function upload(event) { const file=event.target.files?.[0];event.target.value='';if(file) await uploadFile(file) }
async function drop(event) { const file=event.dataTransfer?.files?.[0];if(file) await uploadFile(file) }
async function uploadFile(file) { saving.value=true;try{await knowledgeApi.upload(file);await load();ElMessage.success('资料已提取并保存，原文件不会长期保留')}catch(error){ElMessage.error(error.response?.data?.message||'上传失败')}finally{saving.value=false} }
async function remove(document) { await ElMessageBox.confirm(`确认删除“${document.title}”？`, '删除资料');await knowledgeApi.remove(document.id);await load();ElMessage.success('已删除') }
function number(value){return Number(value||0).toLocaleString()}
function time(value){return value?new Intl.DateTimeFormat('zh-CN',{month:'numeric',day:'numeric'}).format(new Date(value)):'刚刚'}
function fileMark(filename){const ext=String(filename||'DOC').split('.').pop();return ext.slice(0,4).toUpperCase()}
onMounted(load)
</script>

<style scoped>
.knowledge-page{min-height:100vh;color:#172033;background:#f2f6fa;font-family:"Noto Sans SC","Microsoft YaHei",sans-serif}.knowledge-header{position:sticky;top:0;z-index:10;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;min-height:64px;padding:0 max(24px,calc((100vw - 1180px)/2));border-bottom:1px solid #dfe5ed;background:rgba(255,255,255,.94);backdrop-filter:blur(16px)}.knowledge-brand{display:flex;align-items:center;gap:10px;color:#172033;font-weight:750;text-decoration:none}.knowledge-brand span{display:grid;width:30px;height:30px;place-items:center;border-radius:9px;color:#073246;background:#9ce3ef;font:800 11px Georgia,serif}.knowledge-header nav{display:flex;gap:24px}.knowledge-header nav a{color:#657186;font-size:13px;text-decoration:none}.knowledge-header>button,.context-card footer button{justify-self:end;height:36px;padding:0 15px;border:1px solid #235fe3;border-radius:8px;color:#fff;background:#235fe3;font:inherit;font-size:12px;font-weight:700;cursor:pointer}.knowledge-page main{width:min(1180px,calc(100% - 40px));margin:0 auto 60px}.knowledge-hero{max-width:800px;padding:70px 0 42px}.knowledge-hero>p:first-child,.context-card small,.documents-card small{margin:0 0 13px;color:#235fe3;font-size:10px;font-weight:800;letter-spacing:.16em}.knowledge-hero h1{margin:0;font:700 clamp(30px,5vw,52px)/1.15 Georgia,"Songti SC",serif;letter-spacing:-.02em}.knowledge-hero .lead{max-width:670px;margin:20px 0 0;color:#697589;font-size:14px;line-height:1.8}.knowledge-grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:16px}.context-card,.documents-card{border:1px solid #dde4ed;border-radius:14px;background:#fff;box-shadow:0 8px 28px rgba(25,42,69,.05)}.context-card{padding:22px}.context-card header,.documents-card>header{display:flex;justify-content:space-between}.context-card h2,.documents-card h2{margin:0;font-size:19px}.context-card header>span{color:#8994a5;font-size:10px}.context-card label{display:grid;gap:7px;margin-top:17px;color:#657186;font-size:11px}.context-card input,.context-card textarea{width:100%;border:1px solid #d9e0e9;border-radius:8px;color:#172033;background:#fbfcfe;font:inherit}.context-card input{height:40px;padding:0 11px}.context-card textarea{padding:11px;resize:vertical;line-height:1.7}.context-card footer{display:flex;align-items:center;gap:12px;margin-top:16px}.context-card footer span{flex:1;color:#98a1af;font-size:10px}.documents-card{padding:22px}.documents-card>header b{color:#768295;font-size:11px}.documents-card article{display:grid;grid-template-columns:42px 1fr auto;gap:12px;align-items:center;padding:13px 0;border-bottom:1px solid #edf0f4}.documents-card article>span{display:grid;width:42px;height:42px;place-items:center;border-radius:8px;color:#235fe3;background:#edf3ff;font-size:9px;font-weight:800}.documents-card article div{display:grid;min-width:0;gap:5px}.documents-card article strong{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.documents-card article small{margin:0;color:#8c96a5;font-size:9px;letter-spacing:0}.documents-card article button{border:0;color:#a5554e;background:transparent;font:inherit;font-size:10px;cursor:pointer}.state{padding:38px 0;color:#929baa;text-align:center;font-size:11px}.dropzone{display:grid;width:100%;gap:6px;margin-top:16px;padding:20px;border:1px dashed #becae0;border-radius:10px;color:#56647a;background:#f8faff;font:inherit;cursor:pointer}.dropzone strong{font-size:11px}.dropzone span{color:#929cab;font-size:9px}.boundary-note{display:grid;grid-template-columns:repeat(3,1fr);margin-top:16px;border:1px solid #dce3ec;border-radius:14px;background:#eaf0f7}.boundary-note div{display:grid;grid-template-columns:auto 1fr;gap:3px 11px;padding:20px;border-right:1px solid #d7dfe8}.boundary-note div:last-child{border:0}.boundary-note b{grid-row:1/3;color:#235fe3;font:700 22px/1 Georgia,serif}.boundary-note strong{font-size:12px}.boundary-note span{color:#748095;font-size:10px;line-height:1.5}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}@media(max-width:760px){.knowledge-header{grid-template-columns:1fr auto;padding:0 14px}.knowledge-header nav{display:none}.knowledge-page main{width:calc(100% - 24px)}.knowledge-hero{padding:42px 0 30px}.knowledge-grid{grid-template-columns:1fr}.boundary-note{grid-template-columns:1fr}.boundary-note div{border-right:0;border-bottom:1px solid #d7dfe8}}
</style>
