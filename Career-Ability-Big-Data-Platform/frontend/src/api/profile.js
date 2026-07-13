import client from '../utils/request'

const BASE = '/profile'

export function getMyProfile() {
  return client.get(BASE)
}

export function saveMyProfile(data) {
  return client.put(BASE, data)
}
