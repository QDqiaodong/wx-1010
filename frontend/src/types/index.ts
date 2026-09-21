export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT'

export type EquipmentStatus = 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE'

export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ENDED'

/** 场次-器材绑定的现场发装状态 */
export type BindDispatchStatus = 'AVAILABLE' | 'ISSUED'

/** 发装流水状态：已领用 / 已归还 / 场次结束兜底收回 */
export type DispatchStatus = 'ISSUED' | 'RETURNED' | 'AUTO_CLOSED'

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

/** 发装台现场视图：场次绑定的一件器材及其当前发装状态 */
export interface SessionDispatchItem {
  sessionEquipmentId: number
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  category: string
  frostResistanceSpec: string
  targetAgeGroup: AgeGroup
  targetAgeGroupLabel: string
  frostLowerLimit: number | null
  dispatchStatus: BindDispatchStatus
  dispatchStatusLabel: string
  activeRecordId: number | null
  activeVisitorName?: string
  activeVisitorAgeGroup?: AgeGroup
  activeVisitorAgeGroupLabel?: string
}

/** 发装/归还流水 */
export interface EquipmentDispatchRecord {
  id: number
  sessionId: number
  sessionEquipmentId: number
  equipmentId: number
  equipmentCode?: string
  equipmentName?: string
  frostResistanceSpec?: string
  visitorName: string
  visitorAgeGroup: AgeGroup
  visitorAgeGroupLabel: string
  temperatureAtIssue: number
  frostLowerLimitAtIssue: number
  issueOperator: string
  issueTime: string
  status: DispatchStatus
  statusLabel: string
  returnOperator?: string
  returnTime?: string
}

export interface IssueRequest {
  equipmentId: number
  visitorName: string
  visitorAgeGroup: AgeGroup
  temperature: number
  operator: string
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

export const BIND_DISPATCH_STATUS_MAP: Record<BindDispatchStatus, string> = {
  AVAILABLE: '在架',
  ISSUED: '已领用'
}

export const DISPATCH_STATUS_MAP: Record<DispatchStatus, string> = {
  ISSUED: '已领用',
  RETURNED: '已归还',
  AUTO_CLOSED: '结束兜底收回'
}