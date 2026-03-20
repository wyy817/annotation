<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-header">
        <h1 class="logo">WSI Platform</h1>
        <p class="subtitle">全切片图像标注平台</p>
      </div>

      <el-form :model="form" @submit.prevent="handleLogin" label-width="0">
        <el-form-item>
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            placeholder="密码"
            size="large"
            type="password"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width:100%; margin-top:8px"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>
      <div class="register-link">
        没有账号？<router-link :to="{ name: 'register' }">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password)
    userStore.setAuth(res.data.token, res.data.user)
    ElMessage.success(`欢迎，${res.data.user.username}！`)
    if (res.data.user.role === 'REVIEWER') {
      router.replace({ name: 'reviewDashboard' })
    } else {
      router.replace({ name: 'workspaces' })
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0eafc 0%, #cfdef3 100%);
}

.login-box {
  width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 36px 36px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 4px;
}

.subtitle {
  font-size: 13px;
  color: #909399;
}

.register-link {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
  color: #909399;
}

.register-link a {
  color: #409eff;
  text-decoration: none;
}

.register-link a:hover {
  text-decoration: underline;
}
</style>
