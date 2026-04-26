---
name: Landscape KM rework
overview: Simplify landscape Keyboard & Mouse to a two-state toggle (keyboard-only vs split), drop touchpad-left and touchpad-only layouts, normalize legacy modes to split; fix split Fn by syncing `isFnLocked` to the partner keyboard; improve split top shortcut panel UX; explore compact landscape MainActivity header (KeyMod title dead space, 56dp bar) to reclaim vertical space for split mode.
todos:
  - id: fn-split-sync
    content: "CustomKeyboardView: sync isFnLocked (and post updateKeyboard) to splitPartner when Fn toggles; consider folding into syncModifierStates rename/sync scope"
    status: completed
  - id: landscape-cycle
    content: "CompositeFragment: landscape cycleDisplayMode KEYBOARD↔SPLIT; normalize BOTH/TOUCHPAD→SPLIT on landscape apply/config"
    status: completed
  - id: split-top-panel
    content: Audit split_top_panel + createTopPanel in split landscape; fix layout/clipping/UX issues found
    status: completed
  - id: landscape-header
    content: "MainActivity header: landscape variant or compact layout (reduce KeyMod dead space, optional lower header_height)"
    status: completed
isProject: false
---

# Landscape Keyboard & Mouse: layout simplification + split fixes

## Requirements (from you)

| Old step | Your feedback | Action |
|----------|----------------|--------|
| Layout 0 — `BOTH` in landscape (touchpad \| toggle \| keyboard) | Touchpad on left is awkward for typing | **Remove** from landscape (do not offer this layout) |
| Layout 1 — `KEYBOARD` only | Good | **Keep** |
| Layout 2 — `TOUCHPAD` only | Low value | **Remove** from landscape |
| Layout 3 — `SPLIT` | Good for typing + touchpad; top key area is messy; Fn + yuiop broken | **Keep**; **fix** top panel UX; **fix** Fn on right half |

Portrait behavior: you did not ask to change it; **keep** the existing portrait cycle (`BOTH` ↔ `KEYBOARD`, with `TOUCHPAD` / `SPLIT` handled as today via [`onConfigurationChanged`](app/src/main/java/com/openterface/fragment/CompositeFragment.java)).

---

## 1) Landscape display modes and toggle cycle

**Target behavior**

- Landscape has **only two** user-facing layouts: **keyboard-only** (`KEYBOARD`) and **split** (`SPLIT`).
- Each tap on the toggle handle alternates: `KEYBOARD` ↔ `SPLIT`.

**Normalize invalid / legacy states when in landscape**

- If `displayMode` is `BOTH` or `TOUCHPAD` while landscape (including app start with default `BOTH`, or rotation from portrait), coerce to **`SPLIT`** so users never land on removed layouts. (Alternative default `KEYBOARD` is possible if you prefer; **default `SPLIT`** matches “split is good for typing + touchpad.”)

**Code touchpoints**

- [`CompositeFragment.cycleDisplayMode()`](app/src/main/java/com/openterface/fragment/CompositeFragment.java) — branch on `isPortrait`: landscape path only switches `KEYBOARD` ↔ `SPLIT`; portrait path unchanged.
- Call a small helper (e.g. `normalizeDisplayModeForOrientation()`) from `applyDisplayMode()` and/or `onConfigurationChanged()` so normalization runs whenever orientation or mode could leave landscape in `BOTH` / `TOUCHPAD`.

**No layout XML required** for removing landscape `BOTH`/`TOUCHPAD` beyond behavior: normal [`fragment_composite`](app/src/main/res/layout/fragment_composite.xml) is still used for keyboard-only; split still uses [`layout-land/fragment_composite_split.xml`](app/src/main/res/layout-land/fragment_composite_split.xml) when `SPLIT`.

---

## 2) Fn + yuiop (F6–F10) in split mode — root cause and fix

**Root cause (verified in code)**

- [`resolveFnMapping(Key key)`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) maps `y`→F6 … `p`→F10 via HID scan codes (`0x1C` … `0x13`).
- In split mode the **right** half renders `yuiop` on [`keyboardViewRight`](app/src/main/java/com/openterface/fragment/CompositeFragment.java), a **separate** `CustomKeyboardView` instance.
- [`syncModifierStates()`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) copies Shift/Ctrl/Alt/Win to `splitPartner` but **does not** copy **`isFnLocked`**.
- Fn is toggled only on the instance that handled the Fn key (typically the **left** half from the shared top panel / left key rows), so the **right** view keeps `isFnLocked == false` → `resolveFnMapping` returns null for `yuiop` → letters send instead of F-keys.

