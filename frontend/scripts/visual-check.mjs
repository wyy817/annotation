import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'
import { createServer } from 'vite'

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const outputDir = new URL('../artifacts/visual/', import.meta.url)
const projectRoot = fileURLToPath(new URL('../', import.meta.url))

await mkdir(outputDir, { recursive: true })

const demoServer = await createServer({
  root: projectRoot,
  mode: 'demo',
  server: {
    host: '127.0.0.1',
    port: 5175,
    strictPort: true,
  },
})
await demoServer.listen()

const localServer = await createServer({
  root: projectRoot,
  mode: 'development',
  server: {
    host: '127.0.0.1',
    port: 5176,
    strictPort: true,
  },
})
await localServer.listen()

const browser = await chromium.launch({
  executablePath: edgePath,
  headless: true,
})

const results = []

async function inspectPage(page, name, url, screenshotName) {
  const consoleErrors = []
  const failedResponses = []
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push({ text: message.text(), location: message.location() })
    }
  })
  page.on('pageerror', (error) => consoleErrors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 400) {
      failedResponses.push({ status: response.status(), url: response.url() })
    }
  })

  await page.goto(url, { waitUntil: 'networkidle' })
  await page.locator('.po-real-viewer img').first().waitFor({ state: 'visible' })
  await page.waitForFunction(() => {
    const image = document.querySelector('.po-real-viewer img')
    return image?.naturalWidth > 100 && image?.naturalHeight > 100
  })
  if (!name.includes('negative')) {
    await page.locator('.po-real-region.selected').first().waitFor({ state: 'visible' })
    await page.locator('.po-evidence-patch img').waitFor({
      state: name.includes('mobile') ? 'attached' : 'visible',
    })
    await page.waitForFunction(() => {
      const patch = document.querySelector('.po-evidence-patch img')
      return patch?.naturalWidth > 100 && patch?.naturalHeight > 100
    })
  }

  const pageState = await page.evaluate(() => {
    const image = document.querySelector('.po-real-viewer img')
    const viewer = document.querySelector('.po-real-viewer')
    const rail = document.querySelector('.po-real-evidence')
    const sidebar = document.querySelector('.po-review-sidebar')
    const patch = document.querySelector('.po-evidence-patch img')
    const rect = (element) => element ? element.getBoundingClientRect().toJSON() : null
    return {
      viewport: { width: window.innerWidth, height: window.innerHeight },
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 1,
      brokenImages: [...document.images].filter((item) => !item.complete || item.naturalWidth === 0).length,
      evidenceImage: image ? {
        naturalWidth: image.naturalWidth,
        naturalHeight: image.naturalHeight,
      } : null,
      evidencePatch: patch ? {
        naturalWidth: patch.naturalWidth,
        naturalHeight: patch.naturalHeight,
      } : null,
      selectedCandidate: document.querySelector('.po-real-region.selected')?.textContent.trim() || null,
      viewer: rect(viewer),
      rail: rect(rail),
      sidebar: rect(sidebar),
      decisionActionVisible: Boolean(
        document.querySelector('.po-decision-actions')?.getClientRects().length,
      ),
      bodyText: document.body.innerText,
    }
  })

  await page.screenshot({
    path: new URL(screenshotName, outputDir).pathname.slice(1),
    fullPage: true,
  })

  // Each mode anchors on the meaningful content it is expected to render:
  // - negative demo case: no candidate region passed the gate.
  // - completed local case: its persisted validated live-model report. A real
  //   analysis of an uploaded slide has no ground-truth annotation overlap, so
  //   it never shows the demo's "Public annotation compatibility" validation.
  // - positive demo case: precomputed public annotation-overlap validation.
  const requiredText = name.includes('negative')
    ? 'No candidate region passed'
    : name.startsWith('local-')
      ? 'Validated live model response'
      : 'Public annotation compatibility'
  const liveReportRendered = pageState.bodyText.includes('Validated live model response')
  const passed = !pageState.horizontalOverflow
    && pageState.brokenImages === 0
    && pageState.evidenceImage?.naturalWidth > 100
    && (name.includes('negative') || pageState.evidencePatch?.naturalWidth > 100)
    && (name.includes('negative') || Boolean(pageState.selectedCandidate))
    && pageState.bodyText.includes(requiredText)
    && (!name.includes('mobile') || pageState.decisionActionVisible)
    && consoleErrors.length === 0
    && failedResponses.length === 0

  results.push({
    name,
    passed,
    horizontalOverflow: pageState.horizontalOverflow,
    brokenImages: pageState.brokenImages,
    evidenceImage: pageState.evidenceImage,
    evidencePatch: pageState.evidencePatch,
    selectedCandidate: pageState.selectedCandidate,
    viewer: pageState.viewer,
    rail: pageState.rail,
    sidebar: pageState.sidebar,
    decisionActionVisible: pageState.decisionActionVisible,
    liveReportRendered,
    consoleErrors,
    failedResponses,
  })
}

