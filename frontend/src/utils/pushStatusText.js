export function isDeviceSubscriptionUnknown({ hasRegistration, isIos, permission }) {
  return permission === 'granted' && (!hasRegistration || isIos)
}

export function pushStatusText(state) {
  if (!state.supported) return '当前浏览器不支持'
  if (state.needsStandalone) return '添加到主屏幕后可开启'
  if (state.permission === 'denied') return '系统权限已关闭'
  if (state.configKnown && !state.configured) return '服务端未配置'
  if (state.loadError) return state.loadError
  if (state.statusUnknown) return '当前设备通知状态暂时无法确认'
  return state.subscribed ? '已开启' : '未开启'
}
