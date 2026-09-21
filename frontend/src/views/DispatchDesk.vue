<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  Session,
  SessionDispatchItem,
  EquipmentDispatchRecord,
  AgeGroup,
  IssueRequest
} from '@/types'
import { AGE_GROUP_MAP, SESSION_STATUS_MAP } from '@/types'
import { sessionApi, dispatchApi } from '@/api'

const route = useRoute()

// ---------- 场次选择 ----------
const sessions = ref<Session[]>([])
const querySessionId = Number(route.query.sessionId)
const currentSessionId = ref<number | null>(
  Number.isFinite(querySessionId) && querySessionId > 0 ? querySessionId : null
)
const currentSession = computed(() => sessions.value.find(s => s.id === currentSessionId.value) ?? null)
const inProgress = computed(() => currentSession.value?.status === 'IN_PROGRESS')

// ---------- 现场公共参数（多工作人员共用，本地记忆） ----------
const operator = ref(localStorage.getItem('dispatch_operator') || '')
const temperature = ref<number | undefined>(
  Number(localStorage.getItem('dispatch_temperature')) || undefined
)
watch(operator, v => localStorage.setItem('dispatch_operator', v.trim()))
watch(temperature, v => {
  if (v !== undefined && !Number.isNaN(v)) localStorage.setItem('dispatch_temperature', String(v))
})

// ---------- 数据 ----------
const items = ref<SessionDispatchItem[]>([])
const records = ref<EquipmentDispatchRecord[]>([])
const loading = ref(false)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const issuedCount = computed(() => items.value.filter(i => i.dispatchStatus === 'ISSUED').length)
const availableCount = computed(() => items.value.filter(i => i.dispatchStatus === 'AVAILABLE').length)

const ageGroupOptions = Object.entries(AGE_GROUP_MAP).map(([value, data]) => ({
  label: data.label,
  value: value as AgeGroup
}))

const loadSessions = async () => {
  sessions.value = await sessionApi.getAll()
  if (!currentSessionId.value && sessions.value.length > 0) {
    const prefer = sessions.value.find(s => s.status === 'IN_PROGRESS')
    currentSessionId.value = (prefer ?? sessions.value[0]).id
  }
}

const loadData = async (silent = false) => {
  if (!currentSessionId.value) return
  if (!silent) loading.value = true
  try {
    const [itemList, recordList] = await Promise.all([
      dispatchApi.getItems(currentSessionId.value),
      dispatchApi.getRecords(currentSessionId.value)
    ])
    items.value = itemList
    records.value = recordList
  } finally {
    loading.value = false
  }
}

watch(currentSessionId, () => loadData())

// 多工作人员同时操作时，定时拉取最新状态（不靠按钮禁用掩盖并发）
const startAutoRefresh = () => {
  refreshTimer = setInterval(() => loadData(true), 5000)
}
onMounted(async () => {
  await loadSessions()
  startAutoRefresh()
})
onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

// ---------- 场次开始/结束 ----------
const handleStartSession = async () => {
  if (!currentSessionId.value) return
  await sessionApi.start(currentSessionId.value)
  ElMessage.success('场次已开始，可以发装')
  await loadSessions()
  await loadData()
}

