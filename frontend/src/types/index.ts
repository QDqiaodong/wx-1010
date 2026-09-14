export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT'

export type EquipmentStatus = 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE'

export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ENDED'

export interface Equipment {
  id: number
  equipmentCode: string
  name: string
  frostResistanceSpec: string
  ageGroup: AgeGroup
  category: string
  status: EquipmentStatus
  createTime?: string
  updateTime?: string
}

export interface Session {
  id: number
  sessionCode: string
  sessionName: string
  startTime: string
  endTime: string
  childRatio: number
  teenRatio: number
  adultRatio: number
  status: SessionStatus
}

export interface SessionEquipment {
  id: number
  sessionId: number
  equipmentId: number
  targetAgeGroup: AgeGroup
}

export interface AdjustRecord {
  id: number
  sessionId: number
  equipmentId: number
  oldAgeGroup: AgeGroup
  newAgeGroup: AgeGroup
  adjustReason?: string
  adjustOperator: string
  adjustTime?: string
}

export interface AgeGroupSummary {
  ageGroup: AgeGroup
  ageGroupLabel: string
  ageRange: string
  totalCount: number
  equipments: Equipment[]
}

export const AGE_GROUP_MAP: Record<AgeGroup, { label: string; ageRange: string }> = {
  CHILD: { label: '幼童', ageRange: '3-7岁' },
  TEEN: { label: '青少年', ageRange: '8-17岁' },
  ADULT: { label: '成人', ageRange: '18岁以上' }
}

export const EQUIPMENT_STATUS_MAP: Record<EquipmentStatus, string> = {
  AVAILABLE: '可用',
  IN_USE: '使用中',
  MAINTENANCE: '维护中'
}

export const SESSION_STATUS_MAP: Record<SessionStatus, string> = {
  SCHEDULED: '已安排',
  IN_PROGRESS: '进行中',
  ENDED: '已结束'
}