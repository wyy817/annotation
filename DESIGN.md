---
name: PARVISIGHT / 观隅
description: A precise, restrained evidence workspace for AI-assisted pathology review.
colors:
  harbor-cobalt: "oklch(0.45 0.10 230)"
  harbor-cobalt-hover: "oklch(0.39 0.09 230)"
  harbor-cobalt-soft: "oklch(0.95 0.025 230)"
  eosin-rose: "oklch(0.68 0.16 350)"
  eosin-rose-soft: "oklch(0.95 0.035 350)"
  cool-canvas: "oklch(0.975 0.006 230)"
  paper-white: "oklch(1 0 0)"
  surface-subtle: "oklch(0.955 0.008 230)"
  clinical-ink: "oklch(0.22 0.018 230)"
  clinical-ink-muted: "oklch(0.44 0.025 230)"
  hairline: "oklch(0.87 0.012 230)"
  viewer-canvas: "oklch(0.12 0.008 230)"
  viewer-panel: "oklch(0.18 0.012 230)"
  viewer-ink: "oklch(0.94 0.006 230)"
  risk-high: "oklch(0.47 0.17 25)"
  risk-high-soft: "oklch(0.95 0.035 25)"
  attention: "oklch(0.42 0.10 75)"
  attention-soft: "oklch(0.95 0.055 80)"
  verified: "oklch(0.40 0.10 155)"
  verified-soft: "oklch(0.95 0.04 155)"
typography:
  display:
    fontFamily: "Inter, Noto Sans SC, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "28px"
    fontWeight: 650
    lineHeight: 1.2
    letterSpacing: "normal"
  headline:
    fontFamily: "Inter, Noto Sans SC, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "22px"
    fontWeight: 650
    lineHeight: 1.25
    letterSpacing: "normal"
  section:
    fontFamily: "Inter, Noto Sans SC, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: "normal"
  body:
    fontFamily: "Inter, Noto Sans SC, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "Inter, Noto Sans SC, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "12px"
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: "normal"
  data:
    fontFamily: "SFMono-Regular, Consolas, Liberation Mono, monospace"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "normal"
rounded:
  sharp: "2px"
  control: "6px"
  panel: "8px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  2xl: "32px"
  3xl: "40px"
components:
  button-primary:
    backgroundColor: "{colors.harbor-cobalt}"
    textColor: "{colors.paper-white}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0 14px"
    height: "36px"
  button-primary-hover:
    backgroundColor: "{colors.harbor-cobalt-hover}"
    textColor: "{colors.paper-white}"
  button-secondary:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.clinical-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0 14px"
    height: "36px"
  field-default:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.clinical-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "0 12px"
    height: "36px"
  navigation-active:
    backgroundColor: "{colors.harbor-cobalt-soft}"
    textColor: "{colors.harbor-cobalt}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "0 10px"
    height: "36px"
  status-high-risk:
    backgroundColor: "{colors.risk-high-soft}"
    textColor: "{colors.risk-high}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 8px"
    height: "24px"
  queue-row:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.clinical-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sharp}"
    padding: "0 16px"
    height: "52px"
  evidence-panel:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.clinical-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.panel}"
    padding: "16px"
  viewer-tool:
    backgroundColor: "{colors.viewer-panel}"
    textColor: "{colors.viewer-ink}"
    rounded: "{rounded.control}"
    size: "36px"
---

## Overview

**Creative North Star: "The Quiet Evidence Bench"**

PARVISIGHT is designed as a quiet evidence bench: a disciplined clinical work surface where cases, slide evidence, report evidence, and human decisions are arranged for comparison without visual theater. The interface earns trust through stable geometry, explicit uncertainty, traceable status, and a clear distinction between AI suggestion and clinician judgment.

Light, low-chroma product surfaces carry the queue and administrative workflow; the WSI canvas becomes dark only when darkness improves tissue inspection. Information density is deliberate rather than crowded: rows and panels stay compact, primary actions are scarce, and every state change is visible without moving the user's work.

**Key Characteristics:**

- Calm evidence density
- Clinical judgment first
- Flat, bounded surfaces
- Dark only where the slide needs it
- Bilingual by construction

The main desktop composition is a stable application frame with restrained navigation, a scan-friendly work queue, and a focused review workspace. At 1024px, secondary metadata may collapse behind an explicit details control while the case identity, risk, status, and next action remain visible. Below 640px, the product exposes a read-only case queue; WSI review and annotation remain desktop tasks.

## Colors

