import { post } from './request'

export function getPushStatus() {
  return post('/push/status')
}

export function subscribePush(payload) {
  return post('/push/subscribe', payload)
}

export function unsubscribePush(payload = {}) {
  return post('/push/unsubscribe', payload)
}
