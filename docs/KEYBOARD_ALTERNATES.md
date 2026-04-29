# Letter keyboard: alternates and HID mapping

This document describes how **extra symbols** (long-press popup, keycap hints) are defined for the main QWERTY layout and how they map to HID in code.

## Source files

| File | Role |
|------|------|
| [`app/src/main/res/xml/keyboard_lower_landscape.xml`](../app/src/main/res/xml/keyboard_lower_landscape.xml) | Landscape letter keyboard |
| [`app/src/main/res/xml/keyboard_lower_portrait.xml`](../app/src/main/res/xml/keyboard_lower_portrait.xml) | Portrait letter keyboard |

Keep **landscape and portrait** definitions aligned unless you intentionally diverge.

## Code paths

| Layer | Location | Purpose |
|--------|----------|---------|
| Parse XML | `CustomKeyboardView.parseKeyboard()` | Reads `android:codes`, `keyLabel`, `keySymbolLabel`, `keyAlternates`, `keyCornerHint`. Custom attrs are passed through `decodeKeyboardXmlEntities` so values like `&#92;` / `&amp;` become real single characters (XmlPullParser does not decode these in custom attrs). |
| Slot fill | `fillAlternateSlotOptions(Key, AlternateOption[])` | **Alt0** = capital of base when `keyLabel` is one lowercase letter `a`–`z` (mapped via `mapAsciiAlternate`). **Alt1–Alt4** = first up to four comma-separated tokens from `keyAlternates` (same token rules as before). **Base** = single-character `keyLabel` when mappable. For **`/`** only: if **Alt1** is still empty after parsing, code fills **`\`** then **`|`** in Alt1/Alt2 so backslash is never dropped when XML entities mis-parse. |
| Popup layout | `showAlternatesPopup` | 3×2 grid: row1 Alt1, Alt0, Alt2; row2 Alt3, Base, Alt4. Empty slots stay blank and are not selectable. |
| Gesture → slot | `AlternatePopupGeometry.pickSlot` | Delta from touch-down: inner radius → default; outer radius → cancel; else six 60° sectors (unit-tested in `AlternatePopupGeometryTest`). |
| Character → HID | `mapAsciiAlternate(String token)` | Each label must be **exactly one character** and supported here, or that slot is **empty**. |
| Default commit | `AlternatePopupModel.defaultOption` | First occupied slot in order **Alt0 → Alt1 → Alt2 → Alt3 → Alt4 → Base** (lift without moving past the inner gesture radius). |
| Send | `sendAlternateOption(...)` | Applies Shift lock + per-option `requiresShift`, then `sendKeyData`. |

## XML attributes (letter keys)

| Attribute | Meaning |
|-----------|---------|
| `android:codes` | Base key HID usage (hex string in XML). |
| `android:keyLabel` | Main label (often one letter). |
| `custom:keySymbolLabel` | Shift/preview label on key face; not an extra long-press slot. |
| `custom:keyAlternates` | Comma-separated **single-character** tokens (after trim). Tokens fill **Alt1**, then **Alt2**, **Alt3**, **Alt4** in order (max four). **Alt0** is not set from XML; it is the shifted letter for `a`–`z` bases. |
| `custom:keyCornerHint` | Legacy small hint (top-end) when there is **no** Alt1–4 hint row from `keyAlternates`; prefer defining alternates so the top hint row appears. |

### XML escaping (common)

Use these inside attribute values when needed:

| Character | In XML |
|-----------|--------|
| `<` | `&lt;` |
| `>` | `&gt;` |
| `&` | `&amp;` |
| `"` | `&quot;` |
| `` ` `` | `&#96;` |
| `\` | `&#92;` |

---

## Current letter layout: key face + alternates (keycap row shows Alt1–4)

Base HID = `android:codes` (hex). The table’s `keyAlternates` column is unchanged in XML; the app maps tokens to **Alt1+** as above. **Alt0** is implicit for letter keys.

