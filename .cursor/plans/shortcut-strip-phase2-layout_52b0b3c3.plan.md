---
name: shortcut-strip-phase2-layout
overview: "Consolidated build plan after Phase 1 (modifier tap/Fn alignment, docs, Linux Super text): redesign Page 1 local Fn overlays for navigation and symbols, evolve Page 2 row 3 placeholder/DISPLAY behavior, defer media keys until Consumer HID is proven, and prioritize keyboard-page keys (Caps, PrtSc, Menu) with clear HID/firmware checks."
todos:
  - id: page1-fn-map
    content: Extend resolveFixedTopLocalFnMapping for Page 1 row2/row3 to navigation + symbol grid; compact labels/icons
    status: pending
  - id: modifier-lock-fn-on
    content: Decide and implement Option 1 (long-press lock uses base modifier code) or Option 2 (disable lock when Fn on) for E0/E2/E3 cells
    status: pending
  - id: page2-display-fn
    content: "When Fn on Page 2 strip: six placeholder slots + Fn; DISPLAY hidden or inert per spec; handle handleKeyPress/display toggle edge cases"
    status: pending
  - id: menu-hid-verify
    content: Firmware QA Application key 0x65; if OK add CH9329MSKBMap + optional strip slot
    status: pending
  - id: docs-phase2
    content: "USER_GUIDE: Page 1 Fn grid, Page 2 row, media deferred, Menu/Caps/PrtSc notes"
    status: pending
isProject: false
---

# Shortcut strip Phase 2: Page 1 Fn redesign + Page 2 row 3 + key priorities

## Context (already shipped in Phase 1)

The following are **done** in the repo and supersede the “TAB/ENTER label mismatch” section of the older analysis plan:

- [`CustomKeyboardView.sendMomentaryModifierClick`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) respects `resolveFixedTopLocalFnMapping` so **Fn-on modifier caps match HID**.
- [`docs/USER_GUIDE.md`](Openterface_KeyMod_Android/docs/USER_GUIDE.md) explains target OS vs local Fn on the fixed strip.
- `shortcutModifierText` uses **Super** on Linux for `MOD_WIN`.

Treat [`shortcut_strip_modifier_analysis_00078086.plan.md`](/Users/billywang/.cursor/plans/shortcut_strip_modifier_analysis_00078086.plan.md) as **historical** except for the layout reference for Page 0/1/2 structure.

---

## Target behavior to build (Phase 2)

### A. Page 1 — local Fn “overlay” rows

**Base (Fn off)** — unchanged from today [`buildFixedTopRowsPage1()`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java):

- Row 2: ESC, Shift, DEL, TAB, Up, Enter, IME toggle  
- Row 3: Ctrl, Alt, Win/Cmd/Super (labels OS-aware), Left, Down, Right, local Fn toggle  

**Fn on — proposed layout** (replace current Ctrl→Tab / Win→Enter overlays on row 3 cols 1–3):

- Row 2 → **navigation cluster:** Home `0x4A`, End `0x4D`, PgUp `0x4B`, PgDn `0x4E`, Insert `0x49`, Scroll Lock `0x47`, IME toggle (unchanged slot; no duplicate of keys elsewhere if possible).  
- Row 3 → **symbols / system:** Pause/Break `0x48` (validate behavior vs Pause+Ctrl on hosts), `/` `0x38`, `\` `0x31`, `|` via existing map pattern (e.g. `0x64` as in [`CH9329MSKBMap`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/target/CH9329MSKBMap.java)), `"` with shift+apostrophe if needed, `-` `0x2D`, local Fn toggle (fixed).

**Implementation approach:** extend [`resolveFixedTopLocalFnMapping(Key)`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) with explicit cases for **each base `key.code`** on Page 1 row 2 and row 3 (match the building order in `buildFixedTopRowsPage1`). Use compact labels (`PGUP`, `SCR LK`, `PAUSE`) where UI space is tight. Reuse [`sendMomentaryModifierClick`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) / normal `handleKeyPress` paths so Fn-mapped sends stay consistent with locked modifiers.

