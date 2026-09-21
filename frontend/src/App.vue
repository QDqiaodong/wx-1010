<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/equipment', label: '器材管理' },
  { path: '/session', label: '场次管理' },
  { path: '/dispatch', label: '入场发装台' },
  { path: '/adjust', label: '客群调整' },
  { path: '/summary', label: '年龄分组汇总' }
]
</script>

<template>
  <div class="app-container">
    <header class="app-header">
      <h1>冰雪体验馆低温游乐器材游客年龄分组适配管控系统</h1>
    </header>
    
    <nav class="app-nav">
      <el-menu :default-active="route.path" mode="horizontal">
        <el-menu-item v-for="item in menuItems" :key="item.path" @click="router.push(item.path)">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </nav>
    
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  background: linear-gradient(135deg, #1e3a5f 0%, #2d5a87 100%);
  color: white;
  padding: 20px 40px;
  text-align: center;
}

.app-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.app-nav {
  background-color: #f0f2f5;
  border-bottom: 1px solid #e8e8e8;
}

.app-main {
  flex: 1;
  padding: 20px;
}
</style>