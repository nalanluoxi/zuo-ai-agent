<template>
  <el-container style="min-height: 100vh">
    <el-aside width="220px" style="background: #001529">
      <div style="padding: 20px; color: #fff; font-size: 18px; font-weight: 700; text-align: center">
        ZUO Monitor
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#fff"
      >
        <el-menu-item index="/trace">
          <el-icon><Connection /></el-icon>
          <span>链路追踪</span>
        </el-menu-item>
        <el-menu-item index="/event">
          <el-icon><Bell /></el-icon>
          <span>事件中心</span>
        </el-menu-item>
        <el-menu-item index="/heartbeat">
          <el-icon><Monitor /></el-icon>
          <span>心跳监控</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <span>日志搜索</span>
        </el-menu-item>
        <el-menu-item index="/aggregation">
          <el-icon><Search /></el-icon>
          <span>聚合分析</span>
        </el-menu-item>
        <el-menu-item index="/redis">
          <el-icon><Cpu /></el-icon>
          <span>Redis 监控</span>
        </el-menu-item>
        <el-menu-item index="/db">
          <el-icon><Coin /></el-icon>
          <span>DB 监控</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="padding: 24px; background: #f0f2f5">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
</script>
