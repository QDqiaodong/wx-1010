<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Session, SessionIssueOverview, BoundEquipmentItem, AgeGroup } from '@/types'
import { AGE_GROUP_MAP, ISSUE_STATUS_MAP, SESSION_STATUS_MAP } from '@/types'
import { sessionApi, issueApi } from '@/api'
import { extractApiError } from '@/utils/error'

const route = useRoute()
const router = useRouter()

const sessions = ref<Session[]>([])
const currentSessionId = ref<number | null>(null)
const overview = ref<SessionIssueOverview | null>(null)
const loading = ref(false)

const operator = ref<string>('')

const ageOptions = Object.entries(AGE_GROUP_MAP).map(([value, d]) => ({
  value: value as AgeGroup,
  label: `${d.label}（${d.ageRange}）`
}))

const isInProgress = computed(() => overview.value?.sessionStatus === 'IN_PROGRESS')

const inUseCount = computed(() => overview.value?.items.filter(i => i.currentIssueRecordId).length ?? 0)
const availableCount = computed(() => overview.value?.items.filter(i => !i.currentIssueRecordId).length ?? 0)

const issueVisible = ref(false)
const issueTarget = ref<BoundEquipmentItem | null>(null)
const submitting = ref(false)
const issueForm = reactive({
  visitorId: '',
  visitorName: '',
  visitorAgeGroup: 'CHILD' as AgeGroup,
  measuredTemperature: 0 as number
})

const loadOverview = async () => {
  if (!currentSessionId.value) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await issueApi.getOverview(currentSessionId.value)
  } catch (e) {
    ElMessage.error(extractApiError(e, '加载发装台失败'))
  } finally {
    loading.value = false
  }
}

const loadSessions = async () => {
  sessions.value = await sessionApi.getAll()
  const fromRoute = Number(route.params.id)
  if (fromRoute && sessions.value.some(s => s.id === fromRoute)) {
    currentSessionId.value = fromRoute
  } else if (sessions.value.length) {
    currentSessionId.value = sessions.value[0].id
  }
}

onMounted(async () => {
  const savedOp = localStorage.getItem('icepark.operator')
  if (savedOp) operator.value = savedOp
  await loadSessions()
  await loadOverview()
})

watch(currentSessionId, loadOverview)

watch(operator, v => {
  if (v) localStorage.setItem('icepark.operator', v)
})

const tagType = (status: string) =>
  ({ SCHEDULED: 'info', IN_PROGRESS: 'warning', ENDED: 'success' } as Record<string, string>)[status] ?? 'info'

const openIssue = (item: BoundEquipmentItem) => {
  if (!operator.value) {
    ElMessage.warning('请先在顶部填写经办人姓名')
    return
  }
  issueTarget.value = item
  issueForm.visitorId = ''
  issueForm.visitorName = ''
  issueForm.visitorAgeGroup = item.targetAgeGroup
  issueForm.measuredTemperature = item.minTemperature != null ? Number(item.minTemperature) + 5 : 0
  issueVisible.value = true
}

const confirmIssue = async () => {
  if (!currentSessionId.value || !issueTarget.value) return
  if (!issueForm.visitorId.trim()) {
    ElMessage.warning('请填写游客凭证号')
    return
  }
  if (issueForm.measuredTemperature === null || Number.isNaN(issueForm.measuredTemperature)) {
    ElMessage.warning('请填写现场实测气温')
    return
  }
  submitting.value = true
  try {
    await issueApi.issue(currentSessionId.value, {
      sessionEquipmentId: issueTarget.value.sessionEquipmentId,
      visitorId: issueForm.visitorId.trim(),
      visitorName: issueForm.visitorName?.trim() || undefined,
      visitorAgeGroup: issueForm.visitorAgeGroup,
      measuredTemperature: issueForm.measuredTemperature,
      operator: operator.value
    })
    ElMessage.success('发装成功')
    issueVisible.value = false
    await loadOverview()
  } catch (e) {
    // 并发“已被领用”等失败必须明确提示，且不依赖按钮禁用
    ElMessage.error(extractApiError(e, '发装失败'))
    await loadOverview()
  } finally {
    submitting.value = false
  }
}

