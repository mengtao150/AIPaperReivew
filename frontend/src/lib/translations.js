import { reactive } from 'vue'
import api from '../api'
import { activeModel } from './models'
import { errorMessage } from './search'
import { translationIdentity } from './translationIdentity'
const entries = reactive({}), pending = new Map()
function key(articleId, field) { return translationIdentity(activeModel(), articleId, field) }
export function translationEntry(articleId, field) { const id = key(articleId, field); return entries[id] || (entries[id] = { status: 'idle', text: '', error: '' }) }
export async function loadTranslation(articleId, field, retry = false) {
  const id = key(articleId, field), entry = translationEntry(articleId, field)
  if (!activeModel()) { entry.status = 'unconfigured'; return }
  if (pending.has(id)) return pending.get(id)
  if (entry.status === 'ready' && !retry) return
  entry.status = 'loading'; entry.error = ''
  const request = api.post(`/ai/articles/${articleId}/translation`, { field }).then(({ data }) => { entry.text = data.text; entry.status = data.missing ? 'missing' : 'ready'; entry.model = data.model }).catch(error => { entry.status = 'error'; entry.error = errorMessage(error, '翻译暂时不可用，请重试') }).finally(() => pending.delete(id))
  pending.set(id, request); return request
}
