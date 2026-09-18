<template>
  <div class="login-wrap">
    <div class="login-panel">
      <h1>智慧校园食堂</h1>
      <p>SpringBoot + Vue3 实训演示系统</p>
      <el-form @submit.prevent="onLogin">
        <el-form-item label="学号/工号">
          <el-input v-model="form.studentNo" placeholder="例如 2021001001" @keyup.enter="onLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="默认 123456" @keyup.enter="onLogin" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="onLogin">登录</el-button>
        <el-button style="width:100%;margin-top:10px" :loading="oauthLoading" @click="onOAuthLogin">
          教务系统 OAuth2 授权登录
        </el-button>
      </el-form>

      <el-divider>演示账号快速登录</el-divider>
      <div class="quick">
        <el-button v-for="acc in accounts" :key="acc.no" size="small" :type="acc.type"
          :loading="loading && form.studentNo === acc.no" @click="quickLogin(acc.no)">
          {{ acc.label }}
        </el-button>
      </div>
      <div class="tips">
        学生/老师端 2021001001 · 食堂端 S10001 · 管理员端 A10001<br />
        密码均为 123456（登录后自动进入对应工作台）
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api/http'
import { useUserStore } from '../stores/user'
import { homeForRole } from '../router'

const router = useRouter()
const route = useRoute()
const user = useUserStore()
const loading = ref(false)
const oauthLoading = ref(false)
const form = reactive({ studentNo: '2021001001', password: '123456' })

const accounts = [
  { no: '2021001001', label: '学生/老师', type: 'primary' as const },
  { no: 'S10001', label: '食堂', type: 'success' as const },
  { no: 'A10001', label: '管理员', type: 'warning' as const },
]

/** 登录成功后：优先跳回被拦截的页面，否则按角色进入对应工作台 */
function afterLogin() {
  const redirect = route.query.redirect as string | undefined
  if (redirect && redirect !== '/login') {
    router.push(redirect)
  } else {
    router.push(homeForRole(user.role))
  }
}

async function doLogin(studentNo: string, password: string) {
  loading.value = true
  try {
    await user.login(studentNo, password)
    ElMessage.success(`登录成功 · ${user.realName}`)
    afterLogin()
  } finally {
    loading.value = false
  }
}

async function onLogin() {
  if (!form.studentNo || !form.password) {
    ElMessage.warning('请输入学号/工号与密码')
    return
  }
  await doLogin(form.studentNo, form.password)
}

async function quickLogin(no: string) {
  form.studentNo = no
  form.password = '123456'
  await doLogin(no, '123456')
}

async function onOAuthLogin() {
  oauthLoading.value = true
  try {
    // 1. 获取教务授权链接（模拟）
    const auth: any = await http.get('/auth/oauth/edu/authorize')
    // 2. 模拟教务侧已同意授权：输入学号换取登录态
    const { value } = await ElMessageBox.prompt('模拟教务 OAuth2 授权：输入已建档的学号', '教务授权登录', {
      confirmButtonText: '授权并登录',
      cancelButtonText: '取消',
      inputPlaceholder: '例如 2021001001',
    })
    if (!value) return
    const res: any = await http.get('/auth/oauth/edu/callback', { params: { code: `EDU_${value}`, state: auth.data?.state } })
    const data = res.data
    user.token = data.token
    user.role = data.role
    user.realName = data.realName
    user.studentNo = data.studentNo
    user.userId = data.userId
    ElMessage.success('教务授权登录成功')
    afterLogin()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  } finally {
    oauthLoading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, rgba(61,143,214,.35), transparent 40%),
    linear-gradient(135deg, #0f3a66, #1a5f9e 45%, #6aa7d8);
}
.login-panel {
  width: min(440px, 92vw);
  background: rgba(255,255,255,.95);
  border-radius: 18px;
  padding: 32px;
  box-shadow: 0 20px 50px rgba(0,0,0,.18);
}
h1 { margin: 0 0 8px; color: #123f6b; }
p { margin: 0 0 24px; color: #5b6b7c; }
.quick { display: flex; gap: 8px; flex-wrap: wrap; }
.quick .el-button { margin-left: 0; }
.tips { margin-top: 16px; font-size: 12px; color: #7a8898; line-height: 1.8; }
</style>
