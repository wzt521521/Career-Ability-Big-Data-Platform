import request from '../utils/request.js'

const dataOf = (response) => response.data

// ---- 数据源 ----
export const listSources = () => request.get('/collect/source').then(dataOf)
export const getSource = (id) => request.get(`/collect/source/${id}`).then(dataOf)
export const createSource = (data) => request.post('/collect/source', data).then(dataOf)
export const updateSource = (id, data) => request.put(`/collect/source/${id}`, data).then(dataOf)
export const deleteSource = (id) => request.delete(`/collect/source/${id}`).then(dataOf)

// ---- 采集任务 ----
export const listTasks = () => request.get('/collect/task').then(dataOf)
export const getTask = (id) => request.get(`/collect/task/${id}`).then(dataOf)
export const listTasksBySource = (sourceId) => request.get(`/collect/task/by-source/${sourceId}`).then(dataOf)
export const createTask = (data) => request.post('/collect/task', data).then(dataOf)
export const updateTask = (id, data) => request.put(`/collect/task/${id}`, data).then(dataOf)
export const deleteTask = (id) => request.delete(`/collect/task/${id}`).then(dataOf)
export const runTask = (id) => request.post(`/collect/task/${id}/run`).then(dataOf)
export const pauseTask = (id) => request.post(`/collect/task/${id}/pause`).then(dataOf)
export const resumeTask = (id) => request.post(`/collect/task/${id}/resume`).then(dataOf)
export const listTaskLogs = (id, params) => request.get(`/collect/task/${id}/logs`, { params }).then(dataOf)

// ---- 执行日志 ----
export const listLogs = () => request.get('/collect/log').then(dataOf)
export const getLog = (id) => request.get(`/collect/log/${id}`).then(dataOf)
export const listLogsByTask = (taskId) => request.get(`/collect/log/by-task/${taskId}`).then(dataOf)
