import { spawn } from 'node:child_process'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const edgePath = process.argv[2]
const baseUrl = process.argv[3] || 'http://127.0.0.1:5173'
const outputDirectory = resolve(process.argv[4] || join(tmpdir(), 'pathorchestra-ui-smoke'))
const localBaseUrl = process.argv[5]
const debuggingPort = 9337

if (!edgePath) {
  throw new Error('Usage: node scripts/ui-smoke.mjs <edge-path> [base-url] [output-directory]')
}

const delay = (milliseconds) => new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds))

class CdpClient {
  constructor(webSocketUrl) {
    this.webSocketUrl = webSocketUrl
    this.socket = null
    this.nextId = 1
    this.pending = new Map()
  }

  async connect() {
    this.socket = new WebSocket(this.webSocketUrl)
    await new Promise((resolveConnect, rejectConnect) => {
      this.socket.addEventListener('open', resolveConnect, { once: true })
      this.socket.addEventListener('error', rejectConnect, { once: true })
    })
    this.socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data)
      if (!message.id || !this.pending.has(message.id)) return
      const request = this.pending.get(message.id)
      this.pending.delete(message.id)
      if (message.error) request.reject(new Error(message.error.message))
      else request.resolve(message.result)
    })
  }

  send(method, params = {}) {
    const id = this.nextId
    this.nextId += 1
    return new Promise((resolveRequest, rejectRequest) => {
      this.pending.set(id, { resolve: resolveRequest, reject: rejectRequest })
      this.socket.send(JSON.stringify({ id, method, params }))
    })
  }

  close() {
    this.socket?.close()
  }
}

async function waitForPageEndpoint() {
  for (let attempt = 0; attempt < 80; attempt += 1) {
    try {
      const response = await fetch('http://127.0.0.1:' + debuggingPort + '/json/list')
      const pages = await response.json()
      const page = pages.find((candidate) => candidate.type === 'page')
      if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl
    } catch {
      // Edge may still be starting.
    }
    await delay(125)
  }
  throw new Error('Timed out waiting for the Edge debugging endpoint')
}

async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.exception?.description || 'Browser evaluation failed')
  }
  return result.result.value
}

async function settle(client) {
  await evaluate(
    client,
    '(async () => { await document.fonts.ready; await new Promise((resolve) => setTimeout(resolve, 700)); await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))); return true })()',
  )
}

async function navigate(client, url, width, height, mobile = false) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile,
    screenWidth: width,
    screenHeight: height,
  })
  await client.send('Page.navigate', { url })
  await settle(client)
}

async function capture(client, filename) {
  const screenshot = await client.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
    captureBeyondViewport: false,
  })
  await writeFile(join(outputDirectory, filename), Buffer.from(screenshot.data, 'base64'))
}

async function click(client, selector) {
  const clicked = await evaluate(
    client,
    '(() => { const element = document.querySelector(' + JSON.stringify(selector) + '); if (!element) return false; element.click(); return true })()',
  )
  if (!clicked) throw new Error('Missing click target: ' + selector)
  await settle(client)
}

async function setValue(client, selector, value) {
  const updated = await evaluate(
    client,
    '(() => { const element = document.querySelector(' + JSON.stringify(selector) + '); if (!element) return false; const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(element), "value")?.set; setter?.call(element, ' + JSON.stringify(value) + '); element.dispatchEvent(new Event("input", { bubbles: true })); element.dispatchEvent(new Event("change", { bubbles: true })); return true })()',
  )
  if (!updated) throw new Error('Missing input target: ' + selector)
}

async function waitFor(client, expression, message, timeoutMs = 8000) {
  const startedAt = Date.now()
  while (Date.now() - startedAt < timeoutMs) {
    if (await evaluate(client, expression)) return
    await delay(100)
  }
  throw new Error(message)
}

function assert(report, condition, message, details = null) {
  report.checks.push({ ok: Boolean(condition), message, details })
  if (!condition) throw new Error(message + (details ? ': ' + JSON.stringify(details) : ''))
}

