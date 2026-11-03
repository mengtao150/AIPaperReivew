import { reactive } from 'vue'
import api from '../api'
export const modelState = reactive({ profiles: [], activeId: '', loaded: false, error: '' })
let pending
export async function refreshModels() {
  if (pending) return pending
  pending = api.get('/models', { timeout: 7000 }).then(({ data }) => {
    modelState.profiles = data.profiles; modelState.activeId = data.activeId; modelState.loaded = true; modelState.error = ''
  }).catch(() => { modelState.error = '暂时无法读取模型设置'; modelState.loaded = true }).finally(() => { pending = null })
  return pending
}
export function activeModel() { return modelState.profiles.find(p => p.id === modelState.activeId) }
export const providerPresets = {
  deepseek: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-flash', note: '使用 DeepSeek 开放平台 API 密钥。模型 ID 可按账号权限修改。' },
  bigmodel: { label: '智谱 GLM 标准 API · 国内', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4.7-flash', note: '使用智谱通用 API 的密钥和额度。ZCode / GLM Coding 编程套餐仅适用于编程场景。' },
  'bigmodel-coding': { label: '智谱 GLM Coding Plan · 国内', baseUrl: 'https://open.bigmodel.cn/api/coding/paas/v4', model: 'glm-5.3-flash', note: '使用 Coding Plan 专用地址与个人/团队套餐 Key。标准 API Key 仍可能按量计费。模型以套餐权限为准，不自动回退到通用 API；服务商套餐规则仍适用。' },
  zai: { label: 'Z.ai 标准 API · 国际', baseUrl: 'https://api.z.ai/api/paas/v4', model: 'glm-4.7', note: '使用 Z.ai 通用 API；与国内智谱账号、Coding 套餐额度分别管理。' },
  'zai-coding': { label: 'Z.ai GLM Coding Plan · 国际', baseUrl: 'https://api.z.ai/api/coding/paas/v4', model: 'glm-5.3-flash', note: '使用 Z.ai Coding Plan 专用地址；与国内智谱密钥分别管理。平台不会自动切换到通用 API。服务商对套餐用途的要求仍适用。' },
  custom: { label: '自定义兼容接口', baseUrl: '', model: '', note: '支持公网 HTTPS Chat Completions 接口。密钥将发送至你填写的服务地址，请确认该地址属于你的服务商。' }
}
