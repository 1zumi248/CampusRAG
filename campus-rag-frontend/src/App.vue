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
      <router-view v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<style>
:root {
  /* 主色:墨绿 (teal-700),沉稳的校园/知识感 */
  --green: #0f766e;
  --green-light: #99f6e4;
  --green-bg: #f0fdfa;
  --green-hover: #115e59;

  /* AI 点缀:赤陶 (orange-800),温暖人文,与墨绿互补对比 */
  --accent: #c2410c;
  --accent-bg: #fff7ed;
  --accent-light: #fed7aa;

  /* 中性:stone 暖灰,纸质感,避免冷蓝灰 */
  --border: #e7e5e4;
  --text: #1c1917;
  --text-secondary: #57534e;
  --bg: #fafaf9;
  --white: #ffffff;

  /* 覆盖 Element Plus 主色为项目墨绿,避免分页器/按钮等默认蓝色割裂 */
  --el-color-primary: #0f766e;
  --el-color-primary-light-3: #4ba89e;
  --el-color-primary-light-5: #7ec5bd;
  --el-color-primary-light-7: #b1ddd8;
  --el-color-primary-light-9: #e4f3f1;
  --el-color-primary-dark-2: #0c5f58;
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

/* 页面切换过渡 */
.page-enter-active, .page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-leave-to {
  opacity: 0;
}
</style>
