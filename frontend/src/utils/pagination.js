export const PAGE_SIZE = 50

// Some views need the complete set to calculate grouped results or populate selectors.
export async function loadAllPages(fetchPage, params = {}) {
  const records = []
  let current = 1
  let lastResponse
  while (true) {
    lastResponse = await fetchPage({ ...params, current, size: PAGE_SIZE })
    const data = lastResponse?.data || {}
    const pageRecords = Array.isArray(data.records) ? data.records : []
    records.push(...pageRecords)
    const total = Number(data.total)
    if (pageRecords.length === 0 || (Number.isFinite(total) && records.length >= total)
        || (!Number.isFinite(total) && pageRecords.length < PAGE_SIZE)) break
    if (current >= 10000) throw new Error('数据量过大，请缩小筛选范围')
    current += 1
  }
  return { ...lastResponse, data: { ...lastResponse?.data, records } }
}
