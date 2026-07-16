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
      <div class="logo" aria-label="CampusRAG 校园知识工作台">
        <span class="logo-mark">知</span>
        <span class="logo-copy">
          <span class="logo-title">CampusRAG</span>
          <span class="logo-subtitle">校园知识工作台</span>
        </span>
      </div>
      <div class="nav-label">工作台</div>
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
        <span class="service-state"><i></i>知识服务台</span>
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
  --paper: #f7f5f0;
  --ink-muted: #78716c;
  --shadow-soft: 0 18px 50px rgba(41, 37, 36, 0.07);

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
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif;
  color: var(--text);
  background: var(--paper);
}

.app-container {
  height: 100%;
  min-width: 0;
}

.app-aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border);
  background: var(--white);
  position: relative;
  z-index: 30;
}

.logo {
  padding: 22px 18px 20px;
  display: flex;
  align-items: center;
  gap: 11px;
}

.logo-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid var(--green);
  border-radius: 9px 9px 9px 3px;
  color: var(--green);
  font-family: 'Songti SC', 'STSong', serif;
  font-size: 18px;
  font-weight: 700;
}

.logo-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.logo-title {
  color: var(--green);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 19px;
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.5px;
}

.logo-subtitle {
  margin-top: 4px;
  color: var(--ink-muted);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.nav-label {
  padding: 0 20px 8px;
  color: #a8a29e;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
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
  padding: 14px 18px;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.service-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-muted);
  font-size: 11px;
}

.service-state i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--green);
  box-shadow: 0 0 0 3px var(--green-bg);
}

.version {
  font-size: 12px;
  color: #d1d5db;
}

.app-main {
  background: var(--paper);
  padding: 24px;
  height: 100%;
  overflow: auto;
  min-width: 0;
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

@media (max-width: 860px) {
  .app-container {
    flex-direction: column;
  }

  .app-aside {
    width: 100% !important;
    height: 64px;
    flex: 0 0 64px;
    flex-direction: row;
    align-items: center;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }

  .logo {
    padding: 10px 14px;
    gap: 8px;
  }

  .logo-mark {
    width: 32px;
    height: 32px;
  }

  .logo-title {
    font-size: 17px;
  }

  .logo-subtitle,
  .nav-label,
  .aside-footer {
    display: none;
  }

  .side-menu.el-menu {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 4px;
    padding: 0 10px 0 0;
    overflow: hidden;
  }

  .side-menu .el-menu-item {
    height: 40px;
    margin: 0;
    padding: 0 12px !important;
  }

  .app-main {
    width: 100%;
    height: calc(100% - 64px);
    padding: 12px;
  }
}

@media (max-width: 480px) {
  .logo-copy {
    display: none;
  }

  .side-menu.el-menu {
    justify-content: flex-start;
  }

  .side-menu .el-menu-item {
    flex: 1;
    justify-content: center;
    font-size: 13px;
  }
}
</style>
