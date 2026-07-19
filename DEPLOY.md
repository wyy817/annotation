# Pathorchestra Public Demo Deployment

This describes how to publish the **static public demo** to the internet. It does
**not** deploy the clinical system. The Spring API, PostgreSQL, Python GPU
service, and LLM key stay on your local machine and are never exposed.

Host: **Netlify** (free tier, automatic HTTPS). No server, no database, no build
server required — you upload a folder of static files.

## The one rule that matters

**Only `frontend/dist` (the demo build output) ever goes public.**

Never publish the repository, the `backend/`, the `python-service/`, or the LLM
relay configuration. The demo build is deliberately self-contained: no
registration, no upload, no live inference, no API key, no patient data — only
precomputed CAMELYON evidence and browser-local (`localStorage`) workflow state.

## Step 1 — Build the demo

```powershell
cd frontend
npm run build:demo
```

This writes the deployable site to `frontend/dist`. It contains:

- `index.html` — the app entry (hash-routed, so URLs look like `.../#/cases`).
- `assets/` — fingerprinted JS/CSS bundles.
- `demo-ai/` — precomputed positive/negative CAMELYON evidence.
- `_headers` — security + `noindex` headers (from `frontend/public/_headers`).
- `robots.txt` — asks search engines not to crawl (from `frontend/public/`).

It contains **no** source WSI, patient identity, API key, registration, or upload
endpoint. (Verified: the bundle has zero secret-like strings and makes no live
`/api/...` calls.)

## Step 2 — First deploy (drag-and-drop, ~2 minutes)

1. Open <https://app.netlify.com/drop> in your browser.
2. Drag the whole **`frontend/dist`** folder onto the page.
3. Netlify uploads it and gives you a live HTTPS URL like
   `https://random-name-123.netlify.app`.
4. Open it and click through the demo (positive case `PO-26-0717-001`, negative
   case `PO-26-0717-006`) to confirm it works.

At this point the site is public but has a temporary name and is not tied to an
account.

## Step 3 — Keep it and give it a tidy name

1. Sign up for a free Netlify account (GitHub or email) and, when prompted,
   **claim** the site you just dropped so it becomes permanent.
2. In **Site configuration → Change site name**, rename it to something clean,
   e.g. `pathorchestra-demo` → `https://pathorchestra-demo.netlify.app`.

## Step 4 — Updating the demo later

Whenever you change the frontend and want to republish:

```powershell
cd frontend
npm run build:demo
```

Then, on your site's **Deploys** tab in Netlify, drag the new `frontend/dist`
onto the deploy drop zone. It redeploys to the **same URL**. (Optional: install
the Netlify CLI once with `npm i -g netlify-cli`, run `netlify login`, then
`netlify deploy --prod --dir=frontend/dist` for a one-command deploy.)

## Optional — Content Security Policy (stricter, test before keeping)

A CSP further hardens the site but can blank the page if too strict, so it is
**not** enabled by default. To try it, add this block to `frontend/public/_headers`
under the existing `/*` section, rebuild, redeploy, then hard-refresh and check
the browser console. If the page renders and the console is clean, keep it; if
the page is blank, remove it.

```
  Content-Security-Policy: default-src 'self'; img-src 'self' data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; base-uri 'self'; frame-ancestors 'none'
```

`style-src 'unsafe-inline'` is required because the UI library injects inline
styles. The demo makes no cross-origin requests, so `connect-src 'self'` is safe.

## Optional — Custom domain later

If you buy a domain (e.g. `pathorchestra.com`), add it under **Domain
management** in Netlify and follow its DNS instructions. Netlify issues the TLS
certificate automatically. Nothing about the build changes.

## Pre-publish safety checklist

- [ ] You are uploading **`frontend/dist`**, not the repository or `backend/`.
- [ ] `npm run build:demo` (not `npm run build`) produced the folder — the demo
      mode is what removes registration/upload/live inference.
- [ ] The uploaded folder contains `demo-ai/`, `_headers`, and `robots.txt`.
- [ ] The private repo stays private; the LLM relay config is never uploaded.