The palette resembles a clean pathology bench at first light: white paper, cool instrument gray, deep harbor blue controls, and a measured eosin note that links image evidence to annotation without becoming decorative.

### Primary

- **Harbor Cobalt:** Reserved for the current primary command, active navigation, selection, and keyboard focus. Its low-saturation authority should read as operational, not promotional.
- **Harbor Cobalt Soft:** Supports selected rows, focused regions, and quiet informational context without creating blue card fields.

### Evidence Accent

- **Eosin Rose:** Connects pathology evidence across the heatmap legend, tissue callouts, annotation outlines, and matched report excerpts. It is not a status color and must never mean success, failure, or urgency.
- **Eosin Rose Soft:** Provides a sparse evidence background or association highlight where an outline alone is insufficient.

### Neutral

- **Cool Canvas and Paper White:** The application shell uses Cool Canvas; working tables, toolbars, and bounded panels use Paper White. This is a cool clinical white system, not a beige or blue-gray theme.
- **Clinical Ink and Muted Ink:** Clinical Ink carries decisions and primary labels. Muted Ink carries metadata and helper copy while retaining WCAG 2.2 AA contrast on light surfaces.
- **Hairline:** One-pixel dividers establish hierarchy in place of resting shadows.
- **Viewer Canvas, Viewer Panel, and Viewer Ink:** These tokens are confined to the WSI inspection environment and its immediate controls.

### Semantic

- **Risk High:** Signals high-priority or discordant findings. Pair it with a written state, value, icon, or pattern.
- **Attention:** Signals pending review, low confidence, or an incomplete requirement. It never substitutes for high risk.
- **Verified:** Signals accepted, reviewed, or complete states. It does not certify clinical validity.

### Named Rules

**The Ten Percent Rule.** Harbor Cobalt and Eosin Rose together occupy no more than ten percent of a light product screen; their scarcity preserves hierarchy.

**The Evidence Color Rule.** Eosin Rose only connects pathology evidence and annotation. Risk, attention, and verified states always use their dedicated semantic tokens and a non-color cue.

## Typography

**Display Font:** Inter with Noto Sans SC and native system fallbacks.

**Body Font:** Inter with Noto Sans SC and native system fallbacks.

**Data Font:** SFMono-Regular with Consolas and Liberation Mono fallbacks.

The type system is a single, neutral workhorse family across English and Chinese. It prioritizes compact scanning and stable bilingual layouts over editorial contrast. Monospace appears only where character alignment or immutable identity matters.

### Hierarchy

- **Display:** Product page titles and the highest-level task context. Never use it for dashboard metrics or card labels.
- **Headline:** Case title, slide title, or primary review heading.
- **Section:** Panel headings, table group headings, and modal titles.
- **Body:** Case metadata, evidence excerpts, field values, and standard controls.
- **Label:** Table headers, compact controls, chips, and metadata labels. Use sentence case, not all caps.
- **Data:** Accession IDs, slide IDs, model versions, timestamps, coordinates, and confidence values.

All type uses normal letter spacing. Keep line length near 72 characters for report excerpts and long evidence text. Numeric risk values use tabular figures when supported. Do not shrink text responsively; reflow or disclose secondary content instead.

### Named Rules

**The One Workhorse Rule.** Use one sans-serif family for product language in both English and Chinese; reserve monospace for identifiers and aligned data, never for atmosphere.

## Elevation

The system is flat by default. Depth comes from tonal surface changes, one-pixel hairlines, adjacency, and clear spacing. Resting cards, queue rows, toolbars, and viewer panels have no shadow. Shadows are reserved for content that physically overlays the current work: menus, tooltips, popovers, modals, and transient viewer controls.

### Shadow Vocabulary

- **Popover:** A compact soft shadow for menus, date pickers, and evidence popovers.
- **Modal:** A broader shadow for dialogs that interrupt the current task.
- **Viewer Overlay:** A denser dark-environment shadow for floating controls over slide imagery.

### Named Rules

**The Flat-by-Default Rule.** Surfaces are flat at rest; introduce shadow only when an element overlays another layer or is actively lifted by interaction.

## Components

Components are compact, stable, and state-complete. Their dimensions do not change on hover, selection, loading, translation, or role switching. Familiar commands use Lucide icons in implementation, with visible tooltips for icon-only controls.

### Buttons

