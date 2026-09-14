<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { Session, SessionStatus } from '@/types'
import { SESSION_STATUS_MAP } from '@/types'

const props = defineProps<{
  session?: Session | null
  visible: boolean
}>()

const emit = defineEmits<{
  submit: [data: Omit<Session, 'id'>]
  cancel: []
  'update:visible': [value: boolean]
}>()

const form = ref({
  sessionCode: '',
  sessionName: '',
  startTime: '',
  endTime: '',
  childRatio: 0.3,
  teenRatio: 0.4,
  adultRatio: 0.3,
  status: 'SCHEDULED' as SessionStatus
})

const statusOptions = Object.entries(SESSION_STATUS_MAP).map(([value, label]) => ({
  label,
  value
}))

const totalRatio = computed(() => {
  return (form.value.childRatio + form.value.teenRatio + form.value.adultRatio).toFixed(2)
})

watch(() => props.session, (val) => {
  if (val) {
    form.value = { ...val }
  }
}, { immediate: true })

const handleSubmit = () => {
  if (!form.value.sessionCode || !form.value.sessionName || !form.value.startTime || !form.value.endTime) {
    ElMessage.error('请填写完整信息')
    return
  }
  if (parseFloat(totalRatio.value) !== 1) {
    ElMessage.error('客群占比总和必须等于1')
    return
  }
  emit('submit', { ...form.value })
}

const handleCancel = () => {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog :title="session ? '编辑场次' : '新增场次'" :visible="visible" @update:model-value="(val: boolean) => emit('update:visible', val)" width="600px" @close="handleCancel">
    <el-form :model="form" label-width="120px">
      <el-form-item label="场次编号">
        <el-input v-model="form.sessionCode" placeholder="请输入场次编号" />
      </el-form-item>
      <el-form-item label="场次名称">
        <el-input v-model="form.sessionName" placeholder="请输入场次名称" />
      </el-form-item>
      <el-form-item label="开始时间">
        <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" />
      </el-form-item>
      <el-form-item label="结束时间">
        <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" />
      </el-form-item>
      <el-form-item label="幼童占比">
        <el-input-number v-model="form.childRatio" :min="0" :max="1" :step="0.05" />
      </el-form-item>
      <el-form-item label="青少年占比">
        <el-input-number v-model="form.teenRatio" :min="0" :max="1" :step="0.05" />
      </el-form-item>
      <el-form-item label="成人占比">
        <el-input-number v-model="form.adultRatio" :min="0" :max="1" :step="0.05" />
      </el-form-item>
      <el-form-item label="占比总和">
        <span :class="{ 'text-red': parseFloat(totalRatio) !== 1 }">{{ totalRatio }}</span>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status">
          <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.text-red {
  color: #f56c6c;
}
</style>