const KEY = 'family_assistant_session'
function get() { return wx.getStorageSync(KEY) || null }
function set(user) { wx.setStorageSync(KEY, user) }
function clear() { wx.removeStorageSync(KEY) }
module.exports = { get, set, clear }