**Long-press modifier lock:** row 3 base keys `0xE0`/`0xE2`/`0xE3` keep lock semantics; when Fn is on, either (pick one and document):

- **Option 1 (recommended):** Fn-on shows non-modifier legends on those cells but **short tap** sends the Fn action; **long-press** still toggles lock using underlying modifier `key.code` (requires touch listener branch: if Fn on, long-press uses `key.code` for lock only).  
- **Option 2:** Fn-on disables modifier lock on those three cells (simpler logic, weaker power-user story).

### B. Page 2 — Shortcut Hub profile row + bottom row

**Current:** [`buildFixedTopRowsPage2()`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java): row 1 = seven profile slots; row 2 = **five** no-ops + DISPLAY + Fn.

**Target:**

| State | Row under profiles (7 cols) |
|--------|------------------------------|
| Fn **off** | `[ ] [ ] [ ] [ ] [ ]` + **DISPLAY** + **Fn** |
| Fn **on** | `[ ] [ ] [ ] [ ] [ ] [ ]` + **Fn** (six assignable; DISPLAY slot becomes sixth blank or alternate action) |

**Implementation sketch:**

- Add Fn-specific **visual + hit** handling for the DISPLAY key when `fixedTopLocalFnLocked` on Page 2: either rebind col 6 to `KEY_NOOP_PLACEHOLDER` or a dedicated `KEY_TOP_SHORTCUT_DISPLAY_TOGGLE` branch in `resolveFixedTopLocalFnMapping` that returns **no toggle** (dead/blank) so the user sees six equal slots.  
- Ensure `KEY_TOP_SHORTCUT_DISPLAY_TOGGLE` handler in [`handleKeyPress`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) does not flip action-label mode when Fn is on for Page 2 if col 6 is repurposed (mirror existing guard for DISPLAY under Fn on F-row strip).

**Future:** the five/six placeholder slots are candidates for **user-assigned shortcuts** or static keys; today they are no-ops—Phase 2 can keep no-ops or add a minimal static set if product asks.

### C. Media keys — defer as Phase 3

- Current transport [`sendKeyData`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) only emits **keyboard** CH9329 frames (`57AB000208…`). **Consumer/media** HID is **not** implemented in this path.  
- **Do not** promise “all target OS” media until: firmware supports consumer reports + app encodes them + QA on USB and BLE.

### D. Application / Menu vs laptop “Fn”

- **Application (context menu):** standard keyboard usage **0x65** in HID; **not** Windows-only—verify CH9329 accepts it, add to [`CH9329MSKBMap`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/target/CH9329MSKBMap.java), wire one slot if firmware OK. **Medium–high value.**  
- **Laptop Fn:** not a reliable host-visible scancode; **do not** expose as “HID Fn” to the target. App’s **local Fn toggle** is the right abstraction.

### E. Caps Lock, Scroll Lock, and “what’s missing”

Already mappable via same map: **Caps `0x39`**, **Scroll `0x47`**, **PrtSc `0x46`**, **Num Lock `0x53`**.  

**Slot priority** (when forced to choose): Caps Lock and PrtSc &gt; Scroll Lock &gt; Pause/Break edge cases &gt; media (deferred).

---

## Files to touch (implementation pass)

- [`CustomKeyboardView.java`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) — `resolveFixedTopLocalFnMapping`, optional touch/long-press split for Fn-on modifiers, Page 2 DISPLAY behavior under Fn.  
- [`CH9329MSKBMap.java`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/target/CH9329MSKBMap.java) — `MENU` / `APPLICATION` → `65` if firmware confirmed.  
- [`docs/USER_GUIDE.md`](Openterface_KeyMod_Android/docs/USER_GUIDE.md) — document new Page 1 Fn grid and Page 2 six-slot Fn row; note media deferral.

---

## Validation checklist

- Page 1: Fn off matches current; Fn on sends correct HID for every overlay (spot-check with host or logging).  
- Modifier long-press lock: behavior matches chosen Option 1 or 2.  
- Page 2: Fn off shows DISPLAY; Fn on shows six blanks + Fn and DISPLAY does not trap users.  
- Regressions: F-row page (`buildFixedTopRowsPage0`) Fn mappings remain unchanged.
