import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import enUS from './en-US'
import parvisightZh from './parvisight.zh'
import parvisightEn from './parvisight.en'

const STORAGE_KEY = 'wsi-lang'

function detectLocale() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'zh-CN' || saved === 'en-US') return saved
  return 'en-US'
}

const initialLocale = detectLocale()
document.documentElement.lang = initialLocale

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': { ...zhCN, po: parvisightZh },
    'en-US': { ...enUS, po: parvisightEn },
  },
})

export function setLocale(locale) {
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

export function getLocale() {
  return i18n.global.locale.value
}
