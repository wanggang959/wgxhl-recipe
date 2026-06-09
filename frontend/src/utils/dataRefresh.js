export const DATA_SCOPE = {
  recipes: 'recipes',
  favorites: 'favorites',
  wanted: 'wanted',
  todos: 'todos',
  base: 'base',
  users: 'users',
}

export const DATA_REFRESH_EVENT = 'app:data-refresh'

const dataVersions = {}

function normalizeScopes(scopes) {
  return Array.isArray(scopes) ? scopes.filter(Boolean) : [scopes].filter(Boolean)
}

export function markDataChanged(scopes) {
  const normalizedScopes = normalizeScopes(scopes)
  if (normalizedScopes.length === 0) return

  bumpDataVersions(normalizedScopes)

  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(DATA_REFRESH_EVENT, {
      detail: { scopes: normalizedScopes },
    }))
  }
}

export function markDataStale(scopes) {
  const normalizedScopes = normalizeScopes(scopes)
  if (normalizedScopes.length === 0) return

  bumpDataVersions(normalizedScopes)
}

function bumpDataVersions(normalizedScopes) {
  normalizedScopes.forEach((scope) => {
    dataVersions[scope] = (dataVersions[scope] || 0) + 1
  })
}

export function rememberDataVersions(scopes, target) {
  normalizeScopes(scopes).forEach((scope) => {
    target[scope] = dataVersions[scope] || 0
  })
}

export function hasDataChanged(scopes, target) {
  return normalizeScopes(scopes).some((scope) => (dataVersions[scope] || 0) !== (target[scope] || 0))
}

export function hasMatchingScope(event, scopes) {
  const changedScopes = normalizeScopes(event?.detail?.scopes)
  const watchedScopes = normalizeScopes(scopes)
  return changedScopes.some((scope) => watchedScopes.includes(scope))
}
