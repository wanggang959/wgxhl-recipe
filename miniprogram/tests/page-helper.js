function setPath(target, path, value) {
  const parts = String(path).replace(/\[(\d+)\]/g, '.$1').split('.').filter(Boolean)
  let cursor = target
  for (let i = 0; i < parts.length - 1; i += 1) {
    const part = parts[i]
    if (cursor[part] == null) cursor[part] = /^\d+$/.test(parts[i + 1]) ? [] : {}
    cursor = cursor[part]
  }
  cursor[parts[parts.length - 1]] = value
}

function loadPage(modulePath) {
  let definition
  global.Page = value => { definition = value }
  require(modulePath)
  delete global.Page
  return definition
}

function makePage(definition, data = {}) {
  const base = JSON.parse(JSON.stringify(definition.data || {}))
  return {
    ...definition,
    data: { ...base, ...JSON.parse(JSON.stringify(data)) },
    setData(update) {
      for (const [key, value] of Object.entries(update)) setPath(this.data, key, value)
    },
    getTabBar() { return { setData() {} } },
  }
}

function stubStorage() {
  const storage = new Map()
  return {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key),
  }
}

module.exports = { setPath, loadPage, makePage, stubStorage }
