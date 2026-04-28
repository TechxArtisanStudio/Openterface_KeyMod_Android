# Letter keyboard: alternates and HID mapping

This document describes how **extra symbols** (long-press popup, corner hints) are defined for the main QWERTY layout and how they map to HID in code.

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
| Long-press options | `buildAlternateOptions(Key key)` | Builds ordered list: **each alternate token** (via `splitAlternatesTokens` + per-token `decodeKeyboardXmlEntities`) → **base label** (if single char). `keySymbolLabel` is not included in popup options. Deduped with `LinkedHashSet`. Then `reorderAlternatesWithCornerHintFirstAndKeepBaseRight` moves **`keyCornerHint`** to the **leftmost** popup tile (if present) and keeps base key at the far right. |
| Character → HID | `mapAsciiAlternate(String token)` | Each popup label must be **exactly one character** and supported here, or it is **dropped**. |
| Default selection | `findDefaultAlternateSelection(...)` | Always selects **leftmost** popup tile (`index 0`) when options exist. |
| Send | `sendAlternateOption(...)` | Applies Shift lock + per-option `requiresShift`, then `sendKeyData`. |

## XML attributes (letter keys)

| Attribute | Meaning |
|-----------|---------|
| `android:codes` | Base key HID usage (hex string in XML). |
| `android:keyLabel` | Main label (often one letter). |
| `custom:keySymbolLabel` | Shift/preview label shown on key face; not part of long-press popup options. |
| `custom:keyAlternates` | Comma-separated list of **single-character** tokens (after trim). Order affects popup order (before base key). |
| `custom:keyCornerHint` | Small hint drawn on the key; should match a long-press character. After build, that character is shown as the **leftmost** popup tile when possible. |

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

## Current letter layout: key face + alternates + corner

Base HID = `android:codes` (hex).

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
| d | 07 | D | $ | $ |
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

Any character **not** listed above will not produce a valid long-press option until you add a `case` in `mapAsciiAlternate` (and use a single-character token in XML).

---

## Related layouts (different model)

| Layout | Notes |
|--------|--------|
| `keyboard_lower_*_symbols.xml` | Direct keys with `android:codes`; not the alternates / `mapAsciiAlternate` pipeline. |
| Extra numpad / Fn layer | `resolveFnMapping` / `resolveExtraNumpadFnMapping` in `CustomKeyboardView`; separate from this table. |

---

## Quick checklist when adding a new alternate

1. Add a **single character** to `custom:keyAlternates` (or adjust `keySymbolLabel` / `keyLabel`) in **both** portrait and landscape XML if they should match.
2. If the character is new, add a `case` in `mapAsciiAlternate` with the correct HID usage and `requiresShift`.
3. If the character needs XML escaping, use entities from the table above.
4. Confirm long-press shows **at least two** options (otherwise `showAlternatesPopup` may no-op).
5. **`keyCornerHint`** should be a single character that also appears in the long-press set (alternates or base). It becomes the **leftmost** popup tile when reorder applies.
6. Default long-press selection is always the leftmost tile (`index 0`). For **`/`**, use `keyAlternates="&#92;,&#124;"` and `keyCornerHint="&#92;"`; long-press is **`\`, `|`, `/`** only (no `?`). Use **`&#124;`** for pipe in XML so the comma-separated list parses reliably.
7. For **`n`**, `"` stays first in `keyAlternates` (with **`'`** after it); **`keySymbolLabel`** stays **`N`** for shift; corner stays **`"`**.
