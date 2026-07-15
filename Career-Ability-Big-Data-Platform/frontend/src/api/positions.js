import request from '../utils/request.js'

const dataOf = (response) => response.data

export const getPositions = (params) => request.get('/positions', { params }).then(dataOf)
export const getPosition = (id) => request.get(`/positions/${id}`).then(dataOf)
export const comparePositions = (ids) => request
  .get('/positions/compare', { params: { ids: ids.join(',') } })
  .then(dataOf)
export const getSuggestions = (keyword) => request
  .get('/positions/search/suggest', { params: { keyword } })
  .then(dataOf)
