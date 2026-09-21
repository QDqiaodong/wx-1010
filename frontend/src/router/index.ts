import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/equipment'
  },
  {
    path: '/equipment',
    name: 'equipment',
    component: () => import('@/views/EquipmentManagement.vue')
  },
  {
    path: '/session',
    name: 'session',
    component: () => import('@/views/SessionManagement.vue')
  },
  {
    path: '/session/:id/binding',
    name: 'binding',
    component: () => import('@/views/SessionBinding.vue')
  },
  {
    path: '/dispatch',
    name: 'dispatch',
    component: () => import('@/views/DispatchDesk.vue')
  },
  {
    path: '/adjust',
    name: 'adjust',
    component: () => import('@/views/AdjustManagement.vue')
  },
  {
    path: '/summary',
    name: 'summary',
    component: () => import('@/views/AgeGroupSummary.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router