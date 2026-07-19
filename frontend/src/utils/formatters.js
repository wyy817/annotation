export function getRiskBand(score) {
  if (!Number.isFinite(score)) return 'pending'
  if (score >= 0.8) return 'high'
  if (score >= 0.4) return 'intermediate'
  return 'low'
}

export function formatPercent(value, locale = 'en-US') {
  if (!Number.isFinite(value)) return '-'
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(value)
}

export function formatDateTime(value, locale = 'en-US') {
  return new Intl.DateTimeFormat(locale, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: locale === 'en-US',
  }).format(new Date(value))
}

export function formatTime(value, locale = 'en-US') {
  return new Intl.DateTimeFormat(locale, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: locale === 'en-US',
  }).format(new Date(value))
}
