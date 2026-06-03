import { post } from './request'

export function login(payload) {
  return post('/user/login', payload)
}

export function guestLogin() {
  return post('/user/guestLogin')
}

export function previewUserByUsername(payload) {
  return post('/user/preview', payload)
}

export function pageUser(params) {
  return post('/user/page', params)
}

export function createUser(payload) {
  return post('/user/create', payload)
}

export function updateUser(payload) {
  return post('/user/update', payload)
}

export function deleteUser(id) {
  return post('/user/delete', { id })
}

export function setUserStatus(payload) {
  return post('/user/setStatus', payload)
}