- **Shape:** Compact rectangular controls with gently eased corners at 6px.
- **Primary:** Harbor Cobalt with white text, 36px height, and one clear primary action per task region.
- **Secondary:** Paper White with Clinical Ink and a one-pixel Hairline border. Hover uses Surface Subtle rather than shadow.
- **Destructive:** Use Risk High only for an irreversible command and require explicit confirmation.
- **States:** Hover and active states adjust color without changing dimensions. Focus uses a visible Harbor Cobalt outline with separation from the control edge. Loading preserves the label width.

### Chips

- **Style:** Semantic soft background, dark semantic text, compact 24px height, and a written label. A dot, icon, value, or pattern reinforces the meaning.
- **State:** Status chips report state and are not interactive. Filter chips use a distinct selected outline and expose pressed state to assistive technology.

### Cards / Containers

- **Corner Style:** Bounded tools and repeated records use at most 8px corners. Page sections remain unframed.
- **Background:** Paper White on Cool Canvas, separated by Hairline borders or tonal adjacency.
- **Grouping:** Never place a card inside another card. Use dividers, headings, and spacing within a single bounded tool.
- **Queue Rows:** Use stable 52px rows for the default density. Preserve case ID, specimen, risk, status, owner, and next action as the scan path.

### Inputs / Fields

- **Style:** Paper White, one-pixel Hairline border, 6px corners, 36px default height, and an external persistent label.
- **Focus:** Shift the border to Harbor Cobalt and add a separated focus outline; do not use a glow.
- **Validation:** Pair semantic color with a concise message and an icon. Do not communicate validation through placeholder text.

### Navigation

- **Desktop:** A stable app-level rail or header uses labels with Lucide icons. The active destination uses Harbor Cobalt text plus a tonal background or edge indicator, never a large filled tab.
- **Mobile:** Navigation leads to the read-only queue and case summary only. Viewer and annotation actions are absent rather than disabled without explanation.
- **Roles and language:** Role switching and language switching are utilities, visually secondary to case work, and preserve the current route where permitted.

### Case Queue and Evidence

- **Case Queue:** Prefer a single sortable table or aligned list over a dashboard of summary cards. Risk order is visible, but urgent labels never overpower the case identity.
- **Evidence Panel:** Keep image evidence, report evidence, confidence, provenance, and limitations in a consistent reading order. Eosin Rose provides the association cue; semantic state remains separate.
- **Decision Bar:** Accept, Modify, Reject, and Escalate remain visible beside the evidence under review. A decision produces an immediate status update and auditable confirmation without navigating away.
- **WSI Tools:** Viewer controls are 36px square, dark, and icon-led. Selected tools use a Harbor Cobalt outline; annotation evidence uses Eosin Rose on the tissue layer.

### Named Rules

**The State Motion Rule.** Use 150-250ms transitions to explain hover, focus, selection, panel disclosure, and saved state; remove nonessential motion under reduced-motion preferences and never choreograph page entrances.

## Do's and Don'ts

### Do:

- Do keep Harbor Cobalt to one primary command, selection, navigation, or focus role at a time and within the Ten Percent Rule.
- Do use Eosin Rose only to connect heatmap, tissue, annotation, and report evidence.
- Do keep the queue and administration surfaces light, using Paper White on Cool Canvas with one-pixel Hairline boundaries.
- Do pair every risk, attention, verified, and heatmap state with a label, value, icon, outline, or pattern that works without color.
- Do preserve stable 36px controls, 52px queue rows, and compact 6px control corners across hover, loading, translation, and role changes.
- Do keep transitions between 150ms and 250ms, with reduced-motion behavior that removes nonessential movement.
- Do give English and Chinese equal layout priority, using the same hierarchy and allowing text to reflow instead of overlap.
- Do confine dark surfaces to the WSI canvas and its immediate tools, where contrast supports tissue inspection.

### Don't:

- Don't build a neon science-fiction AI cockpit or an interface that treats AI as spectacle.
- Don't create an all-dark application; the focused WSI viewing surface may be dark, but the broader workflow should remain clear in normal clinical lighting.
- Don't produce a generic blue SaaS dashboard made from repetitive, interchangeable cards.
- Don't style it as a consumer wellness or patient-facing medical application.
- Don't use decorative gradients, default glassmorphism, oversized metrics, or ornamental animation.
- Don't reproduce dense legacy PACS-style screens with weak hierarchy and no clear primary task.
- Don't place marketing pages, hero sections, and feature explanations in place of the actual product experience.
- Don't use Eosin Rose as danger, success, approval, or urgency.
- Don't rely on color alone for risk, heatmap, review, or validation meaning.
- Don't put cards inside cards or turn full-width page sections into floating containers.
