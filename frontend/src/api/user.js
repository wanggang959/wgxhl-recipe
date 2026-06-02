import { post } from './request'

export function login(payload) {
  return post('/user/login', payload)
}

export function pageUser(params) {
  return post('/user/page', params)
}
