<template>
  <AuthShell :title="t('po.auth.loginTitle')" :subtitle="t('po.auth.loginSubtitle')">
    <el-form class="po-auth-form" :model="form" label-position="top" @submit.prevent="handleLogin">
      <el-form-item :label="t('po.auth.username')">
        <el-input
          id="login-username"
          v-model="form.username"
          :placeholder="t('po.auth.usernamePlaceholder')"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
        />
      </el-form-item>
      <el-form-item :label="t('po.auth.password')">
        <el-input
          id="login-password"
          v-model="form.password"
          :placeholder="t('po.auth.passwordPlaceholder')"
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
        class="po-auth-submit"
        @click="handleLogin"
      >
        {{ t('po.auth.loginAction') }}
      </el-button>
    </el-form>

    <div class="po-auth-alternative">
      <span>{{ t('po.auth.noAccount') }}</span>
      <router-link :to="{ name: 'register' }">{{ t('po.auth.registerAction') }}</router-link>
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
import { useUserStore } from '@/stores/userStore'
import AuthShell from '@/components/AuthShell.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning(t('po.auth.credentialsRequired'))
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password)
    userStore.setAuth(res.data.token, res.data.user)
    ElMessage.success(t('po.auth.welcomeUser', { name: res.data.user.username }))
    router.replace({ name: 'cases' })
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

.po-auth-submit {
  width: 100%;
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
