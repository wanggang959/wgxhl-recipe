export function restoreBackup({ restoreBackend, restoreFrontend, restartBackend, checkBackend }) {
  const result = { backend: true, frontend: true, healthy: true }
  if (restoreBackend) result.backend = restoreBackend()
  if (restoreFrontend) result.frontend = restoreFrontend()
  if (restoreBackend && result.backend) {
    result.backend = restartBackend()
    if (result.backend) result.healthy = checkBackend()
  }
  return result
}
