<template>
  <div class="register-page">
    <div class="register-box">
      <div class="register-header">
        <h1 class="logo">WSI Platform</h1>
        <p class="subtitle">{{ $t('auth.registerTitle') }}</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            :placeholder="$t('auth.usernamePlaceholder')"
            size="large"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            :placeholder="$t('auth.passwordPlaceholder')"
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
            :placeholder="$t('auth.confirmPassword')"
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
            :placeholder="$t('auth.selectRole')"
            size="large"
            style="width:100%"
          >
            <el-option :label="$t('auth.roleAnnotator')" value="ANNOTATOR" />
            <el-option :label="$t('auth.roleReviewer')" value="REVIEWER" />
          </el-select>
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          style="width:100%; margin-top:8px"
          @click="handleRegister"
        >
          {{ $t('auth.registerBtn') }}
        </el-button>
      </el-form>

      <div class="login-link">
        <router-link :to="{ name: 'login' }">{{ $t('auth.toLogin') }}</router-link>
      </div>
      <div class="lang-row">
        <LangSwitch />
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import LangSwitch from '@/components/LangSwitch.vue'

const { t } = useI18n()
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
    { required: true, message: () => t('auth.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 30, message: () => t('auth.usernameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: () => t('auth.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('auth.passwordMinLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: () => t('auth.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error(t('auth.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  role: [
    { required: true, message: () => t('auth.roleRequired'), trigger: 'change' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register(form.username, form.password, form.confirmPassword, form.role)
    ElMessage.success(t('auth.registerSuccessLogin'))
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

.lang-row {
  text-align: center;
  margin-top: 12px;
}
</style>
