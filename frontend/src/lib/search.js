export function newSearchForm() {
  return { topic: '', query: '', dateFrom: null, dateTo: null, dateField: 'dp', types: [], language: '', textAvailability: 'any', sortOrder: 'relevance' }
}
export function searchPayload(form) {
  return { topic: form.topic.trim(), query: form.query.trim(), dateFrom: form.dateFrom || null, dateTo: form.dateTo || null, dateField: form.dateField, articleTypes: form.types.join('|'), language: form.language, textAvailability: form.textAvailability, sortOrder: form.sortOrder }
}
export function canFetchSnapshot(form, snapshot) { return JSON.stringify(searchPayload(form)) === snapshot }
export function errorMessage(error, fallback = '操作失败，请稍后重试') { return error?.response?.data?.error || (error?.code === 'ECONNABORTED' ? '请求超时，请重试' : fallback) }
