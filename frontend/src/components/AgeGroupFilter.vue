<script setup lang="ts">
import type { AgeGroup } from '@/types'
import { AGE_GROUP_MAP } from '@/types'

defineProps<{
  modelValue?: AgeGroup | ''
  multiple?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: AgeGroup | '' | AgeGroup[]]
}>()

const ageGroupOptions = Object.entries(AGE_GROUP_MAP).map(([value, data]) => ({
  label: `${data.label} (${data.ageRange})`,
  value
}))

const handleChange = (value: AgeGroup | '' | AgeGroup[]) => {
  emit('update:modelValue', value)
}
</script>

<template>
  <div class="age-group-filter">
    <el-select
      :model-value="modelValue"
      :multiple="multiple"
      placeholder="选择年龄分组"
      clearable
      @change="handleChange"
    >
      <el-option v-for="option in ageGroupOptions" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
  </div>
</template>

<style scoped>
.age-group-filter {
  width: 200px;
}
</style>