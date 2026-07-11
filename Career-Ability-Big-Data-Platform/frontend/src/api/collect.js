import request from '../utils/request.js'

// ---- 数据源 ----
export const listSources = () => request.get('/collect/source')
export const getSource = (id) => request.get(`/collect/source/${id}`)
export const createSource = (data) => request.post('/collect/source', data)
export const updateSource = (id, data) => request.put(`/collect/source/${id}`, data)
export const deleteSource = (id) => request.delete(`/collect/source/${id}`)

// ---- 采集任务 ----
export const listTasks = () => request.get('/collect/task')
export const getTask = (id) => request.get(`/collect/task/${id}`)
export const listTasksBySource = (sourceId) => request.get(`/collect/task/by-source/${sourceId}`)
export const createTask = (data) => request.post('/collect/task', data)
export const updateTask = (id, data) => request.put(`/collect/task/${id}`, data)
export const deleteTask = (id) => request.delete(`/collect/task/${id}`)

// ---- 执行日志 ----
export const listLogs = () => request.get('/collect/log')
export const getLog = (id) => request.get(`/collect/log/${id}`)
export const listLogsByTask = (taskId) => request.get(`/collect/log/by-task/${taskId}`)
