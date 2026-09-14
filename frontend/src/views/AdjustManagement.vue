<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElButton, ElDialog } from 'element-plus'
import type { Session, AdjustRecord } from '@/types'
import { AGE_GROUP_MAP, SESSION_STATUS_MAP } from '@/types'
import { sessionApi, adjustRecordApi, sessionEquipmentApi } from '@/api'

const sessions = ref<Session[]>([])
const selectedSession = ref<Session | null>(null)
const records = ref<AdjustRecord[]>([])
const showAdjustDialog = ref(false)
const adjustForm = ref({
  childRatio: 0,
  teenRatio: 0,
  adultRatio: 0,
  adjustReason: '',
  operator: ''
})

const selectedSessionRecords = computed(() => {
  if (!selectedSession.value) return []
  return records.value.filter(r => r.sessionId === selectedSession.value?.id)
})

const loadSessions = async () => {
  sessions.value = await sessionApi.getAll()
}

const loadRecords = async () => {
  records.value = await adjustRecordApi.getAll()
}

onMounted(() => {
  loadSessions()
  loadRecords()
})

const selectSession = (session: Session) => {
  selectedSession.value = session
}

const handleAdjust = () => {
  if (!selectedSession.value) {
    ElMessage.warning('请先选择一个场次')
    return
  }
  adjustForm.value = {
    childRatio: selectedSession.value.childRatio,
    teenRatio: selectedSession.value.teenRatio,
    adultRatio: selectedSession.value.adultRatio,
    adjustReason: '',
    operator: ''
  }
  showAdjustDialog.value = true
}

const handleAutoAdjust = async () => {
  if (!selectedSession.value) return
  
  await sessionEquipmentApi.autoAdjust(selectedSession.value.id, {
    childRatio: adjustForm.value.childRatio,
    teenRatio: adjustForm.value.teenRatio,
    adultRatio: adjustForm.value.adultRatio,
    adjustReason: adjustForm.value.adjustReason,
    operator: adjustForm.value.operator
  })
  
  ElMessage.success('自动调整完成')
  showAdjustDialog.value = false
  loadRecords()
  loadSessions()
}

const totalRatio = computed(() => {
  return (adjustForm.value.childRatio + adjustForm.value.teenRatio + adjustForm.value.adultRatio).toFixed(2)
})
</script>

<template>
  <div class="adjust-management">
    <div class="header">
      <h2>客群结构调整</h2>
      <el-button type="primary" @click="handleAdjust" :disabled="!selectedSession">调整客群配比</el-button>
    </div>
    
    <div class="content">
      <div class="panel">
        <h3>场次列表</h3>
        <el-table :data="sessions" border @row-click="selectSession" :highlight-current-row="true">
          <el-table-column prop="sessionCode" label="场次编号" />
          <el-table-column prop="sessionName" label="场次名称" />
          <el-table-column label="客群配比">
            <template #default="scope">
              <div>
                <span>幼童: {{ (scope.row.childRatio * 100).toFixed(0) }}%</span>
                <span style="margin: 0 10px;">|</span>
                <span>青少年: {{ (scope.row.teenRatio * 100).toFixed(0) }}%</span>
                <span style="margin: 0 10px;">|</span>
                <span>成人: {{ (scope.row.adultRatio * 100).toFixed(0) }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态">
            <template #default="scope">
              <el-tag :type="{ SCHEDULED: 'info', IN_PROGRESS: 'warning', ENDED: 'success' }[scope.row.status as keyof typeof SESSION_STATUS_MAP]">
                {{ SESSION_STATUS_MAP[scope.row.status as keyof typeof SESSION_STATUS_MAP] }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <div class="panel">
        <h3>变更流水台账</h3>
        <div v-if="selectedSession" style="margin-bottom: 10px;">
          <span>当前场次: {{ selectedSession.sessionName }}</span>
        </div>
        <el-table :data="selectedSessionRecords" border>
          <el-table-column label="器材ID" prop="equipmentId" />
          <el-table-column label="变更前">
            <template #default="scope">
              {{ AGE_GROUP_MAP[scope.row.oldAgeGroup as keyof typeof AGE_GROUP_MAP]?.label }}
            </template>
          </el-table-column>
          <el-table-column label="变更后">
            <template #default="scope">
              {{ AGE_GROUP_MAP[scope.row.newAgeGroup as keyof typeof AGE_GROUP_MAP]?.label }}
            </template>
          </el-table-column>
          <el-table-column label="原因" prop="adjustReason" />
          <el-table-column label="操作人" prop="adjustOperator" />
        </el-table>
      </div>
    </div>
    
    <ElDialog v-model="showAdjustDialog" title="调整客群配比" width="500px">
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="幼童占比">
          <el-input-number v-model="adjustForm.childRatio" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="青少年占比">
          <el-input-number v-model="adjustForm.teenRatio" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="成人占比">
          <el-input-number v-model="adjustForm.adultRatio" :min="0" :max="1" :step="0.05" />
        </el-form-item>
        <el-form-item label="占比总和">
          <span :class="{ 'text-red': parseFloat(totalRatio) !== 1 }">{{ totalRatio }}</span>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.adjustReason" type="textarea" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="adjustForm.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAutoAdjust" :disabled="parseFloat(totalRatio) !== 1">确定调整</el-button>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.adjust-management {
  background: white;
  border-radius: 8px;
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.panel {
  background: #fafafa;
  border-radius: 8px;
  padding: 15px;
}

.panel h3 {
  margin: 0 0 15px 0;
  font-size: 16px;
}

.text-red {
  color: #f56c6c;
}
</style>