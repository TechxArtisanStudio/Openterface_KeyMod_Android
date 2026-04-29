---
name: Compose touchpad button
overview: Add a Touchpad button between Clear and Send in Compose that opens the same pop-out experience as Presentation after the recent touchpad rework—shared layout (dialog_touchpad.xml), bottom wash, tips, help overlay, scroll vs move, drag mode, and HID mouse path—not a minimal duplicate listener.
todos:
  - id: extract-touchpad-dialog
    content: Extract Presentation touchpad dialog session into reusable class (e.g. PopOutTouchPadDialog in util/) with presentationPointerKeys flag; move wash/tips/help/listener/dialog lifecycle
  - id: refactor-presentation
    content: PresentationFragment delegates to extracted class; remove duplicated fields/methods now owned by the class
  - id: compose-ui-wire
    content: fragment_compose weights 1:1:3; strings; ComposeFragment opens dialog with pointer keys false; dismiss on destroyView; optional disable when !connected/sending
isProject: false
---

# Compose: Touchpad button (aligned with current Presentation pop-out)

## What changed in Presentation (source of truth)

The pop-out is no longer “inflate + basic `OnTouchPadListener` only”. It now mirrors **Keyboard and Mouse** touchpad UX:

- **Layout** [`dialog_touchpad.xml`](app/src/main/res/layout/dialog_touchpad.xml): `FrameLayout` (280dp) with `TouchPadView` (`touch_surface`), bottom **tips** (`presentation_touchpad_tips`), center **help overlay** (`presentation_touchpad_help_overlay`), top-end **info** (`presentation_touchpad_info`). Bottom **wash** overlay is added **in code** on top of the pad (`presentationTouchpadSetupWash`).

- **Behavior** in [`PresentationFragment`](app/src/main/java/com/openterface/fragment/PresentationFragment.java) (from ~`presentationTouchpadInitWashStyle` through `showTouchpadDialog` ~790–932, plus teardown/send helpers):
  - Wash: accent-tinted gradient overlay, intensity/animations for click pulse and drag (`TOUCHPAD_WASH_*` constants, `AnimatorSet` / `ValueAnimator`).
  - Tips: `TouchPadTipsFormatter.buildCompact` + `TouchPadPointerPhase` + `Handler` idle callback (`POINTER_IDLE_AFTER_MS`).
  - Help: `TouchPadHelpOverlay` (info button, auto-show once `touchpadHelpAutoShownOnce`, `wireDismissTouchTargets`).
  - **Pointer / scroll:** `onTouchMove` — if `lastX == 0 && lastY == 0`, treat as **scroll** (`cm.sendScroll`) and phase `SCROLL`; else **move** with optional **left button held** when `touchpadDragActive` (`sendMouseMovement(dx,dy, buttons)`).
  - **Click / double / right:** haptics + `sendMouseClick`; if drag active, click/double ends drag and releases buttons + wash animation.
  - **Long press:** starts drag (`sendMouseMovement(0,0,1)`), wash base intensity, tips update.
  - **Release:** phase clear; release mouse buttons if not dragging.
  - **Dialog:** same window flags as before; dismiss cleans drag, calls `presentationTouchpadTeardownUi`, then **presentation-only** `sendKeyHID(6)` (`'C'` pointer toggle).
  - **On show:** `sendKeyHID(6)` for pointer (presentation only).

Compose must reuse this **full** behavior for the pad surface; it must **not** call `sendKeyHID(6)` on show/dismiss (would type `c` on the target).

## Recommended implementation

### 1. Extract a reusable “pop-out touchpad dialog” session

Add a class under e.g. [`app/src/main/java/com/openterface/keymod/util/`](app/src/main/java/com/openterface/keymod/util/) (name e.g. **`PopOutTouchPadDialog`** or **`TouchPadDialogController`**) that owns:

