import { createRouter, createWebHistory } from 'vue-router'
import TraceDashboard from '../views/TraceDashboard.vue'
import EventDashboard from '../views/EventDashboard.vue'
import HeartbeatDashboard from '../views/HeartbeatDashboard.vue'
import LogsPage from '../views/LogsPage.vue'
import AggregationPage from '../views/AggregationPage.vue'
import RedisMonitorPage from '../views/RedisMonitorPage.vue'
import DbMonitorPage from '../views/DbMonitorPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/trace' },
    { path: '/trace', component: TraceDashboard },
    { path: '/event', component: EventDashboard },
    { path: '/heartbeat', component: HeartbeatDashboard },
    { path: '/logs', component: LogsPage },
    { path: '/aggregation', component: AggregationPage },
    { path: '/redis', component: RedisMonitorPage },
    { path: '/db', component: DbMonitorPage },
  ]
})

export default router