async function runSuite(client) {
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl,
    checks: [],
  }

  await navigate(client, baseUrl + '/#/cases', 1440, 900)
  const desktopQueue = await evaluate(client, [
    '(() => ({',
    '  heading: document.querySelector("h1")?.textContent.trim(),',
    '  rows: document.querySelectorAll(".po-case-table tbody tr").length,',
    '  innerWidth: window.innerWidth,',
    '  scrollWidth: document.documentElement.scrollWidth,',
    '  actionVisible: Boolean(document.querySelector(".po-open-button")?.getBoundingClientRect().right <= window.innerWidth)',
    '}))()',
  ].join('\n'))
  assert(report, desktopQueue.heading === 'Case queue', 'Desktop queue heading renders', desktopQueue)
  assert(report, desktopQueue.rows === 10, 'Desktop queue renders ten seeded cases', desktopQueue)
  assert(report, desktopQueue.scrollWidth <= desktopQueue.innerWidth, 'Desktop queue has no page-level horizontal overflow', desktopQueue)
  assert(report, desktopQueue.actionVisible, 'Desktop next action is visible', desktopQueue)
  await capture(client, 'dashboard-1440.png')

  await navigate(client, baseUrl + '/#/cases', 1024, 768)
  const compactQueue = await evaluate(client, [
    '(() => ({',
    '  innerWidth: window.innerWidth,',
    '  scrollWidth: document.documentElement.scrollWidth,',
    '  actionRight: document.querySelector(".po-open-button")?.getBoundingClientRect().right,',
    '  confidenceDisplay: getComputedStyle(document.querySelector(".po-case-table th:nth-child(4)")).display,',
    '  evidenceDisplay: getComputedStyle(document.querySelector(".po-case-table th:nth-child(5)")).display',
    '}))()',
  ].join('\n'))
  assert(report, compactQueue.scrollWidth <= compactQueue.innerWidth, '1024px queue has no page-level horizontal overflow', compactQueue)
  assert(report, compactQueue.actionRight <= compactQueue.innerWidth, '1024px next action remains visible', compactQueue)
  assert(report, compactQueue.confidenceDisplay === 'none' && compactQueue.evidenceDisplay === 'none', '1024px queue removes secondary columns', compactQueue)
  await capture(client, 'dashboard-1024.png')

  await navigate(client, baseUrl + '/#/cases', 390, 844, true)
  const mobileQueue = await evaluate(client, [
    '(() => {',
    '  const role = document.querySelector(".po-role-field")?.getBoundingClientRect()',
    '  const language = document.querySelector(".po-language-trigger")?.getBoundingClientRect()',
    '  return {',
    '    innerWidth: window.innerWidth,',
    '    scrollWidth: document.documentElement.scrollWidth,',
    '    mobileList: getComputedStyle(document.querySelector(".po-mobile-cases")).display,',
    '    desktopTable: getComputedStyle(document.querySelector(".po-case-table")).display,',
    '    roleRight: role?.right,',
    '    languageRight: language?.right,',
    '    demoDisclosureWidth: document.querySelector(".po-demo-disclosure")?.getBoundingClientRect().width',
    '  }',
    '})()',
  ].join('\n'))
  assert(report, mobileQueue.innerWidth === 390, 'Mobile emulation uses a 390px CSS viewport', mobileQueue)
  assert(report, mobileQueue.scrollWidth <= mobileQueue.innerWidth, 'Mobile queue has no horizontal overflow', mobileQueue)
  assert(report, mobileQueue.mobileList !== 'none' && mobileQueue.desktopTable === 'none', 'Mobile queue uses the read-only list layout', mobileQueue)
  assert(report, mobileQueue.roleRight <= 390 && mobileQueue.languageRight <= 390, 'Mobile role and language controls remain visible', mobileQueue)
  await capture(client, 'dashboard-390.png')

  await navigate(client, baseUrl + '/#/cases/PO-26-0717-001', 1440, 900)
  const review = await evaluate(client, [
    '(() => {',
    '  const canvas = document.querySelector("canvas")',
    '  const context = canvas?.getContext("2d")',
    '  const pixels = context?.getImageData(0, 0, canvas.width, canvas.height).data',
    '  const colors = new Set()',
    '  if (pixels) {',
    '    const stride = Math.max(4, Math.floor(pixels.length / 1600 / 4) * 4)',
    '    for (let index = 0; index < pixels.length; index += stride) {',
    '      colors.add(pixels[index] + "," + pixels[index + 1] + "," + pixels[index + 2])',
    '    }',
    '  }',
    '  return {',
    '    canvasWidth: canvas?.width,',
    '    canvasHeight: canvas?.height,',
    '    sampledColors: colors.size,',
    '    regions: document.querySelectorAll(".po-region-button").length,',
    '    startVisible: Boolean(document.querySelector(".po-start-review button"))',
    '  }',
    '})()',
  ].join('\n'))
  assert(report, review.canvasWidth > 500 && review.canvasHeight > 400, 'WSI canvas has stable desktop dimensions', review)
  assert(report, review.sampledColors > 40, 'WSI canvas contains nonblank tissue pixels', review)
  assert(report, review.regions === 3, 'Evidence rail and canvas share three numbered regions', review)
  assert(report, review.startVisible, 'Pathologist can start review', review)
  await click(client, '.po-start-review button')
  const decisionOptions = await evaluate(client, 'document.querySelectorAll(".po-decision-options button").length')
  assert(report, decisionOptions === 4, 'Pathologist decision panel exposes four governed actions', { decisionOptions })
  await capture(client, 'review-decision-1440.png')

  await navigate(client, baseUrl + '/#/cases/PO-26-0717-003', 1440, 900)
  await click(client, '.po-start-review button')
  await click(client, '.po-decision-options button:first-child')
  await click(client, '.po-decision-form > .el-button')
  const conditionalStatus = await evaluate(client, 'document.querySelector(".po-status-senior_review")?.textContent.trim()')
  assert(report, conditionalStatus === 'Senior review', 'Discordant case routes to conditional senior review', { conditionalStatus })

  await evaluate(client, 'localStorage.setItem("pathorchestra_demo_role", "REVIEWER")')
  await client.send('Page.reload', { ignoreCache: true })
  await settle(client)
  const reviewerOptions = await evaluate(client, 'document.querySelectorAll(".po-decision-options button").length')
  assert(report, reviewerOptions === 2, 'Senior reviewer receives approve and return actions', { reviewerOptions })
  await click(client, '.po-decision-options button:first-child')
  await click(client, '.po-decision-form > .el-button')
  const approvedStatus = await evaluate(client, 'document.querySelector(".po-status-completed")?.textContent.trim()')
  assert(report, approvedStatus === 'Completed', 'Senior approval completes the case', { approvedStatus })

  await evaluate(client, 'localStorage.setItem("pathorchestra_demo_role", "AI_DATA_ADMIN")')
  await client.send('Page.navigate', { url: baseUrl + '/#/feedback' })
  await settle(client)
  await client.send('Page.reload', { ignoreCache: true })
  await settle(client)
  const validationResult = await evaluate(client, [
    '(() => {',
    '  const row = [...document.querySelectorAll("tbody tr")].find((candidate) => candidate.textContent.includes("PO-26-0717-003"))',
    '  const button = row?.querySelector("button")',
    '  if (!button) return false',
    '  button.click()',
    '  return true',
    '})()',
  ].join('\n'))
  assert(report, validationResult, 'AI/data administrator can validate the routed feedback record')
  await settle(client)
  const feedbackStatus = await evaluate(client, [
    '(() => {',
    '  const row = [...document.querySelectorAll("tbody tr")].find((candidate) => candidate.textContent.includes("PO-26-0717-003"))',
    '  return row?.querySelector(".po-feedback-status")?.textContent.trim()',
    '})()',
  ].join('\n'))
  assert(report, feedbackStatus === 'Validated', 'Validated feedback state persists in the UI', { feedbackStatus })
  await capture(client, 'feedback-validated-1440.png')

  await evaluate(client, 'localStorage.setItem("wsi-lang", "zh-CN")')
  await client.send('Page.navigate', { url: baseUrl + '/#/cases' })
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 390,
    height: 844,
    deviceScaleFactor: 1,
    mobile: true,
    screenWidth: 390,
    screenHeight: 844,
  })
  await client.send('Page.reload', { ignoreCache: true })
  await settle(client)
  const chineseMobile = await evaluate(client, [
    '(() => ({',
    '  heading: document.querySelector("h1")?.textContent.trim(),',
    '  innerWidth: window.innerWidth,',
    '  scrollWidth: document.documentElement.scrollWidth',
    '}))()',
  ].join('\n'))
  assert(report, chineseMobile.heading === '病例队列', 'Chinese queue translation renders', chineseMobile)
  assert(report, chineseMobile.scrollWidth <= chineseMobile.innerWidth, 'Chinese mobile layout has no horizontal overflow', chineseMobile)
  await capture(client, 'dashboard-390-zh.png')

  if (localBaseUrl) {
    await navigate(client, localBaseUrl + '/login', 1440, 900)
    const localLogin = await evaluate(client, [
      '(() => ({',
      '  heading: document.querySelector(".po-auth-panel h1")?.textContent.trim(),',
      '  brand: document.querySelector(".po-auth-brand strong")?.textContent.trim(),',
      '  fields: document.querySelectorAll(".po-auth-form input").length,',
      '  innerWidth: window.innerWidth,',
      '  scrollWidth: document.documentElement.scrollWidth',
      '}))()',
    ].join('\n'))
    assert(report, localLogin.heading === 'Sign in to Pathorchestra', 'Local login uses the product authentication shell', localLogin)
    assert(report, localLogin.brand === 'Pathorchestra', 'Local login preserves the product brand signal', localLogin)
    assert(report, localLogin.fields === 2, 'Local login exposes username and password fields', localLogin)
    assert(report, localLogin.scrollWidth <= localLogin.innerWidth, 'Local desktop login has no horizontal overflow', localLogin)
    await capture(client, 'local-login-1440.png')

    await navigate(client, localBaseUrl + '/login', 390, 844, true)
    const localMobileLogin = await evaluate(client, [
      '(() => {',
      '  const panel = document.querySelector(".po-auth-panel")?.getBoundingClientRect()',
      '  return {',
      '    innerWidth: window.innerWidth,',
      '    scrollWidth: document.documentElement.scrollWidth,',
      '    panelLeft: panel?.left,',
      '    panelRight: panel?.right',
      '  }',
      '})()',
    ].join('\n'))
    assert(report, localMobileLogin.innerWidth === 390, 'Local login uses a 390px CSS viewport', localMobileLogin)
    assert(report, localMobileLogin.scrollWidth <= localMobileLogin.innerWidth, 'Local mobile login has no horizontal overflow', localMobileLogin)
    assert(report, localMobileLogin.panelLeft >= 0 && localMobileLogin.panelRight <= 390, 'Local login panel remains inside the mobile viewport', localMobileLogin)
    await capture(client, 'local-login-390.png')

    await navigate(client, localBaseUrl + '/register', 390, 844, true)
    const localRegister = await evaluate(client, [
      '(() => ({',
      '  heading: document.querySelector(".po-auth-panel h1")?.textContent.trim(),',
      '  fields: document.querySelectorAll(".po-auth-form input").length,',
      '  innerWidth: window.innerWidth,',
      '  scrollWidth: document.documentElement.scrollWidth',
      '}))()',
    ].join('\n'))
    assert(report, localRegister.heading === 'Request an account', 'Local registration uses the product authentication shell', localRegister)
    assert(report, localRegister.fields === 4, 'Local registration retains account and role inputs', localRegister)
    assert(report, localRegister.scrollWidth <= localRegister.innerWidth, 'Local mobile registration has no horizontal overflow', localRegister)
    await capture(client, 'local-register-390.png')

    await evaluate(client, [
      'localStorage.removeItem("wsi_token")',
      'localStorage.removeItem("wsi_user")',
      'localStorage.setItem("wsi-lang", "zh-CN")',
      'true',
    ].join(';'))
    await navigate(client, localBaseUrl + '/login', 390, 844, true)
    const localChineseLogin = await evaluate(client, [
      '(() => ({',
      '  heading: document.querySelector(".po-auth-panel h1")?.textContent.trim(),',
      '  innerWidth: window.innerWidth,',
      '  scrollWidth: document.documentElement.scrollWidth',
      '}))()',
    ].join('\n'))
    assert(report, localChineseLogin.heading === '登录 Pathorchestra', 'Chinese local authentication translation renders', localChineseLogin)
    assert(report, localChineseLogin.scrollWidth <= localChineseLogin.innerWidth, 'Chinese local authentication has no horizontal overflow', localChineseLogin)
    await capture(client, 'local-login-390-zh.png')

    await evaluate(client, [
      'localStorage.removeItem("wsi_token")',
      'localStorage.removeItem("wsi_user")',
      'localStorage.setItem("wsi-lang", "en-US")',
      'true',
    ].join(';'))
    await navigate(client, localBaseUrl + '/login', 1440, 900)
    await setValue(client, '#login-username', 'annotator')
    await setValue(client, '#login-password', 'anno123')
    await click(client, '.po-auth-submit')
    await waitFor(
      client,
      'window.location.pathname === "/cases"',
      'Local login did not navigate to the case queue',
    )
    const authenticatedLocal = await evaluate(client, [
      '(() => ({',
      '  path: window.location.pathname,',
      '  username: document.querySelector(".po-user-copy strong")?.textContent.trim(),',
      '  role: document.querySelector(".po-user-copy small")?.textContent.trim(),',
      '  mode: document.querySelector(".po-mode-label")?.textContent.trim(),',
      '  hasToken: Boolean(localStorage.getItem("wsi_token"))',
      '}))()',
    ].join('\n'))
    assert(report, authenticatedLocal.path === '/cases' && authenticatedLocal.hasToken, 'Local browser login stores the backend JWT and opens the case queue', authenticatedLocal)
    assert(report, authenticatedLocal.username === 'annotator' && authenticatedLocal.role === 'Pathologist', 'Backend ANNOTATOR identity maps to the Pathologist product role', authenticatedLocal)
    assert(report, authenticatedLocal.mode === 'Local system', 'Authenticated product shell exposes the local-system boundary', authenticatedLocal)
    await capture(client, 'local-authenticated-cases-1440.png')

    await click(client, '.po-nav-link[href="/workspaces"]')
    await waitFor(
      client,
      'document.querySelector(".library-header h1")?.textContent.trim() === "Slide library"',
      'Local slide library did not render after authentication',
    )
    const localLibrary = await evaluate(client, [
      '(() => ({',
      '  heading: document.querySelector(".library-header h1")?.textContent.trim(),',
      '  casesLink: [...document.querySelectorAll(".po-nav-link")].some((link) => link.textContent.includes("Cases")),',
      '  libraryLink: [...document.querySelectorAll(".po-nav-link")].some((link) => link.textContent.includes("Slide library")),',
      '  errors: document.querySelectorAll(".el-message--error").length,',
      '  innerWidth: window.innerWidth,',
      '  scrollWidth: document.documentElement.scrollWidth',
      '}))()',
    ].join('\n'))
    assert(report, localLibrary.heading === 'Slide library', 'Authenticated slide library uses the product shell', localLibrary)
    assert(report, localLibrary.casesLink && localLibrary.libraryLink, 'Authenticated slide library links back to the clinical case queue', localLibrary)
    assert(report, localLibrary.errors === 0 && localLibrary.scrollWidth <= localLibrary.innerWidth, 'Authenticated slide library loads through the backend without UI errors', localLibrary)

    await click(client, '.library-actions .el-button--primary')
    await setValue(client, '.el-dialog input', 'UI integration workspace')
    await setValue(client, '.el-dialog textarea', 'Created through the authenticated Pathorchestra frontend')
    await click(client, '.el-dialog__footer .el-button--primary')
    await waitFor(
      client,
      '[...document.querySelectorAll(".ws-card .card-title")].some((element) => element.textContent.trim() === "UI integration workspace")',
      'Workspace created in the browser did not appear in the library',
    )
    const linkedWorkspace = await evaluate(client, [
      '(() => ({',
      '  cardVisible: [...document.querySelectorAll(".ws-card .card-title")].some((element) => element.textContent.trim() === "UI integration workspace"),',
      '  successVisible: [...document.querySelectorAll(".el-message--success")].some((element) => element.textContent.includes("UI integration workspace"))',
      '}))()',
    ].join('\n'))
    assert(report, linkedWorkspace.cardVisible, 'Authenticated frontend creates and renders a PostgreSQL-backed workspace', linkedWorkspace)
    await capture(client, 'local-library-linked-1440.png')
  }

  await writeFile(join(outputDirectory, 'report.json'), JSON.stringify(report, null, 2))
  return report
}

await mkdir(outputDirectory, { recursive: true })
const profileDirectory = await mkdtemp(join(tmpdir(), 'pathorchestra-edge-profile-'))
const edge = spawn(
  edgePath,
  [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--no-first-run',
    '--remote-debugging-port=' + debuggingPort,
    '--user-data-dir=' + profileDirectory,
    'about:blank',
  ],
  {
    stdio: 'ignore',
    windowsHide: true,
  },
)

let client
try {
  const endpoint = await waitForPageEndpoint()
  client = new CdpClient(endpoint)
  await client.connect()
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  const report = await runSuite(client)
  console.log(JSON.stringify({
    ok: true,
    checks: report.checks.length,
    outputDirectory,
  }, null, 2))
} finally {
  client?.close()
  edge.kill()
  await Promise.race([
    new Promise((resolveExit) => edge.once('exit', resolveExit)),
    delay(1000),
  ])
  if (profileDirectory.startsWith(tmpdir())) {
    try {
      await rm(profileDirectory, { recursive: true, force: true })
    } catch {
      // Edge Crashpad can retain a temp-file handle briefly after the browser exits.
    }
  }
}
