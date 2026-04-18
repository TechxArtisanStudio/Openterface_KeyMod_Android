package com.openterface.keymod.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses macro-style token strings (e.g. <CTRL>C</CTRL>) into HID key codes and modifiers.
 * Shared between MacrosFragment and ShortcutHubFragment.
 */
public class KeyParser {

    public static class ParsedKey {
        public final int keyCode;   // HID key code, -1 if invalid
        public final int modifiers; // modifier bitmask

        public ParsedKey(int keyCode, int modifiers) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }
    }

    /**
     * Parse a single token string and return modifiers + key code.
     * The first non-modifier key found is the shortcut key.
     */
    public static ParsedKey parse(String data) {
        Pattern p = Pattern.compile("<[^>]+>|.", Pattern.DOTALL);
        Matcher m = p.matcher(data);

        int activeModifiers = 0;
        int foundKeyCode = -1;

        while (m.find()) {
            String token = m.group();
            if (token == null || token.isEmpty()) continue;

            int mod = parseModifier(token);
            if (mod != 0) {
                activeModifiers |= mod;
                continue;
            }
            if (token.startsWith("</")) {
                int unmod = parseCloseModifier(token);
                if (unmod != 0) {
                    activeModifiers &= ~unmod;
                    continue;
                }
            }
            // Skip delay tokens
            if (token.startsWith("<DELAY")) continue;

            int keyCode = mapTokenToHidCode(token);
            if (keyCode < 0 && token.length() == 1) {
                char c = token.charAt(0);
                keyCode = mapCharToHidCode(c);
                if (needsShift(c)) {
                    activeModifiers |= 0x02;
                }
            }

            if (keyCode >= 0 && foundKeyCode < 0) {
                foundKeyCode = keyCode;
            }
        }

        return new ParsedKey(foundKeyCode, activeModifiers);
    }

    private static int parseModifier(String token) {
        switch (token) {
            case "<CTRL>": return 0x01;
            case "<SHIFT>": return 0x02;
            case "<ALT>": return 0x04;
            case "<CMD>": return 0x08;
            default: return 0;
        }
    }

    private static int parseCloseModifier(String token) {
        switch (token) {
            case "</CTRL>": return 0x01;
            case "</SHIFT>": return 0x02;
            case "</ALT>": return 0x04;
            case "</CMD>": return 0x08;
            default: return 0;
        }
    }

    public static int mapTokenToHidCode(String token) {
        switch (token) {
            case "<ESC>": return 41;
            case "<BACK>": return 42;
            case "<ENTER>": return 40;
            case "<SPACE>": return 44;
            case "<LEFT>": return 80;
            case "<RIGHT>": return 79;
            case "<UP>": return 82;
            case "<DOWN>": return 81;
            case "<HOME>": return 74;
            case "<END>": return 77;
            case "<TAB>": return 43;
            case "<DEL>": return 76;
            case "<INS>": return 73;
            case "<PGUP>": return 75;
            case "<PGDN>": return 78;
            case "<F1>": return 58;
            case "<F2>": return 59;
            case "<F3>": return 60;
            case "<F4>": return 61;
            case "<F5>": return 62;
            case "<F6>": return 63;
            case "<F7>": return 64;
            case "<F8>": return 65;
            case "<F9>": return 66;
            case "<F10>": return 67;
            case "<F11>": return 68;
            case "<F12>": return 69;
            case "\n": return 40;
            case "\t": return 43;
            default: return -1;
        }
    }

    public static int mapCharToHidCode(char c) {
        if (c >= 'a' && c <= 'z') return 4 + (c - 'a');
        if (c >= 'A' && c <= 'Z') return 4 + (c - 'A');
        if (c >= '1' && c <= '9') return 30 + (c - '1');
        if (c == '0') return 39;

        switch (c) {
            case ' ': return 44;
            case '-': case '_': return 45;
            case '=': case '+': return 46;
            case '[': case '{': return 47;
            case ']': case '}': return 48;
            case '\\': case '|': return 49;
            case '#': case '`': return 53;
            case ';': case ':': return 51;
            case '\'': case '"': return 52;
            case '~': return 53;
            case ',': case '<': return 54;
            case '.': case '>': return 55;
            case '/': case '?': return 56;
            case '!': return 30;
            case '@': return 31;
            case '%': return 34;
            case '^': return 35;
            case '&': return 36;
            case '*': return 37;
            case '(': return 38;
            case ')': return 39;
            default: return -1;
        }
    }

    public static boolean needsShift(char c) {
        if (Character.isUpperCase(c)) return true;
        return "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
    }

    public static String toLabel(int keyCode, int modifiers) {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & 0x08) != 0) sb.append("Cmd+");
        if ((modifiers & 0x01) != 0) sb.append("Ctrl+");
        if ((modifiers & 0x02) != 0) sb.append("Shift+");
        if ((modifiers & 0x04) != 0) sb.append("Alt+");

        if (keyCode >= 4 && keyCode <= 29) {
            sb.append((char) ('A' + keyCode - 4));
        } else if (keyCode >= 30 && keyCode <= 38) {
            sb.append((char) ('1' + keyCode - 30));
        } else if (keyCode == 39) {
            sb.append('0');
        } else {
            switch (keyCode) {
                case 40: sb.append("Enter"); break;
                case 41: sb.append("Esc"); break;
                case 42: sb.append("Back"); break;
                case 43: sb.append("Tab"); break;
                case 44: sb.append("Space"); break;
                case 74: sb.append("Home"); break;
                case 75: sb.append("PgUp"); break;
                case 76: sb.append("Del"); break;
                case 77: sb.append("End"); break;
                case 78: sb.append("PgDn"); break;
                case 79: sb.append("Right"); break;
                case 80: sb.append("Left"); break;
                case 81: sb.append("Down"); break;
                case 82: sb.append("Up"); break;
                default: sb.append("Key ").append(keyCode); break;
            }
        }
        return sb.toString();
    }

    /**
     * Convert keyCode + modifiers back to macro token format (e.g. <CTRL><SHIFT>F</SHIFT></CTRL>).
     */
    /**
     * Convert shortcut label for display based on target OS.
     * For macOS: Ctrl → Cmd, Alt → Opt
     * For Windows/Linux: unchanged
     */
    public static String displayLabel(String label, String targetOs) {
        if (label == null) return "";
        if ("macos".equals(targetOs)) {
            return label
                    .replace("Ctrl+", "Cmd+")
                    .replace("Alt+", "Opt+");
        }
        return label;
    }

    /**
     * Convert keyCode + modifiers back to macro token format (e.g. <CTRL><SHIFT>F</SHIFT></CTRL>).
     */
    public static String toToken(int keyCode, int modifiers) {
        StringBuilder sb = new StringBuilder();
        // Open modifiers
        if ((modifiers & 0x08) != 0) sb.append("<CMD>");
        if ((modifiers & 0x01) != 0) sb.append("<CTRL>");
        if ((modifiers & 0x02) != 0) sb.append("<SHIFT>");
        if ((modifiers & 0x04) != 0) sb.append("<ALT>");

        // Key token
        switch (keyCode) {
            case 40: sb.append("<ENTER>"); break;
            case 41: sb.append("<ESC>"); break;
            case 42: sb.append("<BACK>"); break;
            case 43: sb.append("<TAB>"); break;
            case 44: sb.append("<SPACE>"); break;
            case 74: sb.append("<HOME>"); break;
            case 75: sb.append("<PGUP>"); break;
            case 76: sb.append("<DEL>"); break;
            case 77: sb.append("<END>"); break;
            case 78: sb.append("<PGDN>"); break;
            case 79: sb.append("<RIGHT>"); break;
            case 80: sb.append("<LEFT>"); break;
            case 81: sb.append("<DOWN>"); break;
            case 82: sb.append("<UP>"); break;
            case 58: sb.append("<F1>"); break;
            case 59: sb.append("<F2>"); break;
            case 60: sb.append("<F3>"); break;
            case 61: sb.append("<F4>"); break;
            case 62: sb.append("<F5>"); break;
            case 63: sb.append("<F6>"); break;
            case 64: sb.append("<F7>"); break;
            case 65: sb.append("<F8>"); break;
            case 66: sb.append("<F9>"); break;
            case 67: sb.append("<F10>"); break;
            case 68: sb.append("<F11>"); break;
            case 69: sb.append("<F12>"); break;
            default:
                if (keyCode >= 4 && keyCode <= 29) {
                    sb.append((char) ('A' + keyCode - 4));
                } else if (keyCode >= 30 && keyCode <= 39) {
                    sb.append((char) ('1' + (keyCode - 30) % 10));
                } else {
                    sb.append("Key ").append(keyCode);
                }
                break;
        }

        // Close modifiers (reverse order)
        if ((modifiers & 0x04) != 0) sb.append("</ALT>");
        if ((modifiers & 0x02) != 0) sb.append("</SHIFT>");
        if ((modifiers & 0x01) != 0) sb.append("</CTRL>");
        if ((modifiers & 0x08) != 0) sb.append("</CMD>");
        return sb.toString();
    }
}
