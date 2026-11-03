export function translationIdentity(model, articleId, field) { return JSON.stringify([model?.id || 'none', model?.provider || '', model?.baseUrl || '', model?.model || '', articleId, field]) }
