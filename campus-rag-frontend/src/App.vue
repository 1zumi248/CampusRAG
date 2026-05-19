<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Folder } from '@element-plus/icons-vue'

const route = useRoute()
const activeIndex = computed(() => route.path)
</script>

<template>
  <el-container class="app-container">
    <el-aside class="app-aside" width="220px">
      <div class="logo">CampusRAG</div>
      <el-menu
        :default-active="activeIndex"
        router
        class="side-menu"
        background-color="transparent"
        :default-openeds="['/']"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 问答</span>
        </el-menu-item>
        <el-menu-item index="/manage">
          <el-icon><Folder /></el-icon>
          <span>文档管理</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <span class="version">v0.1.0</span>
      </div>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
:root {
  --green: #10b981;
  --green-light: #d1fae5;
  --green-bg: #ecfdf5;
  --green-hover: #059669;
  --border: #e5e7eb;
  --text: #1f2937;
  --text-secondary: #6b7280;
  --bg: #f9fafb;
  --white: #ffffff;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--text);
}

.app-container {
  height: 100%;
}

.app-aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border);
  background: var(--white);
}

.logo {
  padding: 20px 20px 16px;
  font-size: 20px;
  font-weight: 700;
  color: var(--green);
  letter-spacing: -0.5px;
}

.side-menu {
  flex: 1;
  border-right: none !important;
  padding: 0 8px;
}

.side-menu .el-menu-item {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 40px;
  line-height: 40px;
  color: var(--text-secondary);
}

.side-menu .el-menu-item:hover {
  background: var(--green-bg);
  color: var(--green);
}

.side-menu .el-menu-item.is-active {
  background: var(--green-bg);
  color: var(--green);
  font-weight: 600;
}

.side-menu .el-menu-item .el-icon {
  font-size: 18px;
}

.aside-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
}

.version {
  font-size: 12px;
  color: #d1d5db;
}

.app-main {
  background: var(--bg);
  padding: 24px;
  height: 100%;
  overflow: auto;
}
</style>