async function inspectDecisionFlow(page) {
  const consoleErrors = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => consoleErrors.push(error.message))

  await page.goto(
    'http://127.0.0.1:5175/#/cases/PO-26-0717-001',
    { waitUntil: 'networkidle' },
  )
  await page.getByRole('button', { name: 'Start review' }).click()
  await page.locator('.po-decision-options button').filter({ hasText: 'Accept' }).click()
  await page.getByRole('button', { name: 'Record decision' }).click()
  await page.getByText('Current decision').waitFor()
  await page.getByText('Precomputed public demonstration').waitFor()
  await page.getByText('Clinician review required').last().waitFor()
  await page.waitForTimeout(3500)
  await page.evaluate(() => {
    document.activeElement?.blur()
    window.scrollTo(0, 0)
  })
  await page.waitForTimeout(200)
  await page.screenshot({
    path: new URL('demo-report-after-decision.png', outputDir).pathname.slice(1),
    fullPage: true,
  })
  const reportVisible = await page.getByText('Precomputed public demonstration').isVisible()
  await page.getByRole('link', { name: 'Feedback pool' }).click()
  await page.getByText('PO-26-0717-001').first().waitFor()

  const feedbackVisible = await page.getByText('PO-26-0717-001').first().isVisible()
  results.push({
    name: 'demo-decision-feedback-flow',
    passed: reportVisible && feedbackVisible && consoleErrors.length === 0,
    reportVisible,
    feedbackVisible,
    consoleErrors,
  })
}

async function inspectLocalWsiFlow(page) {
  const consoleErrors = []
  const failedResponses = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => consoleErrors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 400) {
      failedResponses.push({ status: response.status(), url: response.url() })
    }
  })

  await page.goto('http://127.0.0.1:5176/login', { waitUntil: 'networkidle' })
  await page.locator('#login-username').fill('annotator')
  await page.locator('#login-password').fill('anno123')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.waitForURL('**/cases')

  await page.goto('http://127.0.0.1:5176/workspace/5', { waitUntil: 'networkidle' })
  await page.locator('.thumb-img').first().waitFor({ state: 'visible' })
  await page.waitForFunction(() => {
    const thumbnails = [...document.querySelectorAll('.thumb-img')]
    return thumbnails.length >= 2
      && thumbnails.every((image) => image.complete && image.naturalWidth > 0)
  })
  const thumbnailState = await page.locator('.thumb-img').evaluateAll((images) => (
    images.map((image) => ({
      width: image.naturalWidth,
      height: image.naturalHeight,
    }))
  ))
  await page.screenshot({
    path: new URL('local-wsi-workspace.png', outputDir).pathname.slice(1),
    fullPage: true,
  })

  const viewerStates = []
  for (const slideId of [4, 5]) {
    await page.goto(`http://127.0.0.1:5176/slide/${slideId}/view`, {
      waitUntil: 'networkidle',
    })
    await page.locator('.coord-bar').waitFor({ state: 'visible', timeout: 30_000 })
    await page.waitForFunction(() => {
      const viewer = document.querySelector('.osd-container')
      return viewer
        && !document.querySelector('.loading-overlay')
        && !document.querySelector('.error-overlay')
        && viewer.querySelectorAll('canvas').length > 0
    })
    await page.waitForTimeout(500)

    const state = await page.evaluate(() => ({
      errorVisible: Boolean(document.querySelector('.error-overlay')),
      canvasCount: document.querySelectorAll('.osd-container canvas').length,
      slideName: document.querySelector('.slide-name')?.textContent.trim(),
      coordinateBar: document.querySelector('.coord-bar')?.textContent.trim(),
    }))
    viewerStates.push({ slideId, ...state })
    await page.screenshot({
      path: new URL(`local-wsi-slide-${slideId}.png`, outputDir).pathname.slice(1),
      fullPage: true,
    })
  }

  results.push({
    name: 'local-wsi-thumbnail-viewer-flow',
    passed: thumbnailState.length >= 2
      && thumbnailState.every((item) => item.width > 0 && item.height > 0)
      && viewerStates.every((item) => !item.errorVisible && item.canvasCount > 0)
      && consoleErrors.length === 0
      && failedResponses.length === 0,
    thumbnailState,
    viewerStates,
    consoleErrors,
    failedResponses,
  })
}