const handleEndSession = async () => {
  if (!currentSession.value) return
  const outstanding = issuedCount.value
  try {
    await ElMessageBox.confirm(
      outstanding > 0
        ? `当前还有 ${outstanding} 件器材已领用未归还，结束场次将由系统统一兜底收回并标记流水，确认结束？`
        : '确认结束本场次？结束后不能再发装。',
      '结束场次',
      { type: 'warning', confirmButtonText: '确认结束', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  const dto = await sessionApi.endSession(currentSession.value.id)
  ElMessage.success(
    outstanding > 0 ? `场次已结束，${outstanding} 件未归还器材已兜底收回` : '场次已结束'
  )
  sessions.value = sessions.value.map(s => (s.id === dto.id ? dto : s))
  await loadData()
}

// ---------- 发装 ----------
const issueDialogVisible = ref(false)
const issuing = ref(false)
const issueTarget = ref<SessionDispatchItem | null>(null)
const issueForm = ref<{ visitorName: string; visitorAgeGroup: AgeGroup }>({
  visitorName: '',
  visitorAgeGroup: 'ADULT'
})

const openIssueDialog = (item: SessionDispatchItem) => {
  issueTarget.value = item
  issueForm.value = { visitorName: '', visitorAgeGroup: item.targetAgeGroup }
  issueDialogVisible.value = true
}

const handleIssue = async () => {
  if (!currentSessionId.value || !issueTarget.value) return
  if (!operator.value.trim()) {
    ElMessage.error('请先在顶部填写发装操作员')
    return
  }
  if (temperature.value === undefined || Number.isNaN(temperature.value)) {
    ElMessage.error('请先在顶部填写现场实测气温')
    return
  }
  if (!issueForm.value.visitorName.trim()) {
    ElMessage.error('请填写领装游客姓名/编号')
    return
  }
  const payload: IssueRequest = {
    equipmentId: issueTarget.value.equipmentId,
    visitorName: issueForm.value.visitorName.trim(),
    visitorAgeGroup: issueForm.value.visitorAgeGroup,
    temperature: temperature.value,
    operator: operator.value.trim()
  }
  issuing.value = true
  try {
    await dispatchApi.issue(currentSessionId.value, payload)
    ElMessage.success(`发装成功：${issueTarget.value.equipmentCode} → ${payload.visitorName}`)
    issueDialogVisible.value = false
    await loadData()
  } catch (e: any) {
    // 409 并发冲突 / 400 校验失败：后端已给出具体原因，原样展示给工作人员
    ElMessage({ type: e?.response?.status === 409 ? 'warning' : 'error', message: e?.response?.data?.error || '发装失败' })
  } finally {
    issuing.value = false
  }
}

// ---------- 归还 ----------
const returningId = ref<number | null>(null)
const handleReturn = async (item: SessionDispatchItem) => {
  if (!currentSessionId.value || !item.activeRecordId) return
  if (!operator.value.trim()) {
    ElMessage.error('请先在顶部填写归还操作员')
    return
  }
  returningId.value = item.activeRecordId
  try {
    await dispatchApi.returnEquipment(currentSessionId.value, item.activeRecordId, operator.value.trim())
    ElMessage.success(`归还成功：${item.equipmentCode}（${item.activeVisitorName}），器材已重新可领用`)
    await loadData()
  } catch (e: any) {
    ElMessage({ type: 'error', message: e?.response?.data?.error || '归还失败' })
  } finally {
    returningId.value = null
  }
}

const dispatchTagType = (status: string) =>
  status === 'ISSUED'
    ? 'warning'
    : status === 'RETURNED'
      ? 'success'
      : 'info'

const fmtTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 19) : '—')
</script>

<template>
  <div class="dispatch-desk">
    <div class="header">
      <h2>入场发装台</h2>
      <el-select
        v-model="currentSessionId"
        placeholder="请选择场次"
        style="width: 320px"
        :loading="loading"
      >
        <el-option
          v-for="s in sessions"
          :key="s.id"
          :label="`${s.sessionCode} ${s.sessionName}（${SESSION_STATUS_MAP[s.status]}）`"
          :value="s.id"
        />
      </el-select>
      <el-button @click="loadData()">刷新</el-button>
    </div>

    <template v-if="currentSession">
      <!-- 场次状态与操作条 -->
      <el-alert
        class="status-bar"
        :type="inProgress ? 'success' : 'info'"
        :closable="false"
        show-icon
      >
        <template #title>
          <div class="status-line">
            <span>
              场次：<b>{{ currentSession.sessionName }}</b>
              （{{ currentSession.sessionCode }}）
            </span>
            <el-tag :type="inProgress ? 'success' : 'info'" class="ml">
              {{ SESSION_STATUS_MAP[currentSession.status] }}
            </el-tag>
            <span class="ml">在架 <b>{{ availableCount }}</b> 件 / 已领用 <b>{{ issuedCount }}</b> 件</span>
            <div class="spacer" />
            <el-button
              v-if="currentSession.status === 'SCHEDULED'"
              size="small"
              type="success"
              @click="handleStartSession"
            >开始场次</el-button>
            <el-button
              v-if="currentSession.status !== 'ENDED'"
              size="small"
              type="danger"
              plain
              @click="handleEndSession"
            >结束场次（兜底收回）</el-button>
          </div>
        </template>
      </el-alert>

      <!-- 现场公共参数 -->
      <el-card class="context-card" shadow="never">
        <el-form :inline="true">
          <el-form-item label="当前操作员" required>
            <el-input v-model="operator" placeholder="工作人员姓名/工号" clearable style="width: 180px" />
          </el-form-item>
          <el-form-item label="现场实测气温(℃)" required>
            <el-input-number v-model="temperature" :step="0.5" :precision="2" :min="-80" :max="50" />
          </el-form-item>
          <el-form-item>
            <span class="hint">气温与操作员用于每次发装校验与留痕，多人同时操作时数据每 5 秒自动刷新</span>
          </el-form-item>
        </el-form>
      </el-card>

      <el-alert
        v-if="!inProgress"
        :title="`场次当前不是「进行中」，发装将被后端拒绝（${
          currentSession.status === 'ENDED' ? '已结束，未归还器材已兜底收回' : '尚未开始'
        }）`"
        type="warning"
        :closable="false"
        show-icon
        class="mb"
      />

      <!-- 器材发装表 -->
      <el-card shadow="never" class="mb">
        <template #header><b>本场次器材发装</b></template>
        <el-table :data="items" border v-loading="loading" empty-text="本场次尚未绑定器材">
          <el-table-column prop="equipmentCode" label="器材编号" width="130" />
          <el-table-column prop="equipmentName" label="器材名称" min-width="130" />
          <el-table-column label="目标年龄段" width="100">
            <template #default="{ row }">
              {{ AGE_GROUP_MAP[row.targetAgeGroup as AgeGroup]?.label }}
            </template>
          </el-table-column>
          <el-table-column label="抗冻规格(下限)" width="180">
            <template #default="{ row }">
              <span>{{ row.frostResistanceSpec }}</span>
              <el-tag size="small" type="info" class="ml">
                ≥ {{ row.frostLowerLimit ?? '未解析' }}℃
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发装状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.dispatchStatus === 'ISSUED' ? 'warning' : 'success'">
                {{ row.dispatchStatusLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前使用游客" min-width="180">
            <template #default="{ row }">
              <template v-if="row.dispatchStatus === 'ISSUED'">
                {{ row.activeVisitorName }}
                <el-tag size="small" class="ml">{{ row.activeVisitorAgeGroupLabel }}</el-tag>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.dispatchStatus === 'AVAILABLE'"
                size="small"
                type="primary"
                :disabled="!inProgress"
                @click="openIssueDialog(row)"
              >发装</el-button>
              <el-button
                v-else
                size="small"
                type="success"
                :loading="returningId === row.activeRecordId"
                @click="handleReturn(row)"
              >归还</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 流水 -->
      <el-card shadow="never">
        <template #header><b>发装/归还流水（按场次）</b></template>
        <el-table :data="records" border empty-text="暂无流水">
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="dispatchTagType(row.status)">{{ row.statusLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="equipmentCode" label="器材编号" width="120" />
          <el-table-column prop="equipmentName" label="器材名称" min-width="120" />
          <el-table-column label="发给游客" min-width="150">
            <template #default="{ row }">
              {{ row.visitorName }}
              <el-tag size="small" class="ml">{{ row.visitorAgeGroupLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发装时气温/下限" width="140">
            <template #default="{ row }">
              {{ row.temperatureAtIssue }}℃ / {{ row.frostLowerLimitAtIssue }}℃
            </template>
          </el-table-column>
          <el-table-column prop="issueOperator" label="发装人" width="100" />
          <el-table-column label="发装时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.issueTime) }}</template>
          </el-table-column>
          <el-table-column prop="returnOperator" label="归还/兜底操作人" width="130">
            <template #default="{ row }">{{ row.returnOperator || '—' }}</template>
          </el-table-column>
          <el-table-column label="归还时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.returnTime) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else description="请选择一个场次" />

    <!-- 发装弹窗 -->
    <el-dialog v-model="issueDialogVisible" title="现场发装" width="460px">
      <el-descriptions :column="1" border v-if="issueTarget" class="mb">
        <el-descriptions-item label="器材">
          {{ issueTarget.equipmentCode }} {{ issueTarget.equipmentName }}
        </el-descriptions-item>
        <el-descriptions-item label="目标年龄段">
          {{ issueTarget.targetAgeGroupLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="抗冻下限">≥ {{ issueTarget.frostLowerLimit }}℃（当前实测 {{ temperature }}℃）</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="110px">
        <el-form-item label="游客姓名/编号" required>
          <el-input v-model="issueForm.visitorName" placeholder="如：张先生 / V2026001" />
        </el-form-item>
        <el-form-item label="游客年龄段" required>
          <el-select v-model="issueForm.visitorAgeGroup">
            <el-option v-for="o in ageGroupOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实测气温">
          <el-input-number v-model="temperature" :step="0.5" :precision="2" :min="-80" :max="50" />
        </el-form-item>
        <el-form-item label="发装操作员">
          <el-input v-model="operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="issuing" @click="handleIssue">确认发装</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dispatch-desk {
  background: white;
  border-radius: 8px;
  padding: 20px;
}

.header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.header h2 {
  margin: 0;
}

.status-bar {
  margin-bottom: 12px;
}

.status-line {
  display: flex;
  align-items: center;
  gap: 4px;
}

.spacer {
  flex: 1;
}

.context-card {
  margin-bottom: 12px;
}

.hint {
  color: #909399;
  font-size: 12px;
}

.ml {
  margin-left: 8px;
}

.mb {
  margin-bottom: 16px;
}

.muted {
  color: #c0c4cc;
}
</style>
