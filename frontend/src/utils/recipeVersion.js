export const DEFAULT_RECIPE_VERSION = '1.0'

const VERSION_PATTERN = /^\d{1,2}\.\d{1,2}$/

export function normalizeRecipeVersion(value) {
  const text = String(value ?? '').trim()
  return text || DEFAULT_RECIPE_VERSION
}

export function isValidRecipeVersion(value) {
  return VERSION_PATTERN.test(normalizeRecipeVersion(value))
}

export function formatRecipeVersionLabel(value) {
  return `v${normalizeRecipeVersion(value)}`
}