async function inspectDemoAccessBoundary(page) {
  const forbiddenCopy = ['Create account', 'Upload slide', 'Workspace files']
  const redirects = []
  for (const path of ['/register', '/workspace/1']) {
    await page.goto(`http://127.0.0.1:5175/#${path}`, { waitUntil: 'networkidle' })
    await page.waitForURL('**/#/cases')
    redirects.push({ path, target: page.url() })
  }
  const bodyText = await page.locator('body').innerText()
  results.push({
    name: 'demo-access-boundary',
    passed: redirects.length === 2
      && redirects.every((item) => item.target.endsWith('/#/cases'))
      && forbiddenCopy.every((copy) => !bodyText.includes(copy)),
    redirects,
    forbiddenCopyVisible: forbiddenCopy.filter((copy) => bodyText.includes(copy)),
  })
}

async function runStep(label, fn) {
  try {
    await fn()
  } catch (error) {
    results.push({ name: label, passed: false, error: error?.message || String(error) })
  }
}

try {
  await runStep('demo-positive-desktop', async () => {
    const demoDesktop = await browser.newPage({ viewport: { width: 1536, height: 960 } })
    try {
      await inspectPage(
        demoDesktop,
        'demo-positive-desktop',
        'http://127.0.0.1:5175/#/cases/PO-26-0717-001',
        'demo-positive-desktop.png',
      )
    } finally {
      await demoDesktop.close()
    }
  })

  await runStep('demo-negative-desktop', async () => {
    const demoNegative = await browser.newPage({ viewport: { width: 1536, height: 960 } })
    try {
      await inspectPage(
        demoNegative,
        'demo-negative-desktop',
        'http://127.0.0.1:5175/#/cases/PO-26-0717-006',
        'demo-negative-desktop.png',
      )
    } finally {
      await demoNegative.close()
    }
  })

  await runStep('demo-positive-mobile', async () => {
    const demoMobile = await browser.newPage({
      viewport: { width: 390, height: 844 },
      deviceScaleFactor: 1,
      isMobile: true,
      hasTouch: true,
    })
    try {
      await inspectPage(
        demoMobile,
        'demo-positive-mobile',
        'http://127.0.0.1:5175/#/cases/PO-26-0717-001',
        'demo-positive-mobile.png',
      )
    } finally {
      await demoMobile.close()
    }
  })

  await runStep('demo-decision-feedback-flow', async () => {
    const demoDecision = await browser.newPage({ viewport: { width: 1280, height: 800 } })
    try {
      await inspectDecisionFlow(demoDecision)
    } finally {
      await demoDecision.close()
    }
  })

  await runStep('demo-access-boundary', async () => {
    const demoBoundary = await browser.newPage({ viewport: { width: 1280, height: 800 } })
    try {
      await inspectDemoAccessBoundary(demoBoundary)
    } finally {
      await demoBoundary.close()
    }
  })

  await runStep('local-positive-desktop', async () => {
    const local = await browser.newPage({ viewport: { width: 1536, height: 960 } })
    try {
      await local.goto('http://127.0.0.1:5176/login', { waitUntil: 'networkidle' })
      await local.locator('#login-username').fill('annotator')
      await local.locator('#login-password').fill('anno123')
      await local.getByRole('button', { name: 'Sign in' }).click()
      await local.waitForURL('**/cases')
      await inspectPage(
        local,
        'local-positive-desktop',
        'http://127.0.0.1:5176/cases/PO-26-0717-001',
        'local-positive-desktop.png',
      )
    } finally {
      await local.close()
    }
  })

  await runStep('local-wsi-thumbnail-viewer-flow', async () => {
    const localWsi = await browser.newPage({ viewport: { width: 1536, height: 960 } })
    try {
      await inspectLocalWsiFlow(localWsi)
    } finally {
      await localWsi.close()
    }
  })
} finally {
  await browser.close()
  await demoServer.close()
  await localServer.close()
}

console.log(JSON.stringify(results, null, 2))

if (results.some((result) => !result.passed)) process.exitCode = 1
