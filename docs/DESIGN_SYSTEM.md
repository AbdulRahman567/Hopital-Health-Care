# Healthcare-HMS — Design System

Goal: **fast, clear, safe** clinical UI. Prioritize clarity and error prevention over decoration.

## 1. Principles
1. **Clarity over cleverness** — clinicians scan, they don't explore.
2. **Names, never IDs** — every reference shows a human-readable label.
3. **Prevent errors** — required markers, inline validation, confirmations, disabled-while-pending.
4. **Consistency** — one component per job; no bespoke variants.
5. **Accessibility** — keyboard first, sufficient contrast, labelled controls.
6. **Calm motion** — animation only when it clarifies state change.

## 2. Foundations
| Token | Guidance |
|---|---|
| Framework | Tailwind CSS + shadcn/ui, tokens as CSS variables (light + dark) |
| Font | System UI sans (e.g., Inter); monospace for MRN/codes |
| Type scale | 12 / 14 / 16 / 18 / 24 / 30 px; body 14–16 px |
| Spacing | 4 px base grid |
| Radius | 6–8 px |
| Icons | Lucide React, consistent 16/20 px |
| Breakpoints | sm 640, md 768, lg 1024, xl 1280 |

### Semantic colors
| Role | Use |
|---|---|
| Primary | Main actions, navigation highlight |
| Success | Completed, paid, verified |
| Warning | Pending, expiring, needs attention |
| Danger | Errors, destructive actions, **severe allergies** |
| Info | Neutral notices |
| Clinical | Distinct accent for medical data panels (vitals, diagnoses, prescriptions) so it is visually separable from administrative data |

Never rely on color alone; pair with icon or text. Maintain WCAG AA contrast (4.5:1 body text).

## 3. Layout
- App shell: sidebar (module navigation filtered by permissions), top bar (tenant name, user menu, notifications), content area.
- Patient context header (name, MRN, age/sex, **allergy banner**) pinned on all patient-related screens.
- Responsive: tables collapse to cards or scroll horizontally in an `overflow-x-auto` container on small screens.

## 4. Core Components
| Component | Rules |
|---|---|
| **FormField** | Label, control, hint, error. **Required fields show `*` and `aria-required`.** Optional fields may show "(optional)" |
| **SelectField / Combobox** | Options are `{value, label}`; displays label, stores value; searchable for long lists; shows placeholder, never an ID; handles controlled state without controlled/uncontrolled warnings |
| **DataTable** | Server pagination, sort, filter; loading skeleton; empty and error states; row actions gated by permission |
| **ConfirmDialog** | Required for destructive actions; names the object being affected; primary button labelled with the action, not "OK" |
| **Button** | Variants: primary, secondary, ghost, danger; shows spinner and disables while pending |
| **Toast** | Success/error feedback; errors include a traceId reference for support |
| **StatusBadge** | Consistent mapping for appointment, visit, invoice and tenant statuses |
| **AllergyBanner** | Always visible when allergies exist; danger styling for severe |
| **Timeline** | Paginated; each entry shows type icon, date, responsible clinician, department |
| **EmptyState / ErrorState / Skeleton** | Required on every data view |
| **PermissionGate** | Hides UI the user cannot use (UX only; server enforces) |

## 5. Forms & Validation
1. Validate on blur and submit with Zod; mirror backend rules.
2. Server field errors (`error.fields[]`) map to the exact input and focus the first invalid field.
3. Generic "unexpected error" appears only for true server faults and includes a traceId.
4. Prevent double submission: disable button, ignore repeated clicks.
5. Warn on navigation away from a dirty form.
6. Date/time fields respect the tenant timezone.

## 6. Tables & Lists
- Default page size 20; show total count; keep filters in the URL.
- Search is debounced (~300 ms).
- Long histories paginate or virtualize.
- Columns show names (patient, doctor, department) — never IDs.

## 7. Medical Data Presentation
- Clinical panels use the Clinical accent and a consistent order: Allergies → Vitals → Diagnoses → Prescriptions → Notes.
- Prescription entries show medicine, dose, frequency, **duration**, and **prescribing doctor**.
- Finalized records display a "Finalized" badge; amendments are shown as linked addenda with author and reason.
- Sensitive fields the user may not view are omitted, not displayed as blank or "N/A".

## 8. States
Every view defines: **loading**, **empty**, **error**, **forbidden (403)**, **not found (404)**, **offline/timeout**.

## 9. Accessibility Checklist
- [ ] All controls reachable and operable by keyboard; visible focus ring
- [ ] Labels associated with inputs; errors announced (`aria-live`)
- [ ] Dialogs trap focus and restore it on close
- [ ] Color contrast meets AA; no color-only meaning
- [ ] Tables have headers and captions where useful
- [ ] Motion respects `prefers-reduced-motion`

## 10. Content & Tone
- Plain, precise language; clinical terms only where needed.
- Error messages say what happened and what to do next.
- No regulatory-compliance claims in UI copy.