| Base key | `codes` | `keySymbolLabel` (key face) | `keyAlternates` (comma-separated) | Corner hint |
|----------|---------|-------------------------------|-----------------------------------|---------------|
| q | 14 | Q | 1 | 1 |
| w | 1A | W | 2 | 2 |
| e | 08 | E | 3 | 3 |
| r | 15 | R | 4 | 4 |
| t | 17 | T | 5 | 5 |
| y | 1C | Y | 6 | 6 |
| u | 18 | U | 7 | 7 |
| i | 0C | I | 8 | 8 |
| o | 12 | O | 9 | 9 |
| p | 13 | P | 0 | 0 |
| a | 04 | A | @ | @ |
| s | 16 | S | # | # |
| d | 07 | D | $ , € , ¥ , £ | $ |
| f | 09 | F | % | % |
| g | 0A | G | ^ | ^ |
| h | 0B | H | & | & |
| j | 0D | J | * | * |
| k | 0E | K | ( , { , [ , < | ( |
| l | 0F | L | ) , } , ] , > | ) |
| z | 1D | Z | ! | ! |
| x | 1B | X | ? | ? |
| c | 06 | C | ; | ; |
| v | 19 | V | : | : |
| b | 05 | B | ' | ' |
| n | 11 | N | " , ' | " |
| m | 10 | M | + , _ | + |
| / | 38 | ? | `&#92;,&#124;` → `\`, `\|` | backslash |
| , | 36 | ; | ` , - , ~ | ` |
| . | 37 | ' | = | = |

Keys in this layout that **do not** use `mapAsciiAlternate` via the above pattern include **Shift**, **BackSpace**, **Fn**, **Win/Cmd/Super** (string label + icon), **Space**, and **Enter** (icons / strings / special handling).

---

## `mapAsciiAlternate`: display character → HID + Shift

Implemented in `CustomKeyboardView.mapAsciiAlternate`.  
`requiresShift = true` means an extra Shift modifier is applied for that option (in addition to any Shift lock already on).

### Letters

| Char | HID usage | Shift? |
|------|-----------|--------|
| a–z | 0x04 + (c − 'a') | no |
| A–Z | 0x04 + (c − 'A') | yes |

### Digits / shifted number row

| Char | Code | Shift? |
|------|------|--------|
| 1 ! | 0x1E | no / yes |
| 2 @ | 0x1F | no / yes |
| 3 # | 0x20 | no / yes |
| 4 $ | 0x21 | no / yes |
| 5 % | 0x22 | no / yes |
| 6 ^ | 0x23 | no / yes |
| 7 & | 0x24 | no / yes |
| 8 * | 0x25 | no / yes |
| 9 ( | 0x26 | no / yes |
| 0 ) | 0x27 | no / yes |

### Punctuation and brackets

| Char | Code | Shift? |
|------|------|--------|
| - _ | 0x2D | no / yes |
| = + | 0x2E | no / yes |
| [ { | 0x2F | no / yes |
| ] } | 0x30 | no / yes |
| ; : | 0x33 | no / yes |
| ' " | 0x34 | no / yes |
| ` ~ | 0x35 | no / yes |
| , < | 0x36 | no / yes |
| . > | 0x37 | no / yes |
| / ? | 0x38 | no / yes |
| \\ \| | 0x31 | no / yes |

Currency alternates also supported:

| Char | Code | Modifiers |
|------|------|-----------|
| € | 0x1F | Alt + Shift |
| ¥ | 0x1C | Alt |
| £ | 0x20 | Alt |

Any character **not** listed above will not produce a valid long-press option until you add a `case` in `mapAsciiAlternate` (and use a single-character token in XML).

---

## Related layouts (different model)

| Layout | Notes |
|--------|--------|
| `keyboard_lower_*_symbols.xml` | Direct keys with `android:codes`; not the alternates / `mapAsciiAlternate` pipeline. |
| Extra numpad / Fn layer | `resolveFnMapping` / `resolveExtraNumpadFnMapping` in `CustomKeyboardView`; separate from this table. |

---

## Quick checklist when adding a new alternate

1. Add a **single character** to `custom:keyAlternates` (comma-separated) in **both** portrait and landscape XML if they should match. Order is **Alt1**, then **Alt2**, **Alt3**, **Alt4** (up to four tokens).
2. If the character is new, add a `case` in `mapAsciiAlternate` with the correct HID usage and `requiresShift`.
3. If the character needs XML escaping, use entities from the table above.
4. Confirm the key still has **at least two** selectable slots (base + at least one mapped alternate, counting implicit **Alt0** for `a`–`z`), or long-press will not open.
5. **`keyCornerHint`** is optional fallback UI when no Alt1–4 hint row is shown; prefer listing symbols in `keyAlternates` so the top hint row appears.
6. Default long-press (lift without moving past the inner radius) sends the first available character in order **Alt0** (capital for `a`–`z`), then **Alt1**…**Alt4**, then **Base**. For **`/`**, with no letter Alt0, default is **Alt1** `\` when present. Use **`&#124;`** for pipe so the list parses reliably.
7. For **`n`**, keep `"` before `'` in `keyAlternates` if **Alt1** should be `"` and **Alt2** `'`; **`keySymbolLabel`** stays **`N`** for shift.
