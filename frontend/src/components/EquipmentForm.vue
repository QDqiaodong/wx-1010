<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { Equipment, AgeGroup, EquipmentStatus } from '@/types'
import { AGE_GROUP_MAP, EQUIPMENT_STATUS_MAP } from '@/types'

const props = defineProps<{
  equipment?: Equipment | null
  visible: boolean
}>()

const emit = defineEmits<{
  submit: [data: Omit<Equipment, 'id'>]
  cancel: []
  'update:visible': [value: boolean]
}>()

const form = ref({
  equipmentCode: '',
  name: '',
  frostResistanceSpec: '',
  ageGroup: 'CHILD' as AgeGroup,
  category: '',
  status: 'AVAILABLE' as EquipmentStatus
})

const ageGroupOptions = Object.entries(AGE_GROUP_MAP).map(([value, data]) => ({
  label: data.label,
  value
}))

const statusOptions = Object.entries(EQUIPMENT_STATUS_MAP).map(([value, label]) => ({
  label,
  value
}))

const categoryOptions = [
  { label: '冰上座椅', value: '冰上座椅' },
  { label: '防护扶手', value: '防护扶手' },
  { label: '冰面辅助器材', value: '冰面辅助器材' },
  { label: '其他', value: '其他' }
]

watch(() => props.equipment, (val) => {
  if (val) {
    form.value = { ...val }
  }
}, { immediate: true })

const handleSubmit = () => {
  if (!form.value.equipmentCode || !form.value.name || !form.value.frostResistanceSpec || !form.value.category) {
    ElMessage.error('请填写完整信息')
    return
  }
  emit('submit', { ...form.value })
}

const handleCancel = () => {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog :title="equipment ? '编辑器材' : '新增器材'" :visible="visible" @update:model-value="(val: boolean) => emit('update:visible', val)" width="500px" @close="handleCancel">
    <el-form :model="form" label-width="120px">
      <el-form-item label="器材编号">
        <el-input v-model="form.equipmentCode" placeholder="请输入器材编号" />
      </el-form-item>
      <el-form-item label="器材名称">
        <el-input v-model="form.name" placeholder="请输入器材名称" />
      </el-form-item>
      <el-form-item label="耐寒规格">
        <el-input v-model="form.frostResistanceSpec" placeholder="请输入耐寒规格" />
      </el-form-item>
      <el-form-item label="适配年龄段">
        <el-select v-model="form.ageGroup">
          <el-option v-for="option in ageGroupOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="器材类别">
        <el-select v-model="form.category">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
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