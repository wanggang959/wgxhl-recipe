import { post } from './request'

export const pageLedger = (query) => post('/ledger/page', query)
export const ledgerSummary = (month) => post('/ledger/summary', { month })
export const createLedger = (entry) => post('/ledger/create', entry)
export const updateLedger = (entry) => post('/ledger/update', entry)
export const deleteLedger = (id) => post('/ledger/delete', { id })
export const completeWithLedger = (payload) => post('/todo/complete-with-ledger', payload)
