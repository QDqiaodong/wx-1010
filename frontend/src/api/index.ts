import axios, { type AxiosResponse } from 'axios'
import type { Equipment, Session, SessionEquipment, AdjustRecord, AgeGroupSummary, AgeGroup } from '@/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

const responseData = <T>(response: AxiosResponse<T>) => response.data

request.interceptors.response.use(responseData, error => {
  console.error('API Error:', error)
  throw error
})

export const equipmentApi = {
  getAll: (ageGroup?: string): Promise<Equipment[]> => {
    const params = ageGroup ? { ageGroup } : {}
    return request.get('/equipment', { params })
  },
  getById: (id: number): Promise<Equipment> => request.get(`/equipment/${id}`),
  getByAgeGroup: (ageGroup: string): Promise<Equipment[]> => request.get(`/equipment/age-group/${ageGroup}`),
  getSummary: (): Promise<AgeGroupSummary[]> => request.get('/equipment/summary'),
  create: (data: Omit<Equipment, 'id'>): Promise<Equipment> => request.post('/equipment', data),
  update: (id: number, data: Partial<Equipment>): Promise<Equipment> => request.put(`/equipment/${id}`, data),
  delete: (id: number): Promise<void> => request.delete(`/equipment/${id}`),
  refreshCache: (): Promise<void> => request.post('/equipment/refresh-cache')
}

export const sessionApi = {
  getAll: (): Promise<Session[]> => request.get('/session'),
  getById: (id: number): Promise<Session> => request.get(`/session/${id}`),
  create: (data: Omit<Session, 'id'>): Promise<Session> => request.post('/session', data),
  update: (id: number, data: Partial<Session>): Promise<Session> => request.put(`/session/${id}`, data),
  delete: (id: number): Promise<void> => request.delete(`/session/${id}`)
}

export const sessionEquipmentApi = {
  getBySession: (sessionId: number): Promise<SessionEquipment[]> => request.get(`/session/${sessionId}/equipment`),
  bind: (sessionId: number, equipmentId: number, targetAgeGroup: AgeGroup): Promise<SessionEquipment> =>
    request.post(`/session/${sessionId}/equipment`, { equipmentId, targetAgeGroup }),
  unbind: (sessionId: number, equipmentId: number): Promise<void> =>
    request.delete(`/session/${sessionId}/equipment/${equipmentId}`),
  adjustAgeGroup: (sessionId: number, equipmentId: number, newAgeGroup: AgeGroup, adjustReason: string, operator: string): Promise<void> =>
    request.put(`/session/${sessionId}/equipment/${equipmentId}/adjust`, { newAgeGroup, adjustReason, operator }),
  autoBind: (sessionId: number): Promise<void> => request.post(`/session/${sessionId}/equipment/auto-bind`),
  autoAdjust: (sessionId: number, data: { childRatio?: number; teenRatio?: number; adultRatio?: number; adjustReason?: string; operator: string }): Promise<AdjustRecord[]> =>
    request.put(`/session/${sessionId}/equipment/auto-adjust`, data)
}

export const adjustRecordApi = {
  getAll: (): Promise<AdjustRecord[]> => request.get('/adjust-record'),
  getBySession: (sessionId: number): Promise<AdjustRecord[]> => request.get(`/adjust-record/session/${sessionId}`)
}