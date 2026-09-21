export type AgeGroup = 'CHILD' | 'TEEN' | 'ADULT'

export type EquipmentStatus = 'AVAILABLE' | 'IN_USE' | 'MAINTENANCE'

export type SessionStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'ENDED'

export interface Equipment {
  id: number
  equipmentCode: string
  name: string
  frostResistanceSpec: string
  minTemperature?: number | null
  ageGroup: AgeGroup
  category: string
  status: EquipmentStatus
  /** 当前占用该器材的发装流水 ID；null 表示在库可领 */
  currentIssueRecordId?: number | null
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

export type IssueStatus = 'ISSUED' | 'RETURNED' | 'FORCE_CLOSED'

export const ISSUE_STATUS_MAP: Record<IssueStatus, string> = {
  ISSUED: '已领用',
  RETURNED: '已归还',
  FORCE_CLOSED: '结束兜底归还'
}

export interface IssueRecord {
  id: number
  sessionId: number
  sessionEquipmentId: number
  equipmentId: number
  equipmentCode?: string
  equipmentName?: string
  frostResistanceSpec?: string
  limitTemperature?: number
  visitorId: string
  visitorName?: string
  visitorAgeGroup: AgeGroup
  visitorAgeGroupLabel?: string
  measuredTemperature?: number
  status: IssueStatus
  statusLabel?: string
  issueOperator: string
  issueTime?: string
  returnOperator?: string
  returnTime?: string
  closeReason?: string
}

export interface BoundEquipmentItem {
  sessionEquipmentId: number
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  frostResistanceSpec: string
  minTemperature?: number | null
  targetAgeGroup: AgeGroup
  targetAgeGroupLabel: string
  currentIssueRecordId?: number | null
  currentVisitorId?: string
  currentVisitorName?: string
}

export interface SessionIssueOverview {
  sessionId: number
  sessionCode: string
  sessionName: string
  sessionStatus: SessionStatus
  sessionStatusLabel: string
  items: BoundEquipmentItem[]
  records: IssueRecord[]
}

export interface IssuePayload {
  sessionEquipmentId: number
  visitorId: string
  visitorName?: string
  visitorAgeGroup: AgeGroup
  measuredTemperature: number
  operator: string
}