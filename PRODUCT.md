# PARVISIGHT（观隅） Product

## Register

product

## Users

PARVISIGHT serves four primary roles in a hospital pathology workflow:

- Pathologists triage assigned cases, inspect whole-slide images and AI evidence, record notes, adjust annotations, and make the final clinical decision.
- Senior pathologists review cases escalated by a pathologist, flagged for low AI confidence or image-text disagreement, or selected for random quality control.
- AI and data administrators manage disease profiles, inspect the validated feedback pool, and monitor the provenance and stated limitations of demo model outputs.
- System administrators manage users, roles, audit access, and system configuration.

The primary working environment is a desktop pathology workstation. The initial disease profile is breast cancer lymph-node metastasis in H&E slides, while the product model must remain extensible to additional diseases, specimens, stains, label taxonomies, risk schemes, and heatmap layers.

## Product Purpose

PARVISIGHT is a B2B, AI-assisted pathology workflow platform. It turns incoming WSI cases and pathology reports into a prioritized work queue, presents explainable image and text evidence in the slide viewer, keeps the pathologist in control of every decision, and routes reviewed annotations into a governed feedback pool.

The first product-grade prototype must demonstrate one coherent workflow:

Upload -> AI triage -> Explainable review -> Doctor decision -> Conditional senior review -> Annotation feedback

The seven-day prototype uses clearly labeled, deterministic demo AI outputs and non-sensitive sample data. It is a clinical decision-support demonstration, not an autonomous diagnostic system and not a clinically validated device. Success means the core workflow is complete, understandable, auditable, and convincing without implying unsupported medical capability.

## Brand Personality

Precise, restrained, trustworthy.

The interface should feel calm and professionally focused during repeated expert use. It should communicate confidence through clear evidence, predictable interactions, explicit uncertainty, and consistent status handling rather than visual spectacle. Motion should make state changes feel fluid without delaying work or drawing attention away from the slide.

## Anti-references

- A neon science-fiction AI cockpit or an interface that treats AI as spectacle.
- An all-dark application; the focused WSI viewing surface may be dark, but the broader workflow should remain clear in normal clinical lighting.
- A generic blue SaaS dashboard made from repetitive, interchangeable cards.
- A consumer wellness or patient-facing medical application.
- Decorative gradients, default glassmorphism, oversized metrics, or ornamental animation.
- Dense legacy PACS-style screens with weak hierarchy and no clear primary task.
- Marketing pages, hero sections, and feature explanations in place of the actual product experience.

## Design Principles

1. Keep clinical judgment visibly in control. AI can prioritize, suggest, and explain; a qualified user accepts, modifies, rejects, or escalates.
2. Show evidence and uncertainty together. Risk scores, heatmaps, report excerpts, confidence, limitations, and provenance must be inspectable rather than hidden behind a summary.
3. Optimize for repeated expert work. Favor information density, stable layouts, keyboard support, and predictable controls over novelty or decoration.
4. Make every transition accountable. Case status, review ownership, annotations, decisions, and feedback eligibility must be traceable through clear state and audit history.
5. Design one coherent disease workflow without hard-coding the product to one disease. Disease-specific fields and labels belong in an extensible profile model.

## Accessibility & Inclusion

- Target WCAG 2.2 AA for product surfaces, including body text and placeholder contrast.
- English is the default language, with complete Chinese parity across the public demo and local system.
- Design primarily for viewports at or above 1440 x 900 and remain structurally usable down to 1024 px.
- Mobile supports read-only case-queue access; WSI review and annotation are not mobile workflows.
- Heatmaps and risk states must not rely on color alone. Pair color with labels, values, icons, outlines, or patterns that remain understandable for common color-vision deficiencies.
- Core workflows require visible focus states and keyboard access. Existing viewer shortcuts should remain discoverable and consistent.
- Use purposeful 150-250 ms state transitions and provide reduced-motion alternatives. Avoid orchestrated page entrances or motion that delays clinical work.
