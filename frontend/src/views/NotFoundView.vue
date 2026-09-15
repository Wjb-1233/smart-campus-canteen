<template>
  <div class="nf">
    <el-result icon="warning" title="404" sub-title="页面不存在或你没有访问权限">
      <template #extra>
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goLogin">重新登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { homeForRole } from '../router'

const router = useRouter()
const user = useUserStore()

function goHome() {
  router.push(user.isLogin ? homeForRole(user.role) : '/login')
}
function goLogin() {
  user.logout()
  router.push('/login')
}
</script>

<style scoped>
.nf {
  min-height: 100vh;
  display: grid;
  place-items: center;
}
</style>