const doReturn = async (item: BoundEquipmentItem) => {
  if (!currentSessionId.value || !item.currentIssueRecordId) return
  try {
    await ElMessageBox.confirm(
      `确认收回器材 ${item.equipmentCode}（游客 ${item.currentVisitorId ?? ''}）？`,
      '归还确认',
      { type: 'warning', confirmButtonText: '确认归还', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await issueApi.returnIssue(currentSessionId.value, item.currentIssueRecordId, operator.value || 'staff')
    ElMessage.success('归还成功，器材已回到在库')
    await loadOverview()
  } catch (e) {
    ElMessage.error(extractApiError(e, '归还失败'))
    await loadOverview()
  }
}

const startSession = async () => {
  if (!currentSessionId.value) return
  try {
    await sessionApi.start(currentSessionId.value)
    ElMessage.success('场次已开始')
    await Promise.all([loadSessions(), loadOverview()])
  } catch (e) {
    ElMessage.error(extractApiError(e, '开始场次失败'))
  }
}

const endSession = async () => {
  if (!currentSessionId.value) return
  try {
    await ElMessageBox.confirm(
      `结束场次后，未归还的 ${inUseCount.value} 件器材将由系统自动兜底归还并释放，确定结束吗？`,
      '结束场次',
      { type: 'warning', confirmButtonText: '结束场次', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await sessionApi.end(currentSessionId.value, operator.value || 'system')
    ElMessage.success('场次已结束，未归还器材已兜底归还')
    await Promise.all([loadSessions(), loadOverview()])
  } catch (e) {
    ElMessage.error(extractApiError(e, '结束场次失败'))
  }
}

const formatTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 19) : '—')
const recordTagType = (s: string) =>
  ({ ISSUED: 'danger', RETURNED: 'success', FORCE_CLOSED: 'info' } as Record<string, string>)[s] ?? 'info'

const goBinding = () => currentSessionId.value && router.push(`/session/${currentSessionId.value}/binding`)
</script>

<template>
  <div class="issue-desk">
    <div class="header">
      <h2>入场发装台</h2>
      <div class="header-controls">
        <el-input v-model="operator" placeholder="经办人姓名" class="operator-input" />
        <el-select v-model="currentSessionId" placeholder="选择场次" style="width: 260px">
          <el-option
            v-for="s in sessions"
            :key="s.id"
            :value="s.id"
            :label="`${s.sessionCode} · ${s.sessionName}（${SESSION_STATUS_MAP[s.status]}）`"
          />
        </el-select>
      </div>
    </div>

    <template v-if="overview">
      <div class="session-bar">
        <div class="session-meta">
          <span class="title">{{ overview.sessionName }}</span>
          <el-tag :type="tagType(overview.sessionStatus)">{{ overview.sessionStatusLabel }}</el-tag>
          <el-tag type="info">在库 {{ availableCount }} 件</el-tag>
          <el-tag type="danger">游客持有 {{ inUseCount }} 件</el-tag>
        </div>
        <div class="session-actions">
          <el-button size="small" @click="goBinding">器材绑定</el-button>
          <el-button
            v-if="overview.sessionStatus === 'SCHEDULED'"
            size="small" type="primary" @click="startSession"
          >开始场次</el-button>
          <el-button
            v-if="overview.sessionStatus === 'IN_PROGRESS'"
            size="small" type="warning" @click="endSession"
          >结束场次（兜底归还）</el-button>
          <el-button size="small" @click="loadOverview">刷新</el-button>
        </div>
      </div>

      <el-alert
        v-if="!isInProgress"
        class="state-alert"
        :title="overview.sessionStatus === 'ENDED'
          ? '场次已结束：不可再发装，未归还器材已在结束时由系统兜底归还并释放。'
          : '场次尚未开始：开始后才能现场发装。'"
        :type="overview.sessionStatus === 'ENDED' ? 'success' : 'info'"
        :closable="false"
        show-icon
      />

      <div class="panel">
        <h3>本场器材（发装 / 归还）</h3>
        <el-table :data="overview.items" border v-loading="loading" empty-text="本场次尚未绑定器材">
          <el-table-column prop="equipmentCode" label="器材编号" width="120" />
          <el-table-column prop="equipmentName" label="器材名称" min-width="130" />
          <el-table-column label="目标年龄段" width="110">
            <template #default="{ row }">{{ row.targetAgeGroupLabel }}</template>
          </el-table-column>
          <el-table-column label="抗冻下限" width="100">
            <template #default="{ row }">
              {{ row.minTemperature != null ? row.minTemperature + ' ℃' : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="当前状态" min-width="180">
            <template #default="{ row }">
              <el-tag v-if="row.currentIssueRecordId" type="danger" size="small">
                已领用 · {{ row.currentVisitorName || row.currentVisitorId }}
              </el-tag>
              <el-tag v-else type="success" size="small">在库可领</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small" type="primary"
                :disabled="!isInProgress || !!row.currentIssueRecordId"
                @click="openIssue(row as BoundEquipmentItem)"
              >发装</el-button>
              <el-button
                size="small" type="success"
                :disabled="!isInProgress || !row.currentIssueRecordId"
                @click="doReturn(row as BoundEquipmentItem)"
              >归还</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint">说明：按钮仅用于操作引导；即使两人同时点发装，后端也会保证同一件器材只成功一次，落败方明确收到“已被领用”。</p>
      </div>

      <div class="panel">
        <h3>发装 / 归还流水</h3>
        <el-table :data="overview.records" border empty-text="暂无流水">
          <el-table-column prop="equipmentCode" label="器材" width="120" />
          <el-table-column label="游客" min-width="150">
            <template #default="{ row }">
              {{ row.visitorName || row.visitorId }}
              <span class="dim">（{{ row.visitorId }}）</span>
            </template>
          </el-table-column>
          <el-table-column label="游客年龄段" width="100">
            <template #default="{ row }">{{ row.visitorAgeGroupLabel }}</template>
          </el-table-column>
          <el-table-column label="实测气温/抗冻下限" width="150">
            <template #default="{ row }">{{ row.measuredTemperature }}℃ / {{ row.limitTemperature }}℃</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="recordTagType(row.status)" size="small">
                {{ ISSUE_STATUS_MAP[row.status as keyof typeof ISSUE_STATUS_MAP] ?? row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发装（人/时间）" min-width="180">
            <template #default="{ row }">{{ row.issueOperator }} · {{ formatTime(row.issueTime) }}</template>
          </el-table-column>
          <el-table-column label="归还（人/时间）" min-width="180">
            <template #default="{ row }">
              <span v-if="row.status === 'ISSUED'" class="dim">未归还</span>
              <span v-else>{{ row.returnOperator }} · {{ formatTime(row.returnTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="overview.records.some(r => r.closeReason)" label="备注" min-width="160">
            <template #default="{ row }">{{ row.closeReason || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-empty v-else description="请选择一个场次" />

    <el-dialog v-model="issueVisible" title="现场发装" width="460px">
      <el-form label-width="110px">
        <el-form-item label="器材">
          <span>{{ issueTarget?.equipmentCode }} · {{ issueTarget?.equipmentName }}（目标：{{ issueTarget?.targetAgeGroupLabel }}，下限 {{ issueTarget?.minTemperature }}℃）</span>
        </el-form-item>
        <el-form-item label="游客凭证号">
          <el-input v-model="issueForm.visitorId" placeholder="票号 / 手环号" />
        </el-form-item>
        <el-form-item label="游客姓名">
          <el-input v-model="issueForm.visitorName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="游客年龄段">
          <el-select v-model="issueForm.visitorAgeGroup">
            <el-option v-for="o in ageOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实测气温(℃)">
          <el-input-number v-model="issueForm.measuredTemperature" :step="0.5" :precision="1" :min="-60" :max="40" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmIssue">确认发装</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.issue-desk {
  background: white;
  border-radius: 8px;
  padding: 20px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
  flex-wrap: wrap;
}
.header h2 { margin: 0; }
.header-controls { display: flex; gap: 12px; }
.operator-input { width: 160px; }
.session-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  gap: 10px;
}
.session-meta { display: flex; align-items: center; gap: 10px; }
.session-meta .title { font-weight: 600; font-size: 16px; }
.session-actions { display: flex; gap: 8px; }
.state-alert { margin-bottom: 14px; }
.panel { margin-bottom: 20px; }
.panel h3 { margin: 0 0 12px; font-size: 16px; }
.hint { color: #909399; font-size: 12px; margin: 8px 2px 0; }
.dim { color: #909399; }
</style>
