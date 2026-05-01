---
name: Shortcut strip Fn layouts
overview: "Authoritative layouts: Page 1 local-Fn overlays (rows 2–3) reorganized into locks/nav/editing; Page 2 hub bottom row punctuation (Fn off) and bracket/shift-symbol row (Fn on). Includes HID notes, locale caveats, and resolver/modifier-lock risks."
todos:
  - id: page1-fn-mapping
    content: Replace Page 1 resolveFixedTopLocalFnMapping row2/row3 cases per table (ESC→ScrLk … Right→PgDn); IME unchanged
    status: pending
  - id: page1-fn-modifier-lock
    content: Decide long-press on E0/E2/E3 when Fn on shows PRT SC/Space/Pause — Option A lock underlying modifier vs Option B disable lock while Fn on
    status: pending
  - id: page2-hub-build-static
    content: buildFixedTopRowsPage2 Fn off six keys + DISPLAY + Fn; Fn on six keys + Fn (DISPLAY slot becomes sixth symbol cell)
    status: pending
  - id: page2-fn-sentinel-or-map
    content: Implement Page 2 keys via FnMapping + sentinel codes if needed so symbols do not collide with Page 0/1 switch(key.code); wire shift-modifier sends for "?#():@"
    status: pending
  - id: user-guide-fn-layouts
    content: USER_GUIDE — Page 1 Fn cluster + Page 2 quote/hub symbols; note US-QWERTY shift assumptions where relevant
    status: pending
isProject: false
---

# Shortcut strip: Page 1 Fn + Page 2 hub row (your arrangement)

## Page 1 — local Fn **on** (physical cells unchanged; only overlay labels / sends change)

Strip is still built in [`buildFixedTopRowsPage1()`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java). Order below matches **column index** 1–7.

### Row 2 (base → Fn on)

| Col | Base (Fn off) | `key.code` | Fn on label | Fn on send |
|-----|----------------|------------|-------------|------------|
| 1 | ESC | `0x29` | SCR LK | `0x47` |
| 2 | SHIFT | `0xE1` | CAPS | `0x39` |
| 3 | DEL | `0x4C` | DEL | `0x4C` (same as base; forward delete) |
| 4 | TAB | `0x2B` | BKSP | `0x2A` |
| 5 | UP | `0x52` | HOME | `0x4A` |
| 6 | ENTER | `0x28` | PGUP | `0x4B` |
| 7 | PH1 / keyboard | `KEY_IME_TOGGLE` | (unchanged) | `null` in resolver — still IME toggle |

### Row 3 (base → Fn on)

| Col | Base (Fn off) | `key.code` | Fn on label | Fn on send |
|-----|----------------|------------|-------------|------------|
| 1 | CTRL | `0xE0` | PRT SC | `0x46` |
| 2 | ALT | `0xE2` | SPACE | `0x2C` |
| 3 | WIN/CMD | `0xE3` | PAUSE / BREAK | `0x48` |
| 4 | LEFT | `0x50` | INS | `0x49` |
| 5 | DOWN | `0x51` | END | `0x4D` |
| 6 | RIGHT | `0x4F` | PGDN | `0x4E` |
| 7 | — | `KEY_FIXED_TOP_LOCAL_FN` | Fn | toggle (unchanged) |

### Analysis — Page 1

- **Coherent story:** Row 2 = scroll/caps + delete/backspace + home/pgup + IME; row 3 = print/space/pause + insert/end/pgdn. This **replaces** the previous Page 1 Fn grid (Home on ESC, `/` on Alt, etc.) entirely; update [`resolveFixedTopLocalFnMapping`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) cases for `0x29`, `0xE1`, `0x4C`, `0x2B`, `0x52`, `0x28`, and `0xE0`–`0xE3`, `0x50`, `0x51`, `0x4F` accordingly.
- **DEL (col 3):** Fn on and Fn off both forward-delete — resolver can return **`null`** for `0x4C` so the base key path sends `0x4C`, or return an identity mapping; avoid the old `0x4C` → PGUP behavior.
- **Modifier long-press:** Positions `0xE0` / `0xE2` / `0xE3` are still **modifier** keys in the `Key` model when Fn is off. When Fn is on they **look** like PRT SC / SPACE / PAUSE. You must choose: **(A)** long-press still toggles Ctrl/Alt/Win lock using underlying codes (Phase 2 Option 1), or **(B)** disable modifier lock on those three while Fn is on (Option 2). Recommend documenting the choice in USER_GUIDE.
- **CAPS as tap on Shift position:** Single tap sends Caps Lock HID (`0x39`); it does **not** mirror Android “shift” behavior — acceptable if labeled CAPS.

