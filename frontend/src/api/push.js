import { post } from './request'

export function getPushStatus(endpoint = '') {
  return post('/push/status', endpoint ? { endpoint } : {})
}

export function subscribePush(payload) {
  return post('/push/subscribe', payload)
}

export function unsubscribePush(payload = {}) {
  return post('/push/unsubscribe', payload)
}