**Fix**

- When `KEY_MODE_FN` toggles `isFnLocked`, mirror the value to `splitPartner` and `post(() -> splitPartner.updateKeyboard())`, same pattern as modifiers (either extend [`syncModifierStates()`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) to include `isFnLocked`, or add `syncFnLockedToPartner()` and call it from the `KEY_MODE_FN` branch).
- Optionally mirror **`extraNumpadFnLocked`** if the extra numpad can appear on both halves in split (lower priority unless you see the same class of bug).

---

## 3) Split mode “top key area” mess — scope for implementation

This is broader UX; the plan is to **audit then fix** in code, not prescribe a single pixel layout without seeing the device.

**Likely areas**

- [`CompositeFragment.setupSplitViews`](app/src/main/java/com/openterface/fragment/CompositeFragment.java): only **left** keyboard calls [`createTopPanel()`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) into [`split_top_panel`](app/src/main/res/layout-land/fragment_composite_split.xml) — possible clipping, height (`rowHeightPx * 2`), or interaction oddities vs full keyboard top rows.
- Compare split top panel with non-split top shortcut behavior (viewport paging, [`createTopPanelTouchListener`](app/src/main/java/com/openterface/keymod/CustomKeyboardView.java), Fn / modifier visuals).

**Deliverable**

- After Fn sync lands, run split mode on a landscape device/emulator, list concrete issues (overflow, duplicate semantics, wrong height), then adjust `createTopPanel` / split layout / margins as needed.

---

## 4) MainActivity top bar in landscape (space + design directions)

**Why it feels heavy**

- The global header is [`header_layout`](app/src/main/res/layout/activity_main.xml) (`RelativeLayout`, [`@dimen/header_height`](app/src/main/res/values/dimens.xml) = **56dp**).
- The title [`app_title`](app/src/main/res/layout/activity_main.xml) uses **`layout_width="match_parent"`** with `layout_toEndOf` menu and `layout_toStartOf` `header_end_cluster`, so the **TextView spans the full gap** between left menu and right icons even though the string “KeyMod” is short — the unused horizontal band reads as “a lot of blank space” (it is literally empty title cell, not separate padding).
- Right cluster: Target OS + connection (Bluetooth / signal) is appropriate density; the main waste is **title row width semantics** + **fixed bar height** for a mode where vertical pixels compete with split [`split_top_panel`](app/src/main/res/layout-land/fragment_composite_split.xml) + keyboards.

**Link to split “top key area”**

- Shorter or denser **activity** header in landscape **does not** fix split Fn logic by itself, but it **increases usable vertical space** for the fragment and can make the split shortcut strip feel less “stacked” and cramped — same overall screen budget.

**Design / layout options (pick after quick mock or device pass)**

1. **Landscape-only XML** — Add [`res/layout-land/activity_main.xml`](app/src/main/res/layout/activity_main.xml) (or a `layout-land` override for just `header_layout` if you split include): reduce `header_height` (e.g. 40–48dp), tighten `header_padding`, keep the same controls.
2. **Title `wrap_content` + no greedy center strip** — Change title to `wrap_content` width (landscape or both): blank between word and OS/Bluetooth **collapses** visually; icons stay `alignParentEnd`. Watch overlap on narrow landscape (min width / ellipsize).
3. **Drop inline title in landscape** — Menu + OS + connection only; app name only in drawer / recents (common for tool-style apps). Maximum horizontal simplicity.
4. **Chip / logo instead of wordmark** — Small branded icon where title was; frees mental space without a long empty `TextView`.
5. **Merge OS + connection** — Single combined status control (one tap = connection sheet, long-press = OS) to shrink `header_end_cluster` width (optional; smaller win than title strip).
6. **Immersive / lean mode for KM landscape** — Optional later: transient or thinner chrome (e.g. hide title row when `CompositeFragment` visible in landscape); higher engineering cost (lifecycle, back, accessibility).

**Open point**

- No single “best” without trying **(1)+(2)** on a real device first — low risk and reversible.

---

## 5) Suggested implementation order

1. **Fn split sync** — small, isolated change in `CustomKeyboardView`; high confidence, fixes F6–F10 on right half.
2. **Landscape cycle + normalization** — `CompositeFragment` only; verify rotation portrait ↔ landscape and toggle sequence.
3. **Split top panel polish** — iterative UI pass driven by screenshots / QA.
4. **Landscape header compaction** — prototype `layout-land` header and/or title `wrap_content` ([`activity_main.xml`](app/src/main/res/layout/activity_main.xml)); measure impact on split vertical space; refine with your preferred balance (icons-only vs small title).
