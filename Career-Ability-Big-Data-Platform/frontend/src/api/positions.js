import request from '../utils/request.js'

export const getPositions = (params) => request.get('/positions', { params })
export const getPosition = (id) => request.get(`/positions/${id}`)
export const getSuggestions = (keyword) => request.get('/positions/search/suggest', { params: { keyword } })
