const KEYS = {
  recipes: 'family_recipe_dirty_at',
  favorites: 'family_favorite_dirty_at',
  want: 'family_want_dirty_at',
  todo: 'family_todo_dirty_at',
}

function markDirty(scope) {
  const key = KEYS[scope] || scope
  if (!key) return
  try { wx.setStorageSync(key, Date.now()) } catch (_) {}
}

function version(scope) {
  const key = KEYS[scope] || scope
  try { return Number(wx.getStorageSync(key) || 0) } catch (_) { return 0 }
}

module.exports = { markDirty, version }