- Inflation of [`R.layout.dialog_touchpad`](app/src/main/res/layout/dialog_touchpad.xml), `Dialog` creation, window layout/transparent background, root dismiss click, `OnDismissListener`.
- All state currently on `PresentationFragment` that exists **only** for this dialog: e.g. `touchpadDragActive`, wash overlay view + animator + color/intensity, `touchpadTipHandler`, `touchpadPointerPhase`, `touchpadHelpAutoShownOnce` (or a per-session flag passed in), and the **`presentationTouchpad*`** / `showTouchpadDialog` / `releaseTouchpadMouseButtons` logic that does not belong to the rest of the presentation UI.

**Constructor / `show` parameters (minimal):**

- `Fragment host` — for `requireContext`, `getActivity()` as `MainActivity`, `isAdded` checks in callbacks.
- **`boolean sendPresentationPointerKeys`** — when `true`, keep current `sendKeyHID(6)` on show and dismiss; when **`false` (Compose)**, omit entirely.

**Internals:**

- Resolve `ConnectionManager` like today (`getConnectionManager()` pattern).
- Use a **single `Handler(Looper.getMainLooper())`** inside the class for delayed `sendKeyRelease` / double-click second click / tip idle (replace `timerHandler` usage from `PresentationFragment` for code that moves here).
- Expose **`dismissIfShowing()`** for `ComposeFragment.onDestroyView()` (and optionally Presentation if needed).

**Optional follow-up (not blocking):** Rename XML ids from `presentation_touchpad_*` to neutral `dialog_touchpad_*` once logic lives in one place; until then Compose reuses the same ids (fine).

### 2. Refactor Presentation

[`PresentationFragment`](app/src/main/java/com/openterface/fragment/PresentationFragment.java): `btnPointer` still opens the dialog, but implementation becomes a field or call to the extracted class with **`sendPresentationPointerKeys = true`**. Remove duplicated methods/fields that moved into the class.

### 3. Compose UI and wiring

- [`fragment_compose.xml`](app/src/main/res/layout/fragment_compose.xml): insert **Touchpad** `Button` between Clear and Send with **`layout_weight` 1 : 1 : 3** (same horizontal row, spacing with `layout_marginEnd` on Clear and Touchpad).
- [`strings.xml`](app/src/main/res/values/strings.xml): short label e.g. `compose_touchpad` (“Touchpad”).
- [`ComposeFragment`](app/src/main/java/com/openterface/fragment/ComposeFragment.java): open dialog via extracted class with **`sendPresentationPointerKeys = false`**; **`dismissIfShowing()`** in `onDestroyView`; optionally disable the button when `!connected` or `sending` (match Send/Clear behavior).

## Outdated approach (do not do)

- A tiny helper that only wires the **old** move/click listener without wash, tips, help, `sendScroll`, or long-press drag would **diverge** from Presentation and from Keyboard and Mouse parity.

## Files likely touched

| Area | Files |
|------|--------|
| New session class | New file under `util/` (e.g. `PopOutTouchPadDialog.java`) |
| Presentation | [`PresentationFragment.java`](app/src/main/java/com/openterface/fragment/PresentationFragment.java) |
| Compose | [`fragment_compose.xml`](app/src/main/res/layout/fragment_compose.xml), [`ComposeFragment.java`](app/src/main/java/com/openterface/fragment/ComposeFragment.java), [`strings.xml`](app/src/main/res/values/strings.xml) |
| Layout | [`dialog_touchpad.xml`](app/src/main/res/layout/dialog_touchpad.xml) — only if renaming ids or theme tweaks; otherwise unchanged |

## Risks

- **Lifecycle:** All `postDelayed` / animators must be canceled on dismiss; host `Fragment` must be checked with `isAdded()` / null `getActivity()` in async callbacks.
- **Help auto-show once:** Decide whether Compose shares the same “once ever” behavior as Presentation (same static in class) or separate flag—document choice in implementation (simplest: one boolean on the controller instance per show session; “once ever” can remain a static if product wants global first-time only).
