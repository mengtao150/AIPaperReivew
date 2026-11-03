<template>
  <div class="page-heading"><div><div class="eyebrow">MODEL CONNECTIONS</div><h1>模型设置</h1><p>连接你自己的模型，为检索问答与中文阅读提供支持。</p></div><el-button @click="newConnection"><Icon name="plus" :size="16" />添加连接</el-button></div>
  <div class="settings-layout">
    <section>
      <div class="section-caption">已保存的连接 <span>{{ modelState.profiles.length }}</span></div>
      <div v-if="modelState.error" class="inline-error">{{ modelState.error }} <button class="text-button" @click="refreshModels">重试</button></div>
      <div v-if="!modelState.profiles.length" class="surface empty-state"><div class="empty-icon"><Icon name="connection" :size="25" /></div><h3>连接第一个模型</h3><p>填写 API 密钥并测试连接。<br>保存后，下次打开可继续使用。</p></div>
      <article v-for="profile in modelState.profiles" :key="profile.id" :class="['surface connection-card', { selected: form.id === profile.id }]">
        <div class="connection-title"><span :class="['provider-logo', profile.provider]">{{ providerPresets[profile.provider]?.label[0] || 'M' }}</span><div><h3>{{ profile.name }}</h3><span>{{ providerPresets[profile.provider]?.label }}</span></div><span v-if="profile.id === modelState.activeId" class="pill green"><Icon name="check" :size="11" />正在使用</span></div>
        <div class="connection-model">{{ profile.model }}</div><div class="connection-key">{{ profile.maskedKey }}<span>已加密保存</span></div>
        <div class="connection-foot"><small>上次测试 {{ formatTime(profile.testedAt) }}</small><div><button class="text-button" @click="edit(profile)">编辑</button><button v-if="profile.id !== modelState.activeId" class="text-button" @click="activate(profile)">设为默认</button><button class="text-button delete-button" :aria-label="`删除 ${profile.name}`" @click="remove(profile)"><Icon name="trash" :size="14" /></button></div></div>
      </article>
      <div class="settings-note soft-note"><Icon name="info" :size="16" /><span>配置保存在本机服务端，按当前浏览器工作区区分。清除站点 Cookie 或更换浏览器后，需要重新建立连接。</span></div>
    </section>
    <section class="surface configuration-panel">
      <div class="surface-header"><h2><Icon name="settings" :size="18" />{{ form.id ? '编辑模型连接' : '添加模型连接' }}</h2><span class="pill">自带密钥</span></div>
      <div class="surface-body">
        <el-form @submit.prevent="test(true)">
          <div class="form-field"><label class="field-label" for="connection-provider">模型服务商</label><el-select id="connection-provider" v-model="form.provider" :disabled="!!busy" style="width:100%" @change="changeProvider"><el-option v-for="(preset, key) in providerPresets" :key="key" :label="preset.label" :value="key" /></el-select><p class="field-help">{{ providerPresets[form.provider].note }}</p></div>
          <div class="form-field"><label class="field-label" for="connection-name">连接名称</label><el-input id="connection-name" v-model="form.name" :disabled="!!busy" maxlength="80" placeholder="例如：我的 DeepSeek" /></div>
          <div class="form-field"><label class="field-label" for="connection-url">API 地址 <span>Base URL</span></label><el-input id="connection-url" v-model="form.baseUrl" :disabled="!!busy" placeholder="https://…" /><p class="field-help">填写所选服务的 Chat Completions 基础地址；Coding Plan 使用专用地址。</p></div>
          <div class="form-field"><label class="field-label" for="connection-model">模型 ID</label><el-input id="connection-model" v-model="form.model" :disabled="!!busy" placeholder="填写该服务商提供的模型标识" /><p class="field-help">可编辑为你有访问权限的模型，服务商的模型名称可能更新。</p></div>
          <div class="form-field"><label class="field-label" for="connection-key">API 密钥 <span>{{ form.id ? '留空保留原密钥' : '仅用于你的模型请求' }}</span></label><el-input id="connection-key" v-model="form.apiKey" :disabled="!!busy" type="password" show-password autocomplete="new-password" :placeholder="form.id ? '已保存密钥，输入新值以替换' : '粘贴你的 API 密钥'" /><p class="field-help">密钥在服务端加密保存，页面不回显已保存的密钥。</p></div>
          <div v-if="failure" class="inline-error" role="alert"><strong>连接未通过</strong><p style="margin:6px 0 0">{{ failure }}</p><small v-if="failureDetails" style="display:block;margin-top:8px">{{ providerPresets[form.provider].label }} · {{ form.model }}<template v-if="failureDetails.upstreamStatus"> · 上游 HTTP {{ failureDetails.upstreamStatus }}</template> · {{ failureDetails.code }}<template v-if="failureDetails.providerCode"> · 服务商错误码 {{ failureDetails.providerCode }}</template></small></div>
          <div v-if="testResult" class="test-success" role="status"><Icon name="check" :size="17" /><div><strong>连接测试成功</strong><small>模型已返回有效响应 · {{ testResult.latencyMs }} ms{{ testResult.saved ? ' · 配置已保存' : '' }}</small></div></div>
          <div class="settings-actions"><el-button :loading="busy === 'test'" :disabled="!!busy" @click="test(false)"><Icon v-if="!busy" name="connection" :size="15" />测试连接</el-button><el-button type="primary" :loading="busy === 'save'" :disabled="!!busy" native-type="submit">测试并保存<Icon name="arrow" :size="15" /></el-button></div>
          <p class="field-help settings-cost">连接测试会向所选服务发送一次简短请求，可能产生少量费用。修改后的配置通过测试后才会覆盖原配置。</p>
        </el-form>
      </div>
    </section>
  </div>
