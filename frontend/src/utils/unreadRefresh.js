export function startUnreadRefresh({ refresh, document, serviceWorker, setInterval, clearInterval }) {
  const intervalMs = 60_000
  let timer = null
  let stopped = false

  function stopTimer() {
    if (timer !== null) clearInterval(timer)
    timer = null
  }

  function startTimer() {
    if (timer === null) timer = setInterval(() => {
      if (!document.hidden && !stopped) refresh()
    }, intervalMs)
  }

  function onVisibilityChange() {
    if (document.hidden) {
      stopTimer()
    } else {
      refresh()
      startTimer()
    }
  }

  function onServiceWorkerMessage(event) {
    if (!document.hidden && event.data?.type === 'notification-received') refresh()
  }

  document.addEventListener('visibilitychange', onVisibilityChange)
  serviceWorker?.addEventListener('message', onServiceWorkerMessage)
  if (!document.hidden) {
    refresh()
    startTimer()
  }

  return () => {
    stopped = true
    stopTimer()
    document.removeEventListener('visibilitychange', onVisibilityChange)
    serviceWorker?.removeEventListener('message', onServiceWorkerMessage)
  }
}
