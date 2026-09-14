<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElButton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import type { Equipment, SessionEquipment, Session } from '@/types'
import { AGE_GROUP_MAP } from '@/types'
import { equipmentApi, sessionApi, sessionEquipmentApi } from '@/api'

const route = useRoute()
const router = useRouter()
const sessionId = Number(route.params.id)

const session = ref<Session | null>(null)
const allEquipments = ref<Equipment[]>([])
const sessionEquipments = ref<SessionEquipment[]>([])

const availableEquipments = computed(() => {
  const boundIds = new Set(sessionEquipments.value.map(se => se.equipmentId))
  return allEquipments.value.filter(e => !boundIds.has(e.id))
})

const boundEquipmentDetails = computed(() => {
  const equipmentMap = new Map(allEquipments.value.map(e => [e.id, e]))
  return sessionEquipments.value.map(se => ({
    ...se,
    equipment: equipmentMap.get(se.equipmentId)
  }))
})

const loadData = async () => {
  session.value = await sessionApi.getById(sessionId)
  allEquipments.value = await equipmentApi.getAll()
  sessionEquipments.value = await sessionEquipmentApi.getBySession(sessionId)
}

onMounted(loadData)

const handleBind = async (equipmentId: number) => {
  const equipment = allEquipments.value.find(e => e.id === equipmentId)
  if (!equipment) return
  
  await sessionEquipmentApi.bind(sessionId, equipmentId, equipment.ageGroup)
  ElMessage.success('绑定成功')
  loadData()
}

const handleUnbind = async (equipmentId: number) => {
  await sessionEquipmentApi.unbind(sessionId, equipmentId)
  ElMessage.success('解绑成功')
  loadData()
}

const handleAutoBind = async () => {
  await sessionEquipmentApi.autoBind(sessionId)
  ElMessage.success('自动绑定完成')
  loadData()
}

const goBack = () => {
  router.push('/session')
}
</script>

<template>
  <div class="session-binding">
    <div class="header">
      <el-button @click="goBack">返回</el-button>
      <h2>{{ session?.sessionName }} - 器材绑定</h2>
      <el-button type="primary" @click="handleAutoBind">自动绑定</el-button>
    </div>
    
    <div class="content">
      <div class="panel">
        <h3>可用器材</h3>
        <el-table :data="availableEquipments" border>
          <el-table-column prop="equipmentCode" label="器材编号" />
          <el-table-column prop="name" label="器材名称" />
          <el-table-column prop="ageGroup" label="适配年龄段">
            <template #default="scope">
              {{ AGE_GROUP_MAP[scope.row.ageGroup as keyof typeof AGE_GROUP_MAP]?.label }}
            </template>
          </el-table-column>
          <el-table-column prop="category" label="类别" />
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="primary" @click="handleBind((scope.row as Equipment).id)">绑定</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <div class="panel">
        <h3>已绑定器材</h3>
        <el-table :data="boundEquipmentDetails" border>
          <el-table-column label="器材编号">
            <template #default="scope">{{ scope.row.equipment?.equipmentCode }}</template>
          </el-table-column>
          <el-table-column label="器材名称">
            <template #default="scope">{{ scope.row.equipment?.name }}</template>
          </el-table-column>
          <el-table-column prop="targetAgeGroup" label="绑定年龄段">
            <template #default="scope">
              {{ AGE_GROUP_MAP[scope.row.targetAgeGroup as keyof typeof AGE_GROUP_MAP]?.label }}
            </template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button size="small" type="danger" @click="handleUnbind(scope.row.equipmentId)">解绑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.session-binding {
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
</style>