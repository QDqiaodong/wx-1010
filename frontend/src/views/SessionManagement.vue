<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElButton } from 'element-plus'
import type { Session } from '@/types'
import { SESSION_STATUS_MAP } from '@/types'
import { sessionApi } from '@/api'
import SessionForm from '@/components/SessionForm.vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const sessions = ref<Session[]>([])
const showForm = ref(false)
const editingSession = ref<Session | null>(null)

const loadSessions = async () => {
  sessions.value = await sessionApi.getAll()
}

onMounted(loadSessions)

const handleAdd = () => {
  editingSession.value = null
  showForm.value = true
}

const handleEdit = (session: Session) => {
  editingSession.value = session
  showForm.value = true
}

const handleDelete = async (id: number) => {
  await sessionApi.delete(id)
  ElMessage.success('删除成功')
  loadSessions()
}

const handleBinding = (id: number) => {
  router.push(`/session/${id}/binding`)
}

const handleDispatch = (id: number) => {
  router.push({ path: '/dispatch', query: { sessionId: String(id) } })
}

const handleSubmit = async (data: Omit<Session, 'id'>) => {
  if (editingSession.value) {
    await sessionApi.update(editingSession.value.id, data)
    ElMessage.success('更新成功')
  } else {
    await sessionApi.create(data)
    ElMessage.success('创建成功')
  }
  showForm.value = false
  loadSessions()
}

const handleCancel = () => {
  showForm.value = false
}
</script>

<template>
  <div class="session-management">
    <div class="header">
      <h2>冰雪场次管理</h2>
      <el-button type="primary" @click="handleAdd">新增场次</el-button>
    </div>
    
    <el-table :data="sessions" border>
      <el-table-column prop="sessionCode" label="场次编号" />
      <el-table-column prop="sessionName" label="场次名称" />
      <el-table-column prop="startTime" label="开始时间" />
      <el-table-column prop="endTime" label="结束时间" />
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
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="handleBinding((scope.row as Session).id)">器材绑定</el-button>
          <el-button size="small" type="warning" @click="handleDispatch((scope.row as Session).id)">入场发装</el-button>
          <el-button size="small" @click="handleEdit(scope.row as Session)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete((scope.row as Session).id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <SessionForm :session="editingSession" :visible="showForm" @submit="handleSubmit" @cancel="handleCancel" />
  </div>
</template>

<style scoped>
.session-management {
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
</style>