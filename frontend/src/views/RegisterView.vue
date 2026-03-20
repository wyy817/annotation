<template>
  <div class="register-page">
    <div class="register-box">
      <div class="register-header">
        <h1 class="logo">WSI Platform</h1>
        <p class="subtitle">创建新账号</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名（3~30 个字符）"
            size="large"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            placeholder="密码（至少 6 位）"
            size="large"
            type="password"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            placeholder="确认密码"
            size="large"
            type="password"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item prop="role">
          <el-select
            v-model="form.role"
            placeholder="选择角色"
            size="large"
            style="width:100%"
          >
            <el-option label="标注员 (ANNOTATOR)" value="ANNOTATOR" />
            <el-option label="审阅者 (REVIEWER)" value="REVIEWER" />
          </el-select>
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width:100%; margin-top:8px"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>

      <div class="login-link">
        已有账号？<router-link :to="{ name: 'login' }">返回登录</router-link>
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

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 30, message: '用户名长度须在 3~30 个字符之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register(form.username, form.password, form.confirmPassword, form.role)
    ElMessage.success('注册成功，请登录')
    router.push({ name: 'login' })
  } catch {
    // 错误消息已由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0eafc 0%, #cfdef3 100%);
}

.register-box {
  width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 36px 36px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.register-header {
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

.login-link {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
  color: #909399;
}

.login-link a {
  color: #409eff;
  text-decoration: none;
}

.login-link a:hover {
  text-decoration: underline;
}
</style>
