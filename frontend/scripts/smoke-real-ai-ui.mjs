import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const DEBUG_ORIGIN = process.env.EDGE_DEBUG_ORIGIN || 'http://127.0.0.1:9222'
const APP_ORIGIN = process.env.APP_ORIGIN || 'http://127.0.0.1:5173'
const OUTPUT_DIR = path.resolve('screenshots')

const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds))

class CdpClient {
  constructor(socket) {
    this.socket = socket
    this.nextId = 1
    this.pending = new Map()

    socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data)
      if (!message.id) return
      const request = this.pending.get(message.id)
      if (!request) return
      this.pending.delete(message.id)
      if (message.error) request.reject(new Error(message.error.message))
      else request.resolve(message.result)
    })
  }

  static async connect(webSocketUrl) {
    const socket = new WebSocket(webSocketUrl)
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true })
      socket.addEventListener('error', reject, { once: true })
    })
    return new CdpClient(socket)
  }

  send(method, params = {}) {
    const id = this.nextId++
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.socket.send(JSON.stringify({ id, method, params }))
    })
  }

  close() {
    this.socket.close()
  }
}

async function createTarget(url) {
  const response = await fetch(`${DEBUG_ORIGIN}/json/new?${encodeURIComponent(url)}`, {
    method: 'PUT',
  })
  if (!response.ok) throw new Error(`Unable to create Edge target: HTTP ${response.status}`)
  return response.json()
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

async function waitForDocument(client, expectedUrl) {
  let lastState = null
  for (let attempt = 0; attempt < 80; attempt += 1) {
    lastState = await evaluate(client, `({
      readyState: document.readyState,
      url: window.location.href,
    })`)
    if (lastState.readyState === 'complete' && lastState.url.startsWith(expectedUrl)) return
    await sleep(100)
  }
  throw new Error(`Timed out waiting for ${expectedUrl}; last state: ${JSON.stringify(lastState)}`)
}

async function waitForEvidence(client) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    const state = await evaluate(client, `(() => ({
      viewer: Boolean(document.querySelector('.po-real-viewer')),
      regions: document.querySelectorAll('.po-real-region').length,
      imageCount: document.querySelectorAll('.po-real-viewer img').length,
      imagesReady: [...document.querySelectorAll('.po-real-viewer img')]
        .every((image) => image.complete && image.naturalWidth > 0),
    }))()`)
    if (state.viewer && state.regions === 5 && state.imageCount >= 2 && state.imagesReady) return state
    await sleep(250)
  }
  throw new Error('Timed out waiting for five real AI candidate regions and evidence images')
}

async function inspectLayout(client) {
  return evaluate(client, `(() => {
    const rect = (selector) => {
      const element = document.querySelector(selector)
      if (!element) return null
      const box = element.getBoundingClientRect()
      if (box.width === 0 || box.height === 0) return null
      return { x: box.x, y: box.y, width: box.width, height: box.height,
        right: box.right, bottom: box.bottom }
    }
    const overlaps = (first, second) => Boolean(first && second
      && first.x < second.right && first.right > second.x
      && first.y < second.bottom && first.bottom > second.y)
    const viewer = rect('.po-real-viewer')
    const evidence = rect('.po-review-evidence')
    const sidebar = rect('.po-review-sidebar')
    const toolbar = rect('.po-real-viewer-toolbar')
    return {
      title: document.title,
      bodyWidth: document.documentElement.scrollWidth,
      viewportWidth: document.documentElement.clientWidth,
      regionCount: document.querySelectorAll('.po-real-region').length,
      loadedImages: [...document.querySelectorAll('.po-real-viewer img')]
        .filter((image) => image.complete && image.naturalWidth > 0).length,
      viewer,
      evidence,
      sidebar,
      toolbar,
      viewerOverlapsEvidence: overlaps(viewer, evidence),
      toolbarOutsideViewer: Boolean(toolbar && viewer && (
        toolbar.x < viewer.x || toolbar.y < viewer.y
        || toolbar.right > viewer.right || toolbar.bottom > viewer.bottom
      )),
    }
  })()`)
}

async function capture(client, name) {
  const metrics = await client.send('Page.getLayoutMetrics')
  const content = metrics.cssContentSize || metrics.contentSize
  const screenshot = await client.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
    captureBeyondViewport: true,
    clip: {
      x: 0,
      y: 0,
      width: Math.min(Math.ceil(content.width), 1800),
      height: Math.min(Math.ceil(content.height), 2400),
      scale: 1,
    },
  })
  const filePath = path.join(OUTPUT_DIR, name)
  await writeFile(filePath, Buffer.from(screenshot.data, 'base64'))
  return filePath
}

async function setViewport(client, width, height, mobile = false) {
  await client.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: 1,
    mobile,
    screenWidth: width,
    screenHeight: height,
  })
}

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true })
  const target = await createTarget(`${APP_ORIGIN}/login`)
  const client = await CdpClient.connect(target.webSocketDebuggerUrl)

  try {
    await client.send('Page.enable')
    await client.send('Runtime.enable')
    await setViewport(client, 1440, 1000)
    await waitForDocument(client, `${APP_ORIGIN}/login`)

    await evaluate(client, `(async () => {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'annotator', password: 'anno123' }),
      })
      const payload = await response.json()
      if (!response.ok || payload.code !== 200) throw new Error('Local smoke login failed')
      localStorage.setItem('wsi_token', payload.data.token)
      localStorage.setItem('wsi_user', JSON.stringify(payload.data.user))
      return true
    })()`)

    await client.send('Page.navigate', { url: `${APP_ORIGIN}/cases/PO-26-0717-001` })
    await waitForDocument(client, `${APP_ORIGIN}/cases/PO-26-0717-001`)
    await waitForEvidence(client)

    const desktop = await inspectLayout(client)
    const desktopScreenshot = await capture(client, 'real-ai-desktop.png')
    if (desktop.bodyWidth > desktop.viewportWidth || desktop.viewerOverlapsEvidence
      || desktop.toolbarOutsideViewer) {
      throw new Error(`Desktop layout assertion failed: ${JSON.stringify(desktop)}`)
    }

    await setViewport(client, 390, 844, true)
    await sleep(800)
    const mobile = await inspectLayout(client)
    const mobileScreenshot = await capture(client, 'real-ai-mobile.png')
    if (mobile.bodyWidth > mobile.viewportWidth || mobile.toolbarOutsideViewer) {
      throw new Error(`Mobile layout assertion failed: ${JSON.stringify(mobile)}`)
    }

    process.stdout.write(`${JSON.stringify({
      desktop,
      mobile,
      screenshots: { desktopScreenshot, mobileScreenshot },
    }, null, 2)}\n`)
  } finally {
    client.close()
    await fetch(`${DEBUG_ORIGIN}/json/close/${target.id}`)
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`)
  process.exitCode = 1
})
