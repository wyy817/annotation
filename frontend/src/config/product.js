export const APP_MODE = import.meta.env.VITE_APP_MODE === 'demo' ? 'demo' : 'local'
export const IS_DEMO_MODE = APP_MODE === 'demo'

export const PRODUCT_ROLES = Object.freeze([
  {
    id: 'ANNOTATOR',
    labelKey: 'po.roles.pathologist',
    shortLabelKey: 'po.roles.pathologistShort',
  },
  {
    id: 'REVIEWER',
    labelKey: 'po.roles.seniorPathologist',
    shortLabelKey: 'po.roles.seniorPathologistShort',
  },
  {
    id: 'AI_DATA_ADMIN',
    labelKey: 'po.roles.aiDataAdmin',
    shortLabelKey: 'po.roles.aiDataAdminShort',
  },
  {
    id: 'SYSTEM_ADMIN',
    labelKey: 'po.roles.systemAdmin',
    shortLabelKey: 'po.roles.systemAdminShort',
  },
])

export const DEMO_IDENTITIES = Object.freeze({
  ANNOTATOR: {
    username: 'maya.lin',
    displayName: 'Dr. Maya Lin',
    role: 'ANNOTATOR',
  },
  REVIEWER: {
    username: 'elena.ortiz',
    displayName: 'Dr. Elena Ortiz',
    role: 'REVIEWER',
  },
  AI_DATA_ADMIN: {
    username: 'jordan.kim',
    displayName: 'Jordan Kim',
    role: 'AI_DATA_ADMIN',
  },
  SYSTEM_ADMIN: {
    username: 'alex.morgan',
    displayName: 'Alex Morgan',
    role: 'SYSTEM_ADMIN',
  },
})

const ROLE_ALIASES = Object.freeze({
  ADMIN: 'SYSTEM_ADMIN',
  PATHOLOGIST: 'ANNOTATOR',
  SENIOR_PATHOLOGIST: 'REVIEWER',
})

export function normalizeProductRole(role) {
  const normalized = ROLE_ALIASES[role] || role
  return PRODUCT_ROLES.some((candidate) => candidate.id === normalized)
    ? normalized
    : 'ANNOTATOR'
}

export function getProductRole(role) {
  const normalized = normalizeProductRole(role)
  return PRODUCT_ROLES.find((candidate) => candidate.id === normalized)
}

export function canMakeClinicalDecision(role) {
  const normalized = normalizeProductRole(role)
  return normalized === 'ANNOTATOR' || normalized === 'REVIEWER'
}

export function isOperationalReadOnly(role) {
  const normalized = normalizeProductRole(role)
  return normalized === 'AI_DATA_ADMIN' || normalized === 'SYSTEM_ADMIN'
}
