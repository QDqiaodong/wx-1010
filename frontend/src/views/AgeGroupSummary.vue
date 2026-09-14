<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { AgeGroupSummary } from '@/types'
import { EQUIPMENT_STATUS_MAP } from '@/types'
import { equipmentApi } from '@/api'

const summaries = ref<AgeGroupSummary[]>([])

const loadSummary = async () => {
  summaries.value = await equipmentApi.getSummary()
}

onMounted(loadSummary)

const getAgeGroupColor = (ageGroup: string) => {
  const colors: Record<string, string> = {
    CHILD: '#67c23a',
    TEEN: '#409eff',
    ADULT: '#f56c6c'
  }
  return colors[ageGroup] || '#909399'
}
</script>

<template>
  <div class="age-group-summary">
    <div class="header">
      <h2>按年龄分组汇总适配器材</h2>
      <el-button @click="loadSummary">刷新数据</el-button>
    </div>
    
    <div class="summary-cards">
      <div 
        v-for="summary in summaries" 
        :key="summary.ageGroup"
        class="summary-card"
        :style="{ borderColor: getAgeGroupColor(summary.ageGroup) }"
      >
        <div class="card-header" :style="{ background: getAgeGroupColor(summary.ageGroup) }">
          <h3>{{ summary.ageGroupLabel }}</h3>
          <span class="age-range">{{ summary.ageRange }}</span>
        </div>
        <div class="card-body">
          <div class="total-count">
            <span>总计适配器材:</span>
            <span class="count">{{ summary.totalCount }}</span>
          </div>
          <el-table :data="summary.equipments" border size="small" v-if="summary.equipments.length > 0">
            <el-table-column prop="equipmentCode" label="编号" width="100" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="category" label="类别" width="100" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="scope">
                <el-tag :type="{ AVAILABLE: 'success', IN_USE: 'warning', MAINTENANCE: 'danger' }[scope.row.status as keyof typeof EQUIPMENT_STATUS_MAP]" size="small">
                  {{ EQUIPMENT_STATUS_MAP[scope.row.status as keyof typeof EQUIPMENT_STATUS_MAP] }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty">
            暂无适配器材
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.age-group-summary {
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

.summary-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.summary-card {
  border-radius: 8px;
  border-width: 3px;
  border-style: solid;
  overflow: hidden;
}

.card-header {
  color: white;
  padding: 15px;
}

.card-header h3 {
  margin: 0;
  font-size: 18px;
}

.age-range {
  font-size: 12px;
  opacity: 0.9;
}

.card-body {
  padding: 15px;
}

.total-count {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.count {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.empty {
  text-align: center;
  color: #999;
  padding: 20px;
}
</style>