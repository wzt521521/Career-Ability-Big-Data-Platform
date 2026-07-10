import request from '../utils/request.js'

export const getOverview = () => request.get('/stats/overview')
export const getPositionStats = () => request.get('/stats/positions')
export const getSalaryStats = () => request.get('/stats/salary')
export const getSkillStats = () => request.get('/stats/skills')
export const getEducationStats = () => request.get('/stats/education')
export const getCityStats = () => request.get('/stats/city')
export const getCompanyStats = () => request.get('/stats/company')
export const getTrendStats = () => request.get('/stats/trends')
