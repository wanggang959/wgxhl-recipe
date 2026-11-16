export function overlapStartPage(loadedPage, deletedCount, pageSize) {
  return deletedCount > 0 ? Math.max(1, loadedPage - Math.floor(deletedCount / pageSize)) : loadedPage + 1
}

export function mergePageRecords(existing, incoming) {
  const result = existing.slice()
  const seen = new Set(existing.map((item) => item.id))
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      seen.add(item.id)
      result.push(item)
    }
  }
  return result
}