---

## Page 2 — hub bottom row (below profile slots)

Row is built in [`buildFixedTopRowsPage2()`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java): **Fn off** shows **six** character keys + DISPLAY + Fn; **Fn on** shows **six** keys + Fn (DISPLAY slot is repurposed as the sixth key, same as prior hub spec).

### Fn **off**

| Col | Label | Typical HID / modifiers | Notes |
|-----|-------|-------------------------|--------|
| 1 | `"` | `0x34` + `MOD_SHIFT` | Double quote (US: Shift+apostrophe) |
| 2 | `'` | `0x34` | Apostrophe |
| 3 | `.` | `0x37` | Period |
| 4 | `?` | `0x38` + `MOD_SHIFT` | Question (US: Shift+slash) |
| 5 | `#` | `0x20` + `MOD_SHIFT` | Hash (US: Shift+3) |
| 6 | DISPLAY | `KEY_TOP_SHORTCUT_DISPLAY_TOGGLE` | Icon/text mode for strip (existing behavior) |
| 7 | Fn | `KEY_FIXED_TOP_LOCAL_FN` | — |

### Fn **on**

Interpret `[[], []]` as **`[`** and **`]`** in two columns.

| Col | Label | Typical HID / modifiers | Notes |
|-----|-------|-------------------------|--------|
| 1 | `(` | `0x26` + `MOD_SHIFT` | US: Shift+9 |
| 2 | `)` | `0x27` + `MOD_SHIFT` | US: Shift+0 |
| 3 | `[` | `0x2F` | Left bracket |
| 4 | `]` | `0x30` | Right bracket |
| 5 | `@` | `0x1F` + `MOD_SHIFT` | US: Shift+2 |
| 6 | `:` | `0x33` + `MOD_SHIFT` | Shift+semicolon |
| 7 | Fn | `KEY_FIXED_TOP_LOCAL_FN` | — |

### Analysis — Page 2

- **Layout matches strip spec:** Five “content” keys + DISPLAY + Fn when Fn off; **six** content keys + Fn when Fn on (DISPLAY hidden or inert while Fn on — reuse existing guard so DISPLAY does not toggle action-label mode incorrectly).
- **Locale / keyboard layout:** `( ) @ # "` as **Shift+digit/shift+punctuation** assumes a **US QWERTY–like** host mapping. Other national layouts may produce different glyphs; document briefly in USER_GUIDE.
- **Resolver collisions:** Bare HID codes reused on Page 1 (e.g. `0x34`, `0x37`, …) cannot map to **different** Page 2 Fn-on targets via the **same global** `switch (key.code)` unless Page 2 keys use **distinct** underlying codes. **Recommended:** dedicated **Shortcut Hub sentinel** range (e.g. `0xF015`…) for the six Page 2 cells + explicit `{ baseSend, fnOnSend }` table, **or** only use HID codes **not present** in the Page 0/1 Fn switch — Page 2’s mix of punctuation will likely force **sentinels + small dispatch**.
- **Duplication vs main keyboard:** These are deliberate **strip shortcuts** next to profiles; duplication with the letter grid is acceptable.

---

## Files to touch (implementation phase)

- [`CustomKeyboardView.java`](Openterface_KeyMod_Android/app/src/main/java/com/openterface/keymod/CustomKeyboardView.java) — `resolveFixedTopLocalFnMapping`, `buildFixedTopRowsPage2()`, `handleKeyPress` / DISPLAY guards, modifier long-press behavior on `0xE0`/`0xE2`/`0xE3` when Fn on.
- [`docs/USER_GUIDE.md`](Openterface_KeyMod_Android/docs/USER_GUIDE.md) — replace obsolete Page 1 Fn grid prose with tables above + Page 2 hub row + locale note.

---

## Superseded content

Older “avoid duplicates with legacy Page 1 Fn” constraints and the previous Page 2 proposal (Caps/Num/PrtSc/Bksp/` grave row) are **superseded** by this document.
