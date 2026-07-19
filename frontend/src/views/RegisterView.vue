<template>
  <AuthShell :title="t('po.auth.registerTitle')" :subtitle="t('po.auth.registerSubtitle')">
    <el-form
      ref="formRef"
      class="po-auth-form"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <el-form-item prop="username" :label="t('po.auth.username')">
        <el-input
          id="register-username"
          v-model="form.username"
          :placeholder="t('po.auth.usernamePlaceholder')"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
        />
      </el-form-item>
      <el-form-item prop="password" :label="t('po.auth.password')">
        <el-input
          id="register-password"
          v-model="form.password"
          :placeholder="t('po.auth.passwordPlaceholder')"
          size="large"
          type="password"
          :prefix-icon="Lock"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item prop="confirmPassword" :label="t('po.auth.confirmPassword')">
        <el-input
          id="register-confirm-password"
          v-model="form.confirmPassword"
          :placeholder="t('po.auth.confirmPasswordPlaceholder')"
          size="large"
          type="password"
          :prefix-icon="Lock"
          show-password
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item prop="role" :label="t('po.auth.role')">
        <el-select
          v-model="form.role"
          :placeholder="t('po.auth.rolePlaceholder')"
          size="large"
          class="po-auth-role"
        >
          <el-option :label="t('po.roles.pathologist')" value="ANNOTATOR" />
          <el-option :label="t('po.roles.seniorPathologist')" value="REVIEWER" />
        </el-select>
      </el-form-item>
      <el-button
        type="primary"
        size="large"
        :loading="loading"
        class="po-auth-submit"
        @click="handleRegister"
      >
        {{ t('po.auth.registerAction') }}
      </el-button>
    </el-form>

    <div class="po-auth-alternative">
      <span>{{ t('po.auth.hasAccount') }}</span>
      <router-link :to="{ name: 'login' }">{{ t('po.auth.loginAction') }}</router-link>
    </div>
  </AuthShell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import AuthShell from '@/components/AuthShell.vue'

const { t } = useI18n()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: '',
})

const rules = {
  username: [
    { required: true, message: () => t('po.auth.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 30, message: () => t('po.auth.usernameLength'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: () => t('po.auth.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('po.auth.passwordMinLength'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: () => t('po.auth.confirmPasswordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error(t('po.auth.passwordMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  role: [
    { required: true, message: () => t('po.auth.roleRequired'), trigger: 'change' },
  ],
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register(form.username, form.password, form.confirmPassword, form.role)
    ElMessage.success(t('po.auth.registrationSubmitted'))
    router.push({ name: 'login' })
  } catch {
    // The HTTP interceptor presents API errors consistently.
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.po-auth-form :deep(.el-form-item__label) {
  padding-bottom: 6px;
  color: var(--po-ink);
  font-size: 12px;
  font-weight: 600;
}

.po-auth-role,
.po-auth-submit {
  width: 100%;
}

.po-auth-submit {
  margin-top: 6px;
}

.po-auth-alternative {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-top: 20px;
  color: var(--po-ink-muted);
  font-size: 12px;
}

.po-auth-alternative a {
  color: var(--po-harbor);
  font-weight: 600;
  text-decoration: none;
}

.po-auth-alternative a:hover {
  text-decoration: underline;
}
</style>
