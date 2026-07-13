import client from '../utils/request'

const BASE = '/recommend'

export function getRecommendations(params = {}) {
  return client.get(BASE, { params })
}

export function getGapAnalysis(positionId) {
  return client.get(`${BASE}/${positionId}/gap-analysis`)
}