</template>
<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '../components/WorkbenchIcon.vue'
import api from '../api'
import { modelState, refreshModels, providerPresets } from '../lib/models'
import { errorMessage } from '../lib/search'
const form = reactive({ id: null, name: '我的 DeepSeek', provider: 'deepseek', baseUrl: providerPresets.deepseek.baseUrl, model: providerPresets.deepseek.model, apiKey: '' })
const busy = ref(''), failure = ref(''), failureDetails = ref(null), testResult = ref(null)
watch(form, () => { if (!busy.value) { failure.value = ''; failureDetails.value = null; testResult.value = null } })
function newConnection() { if (busy.value) return; Object.assign(form, { id: null, provider: 'deepseek', name: '我的 DeepSeek', ...providerPresets.deepseek, apiKey: '' }); failure.value = ''; testResult.value = null }
function changeProvider() { const p = providerPresets[form.provider]; form.baseUrl = p.baseUrl; form.model = p.model; if (!form.id) form.name = p.label }
function edit(profile) { if (busy.value) return; Object.assign(form, { ...profile, apiKey: '' }); testResult.value = null; failure.value = '' }
async function test(save) {
  if (busy.value) return
  if (!form.name.trim() || !form.baseUrl.trim() || !form.model.trim() || (!form.id && !form.apiKey.trim())) { failure.value = '请填写连接名称、API 地址、模型 ID 和密钥。'; return }
  busy.value = save ? 'save' : 'test'; failure.value = ''; failureDetails.value = null; testResult.value = null
  try {
    const { data } = await api.post(save ? '/models' : '/models/test', { id: form.id, name: form.name.trim(), provider: form.provider, baseUrl: form.baseUrl.trim(), model: form.model.trim(), apiKey: form.apiKey })
    if (data.ok !== true) { failure.value = data.error || '模型连接未通过，请检查配置后重试'; failureDetails.value = data.code ? data : null; return }
    if (save) { form.id = data.profile.id; form.apiKey = ''; await refreshModels(); ElMessage.success('连接已测试并保存') }
    testResult.value = { ...data, saved: save }
  } catch (error) { failure.value = errorMessage(error, '连接失败，请检查服务地址、模型 ID 和密钥'); failureDetails.value = error.response?.data?.code ? error.response.data : null }
  finally { busy.value = '' }
}
async function activate(profile) { try { await api.put(`/models/${profile.id}/active`); await refreshModels(); ElMessage.success(`已切换至 ${profile.name}`) } catch (error) { ElMessage.error(errorMessage(error)) } }
async function remove(profile) {
  try { await ElMessageBox.confirm(`删除“${profile.name}”及其已保存密钥？`, '删除模型连接', { confirmButtonText: '删除', cancelButtonText: '保留', type: 'warning' }); await api.delete(`/models/${profile.id}`); if (form.id === profile.id) newConnection(); await refreshModels() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error)) }
}
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—' }
</script>
<style scoped>
.settings-layout{display:grid;grid-template-columns:minmax(260px,.85fr) minmax(380px,1.15fr);gap:26px;max-width:1100px}.section-caption{font-size:11px;color:#9299ab;margin:3px 0 14px;display:flex;gap:8px}.section-caption span{color:#b2adc9}.connection-card{padding:21px;margin-bottom:13px;transition:border-color .2s}.connection-card.selected{border-color:#bbb2f2}.connection-title{display:flex;gap:10px;align-items:center}.provider-logo{width:36px;height:36px;border-radius:10px;background:#eaf1ff;color:#577cd3;display:grid;place-items:center;font-weight:600;font-size:18px}.provider-logo.bigmodel,.provider-logo.zai{background:#f0ecff;color:#8772d4}.connection-title h3{margin:0 0 4px;font-size:13px;font-weight:600}.connection-title>div>span{font-size:10px;color:#929bad}.connection-title>.pill{margin-left:auto}.connection-model{font-size:12px;color:#66718a;margin:22px 0 12px;font-family:Consolas,monospace}.connection-key{font-size:11px;letter-spacing:2px;color:#8991a5}.connection-key span{letter-spacing:0;font-size:9px;color:#a7adbb;margin-left:12px}.connection-foot{margin-top:19px;padding-top:13px;border-top:1px solid #f0f1f6;display:flex;justify-content:space-between;align-items:center;gap:8px}.connection-foot small{font-size:9px;color:#a0a8b9}.connection-foot>div{display:flex;gap:8px}.delete-button{color:#b7acb5}.settings-note{margin:21px 3px}.configuration-panel .surface-body{padding:25px 27px}.settings-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:24px}.settings-cost{margin-top:14px}.test-success{display:flex;gap:9px;padding:13px;background:#eff8f4;border:1px solid #dbeee4;color:#3e9274;border-radius:8px}.test-success strong{font-size:12px;font-weight:500}.test-success small{display:block;font-size:10px;margin-top:5px;color:#6d9e8b}@media(max-width:1050px){.settings-layout{grid-template-columns:1fr}.configuration-panel{grid-row:1}}@media(max-width:760px){.configuration-panel .surface-body{padding:20px}.connection-foot{flex-wrap:wrap}}
</style>
