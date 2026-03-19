# KeyMod Android - iOS Feature Port Implementation Plan

**Created:** 2026-03-20  
**Target:** Match KeyMod iOS feature parity  
**Platform:** Android (Java/XML → consider Kotlin/Jetpack Compose migration)

---

## 📊 Feature Comparison Overview

| Priority | Feature | iOS Status | Android Status | Effort | Notes |
|----------|---------|------------|----------------|--------|-------|
| P0 | Launch Panel | ✅ Complete | ❌ Missing | Medium | Entry point for all modes |
| P0 | Keyboard & Mouse View | ✅ Complete | ⚠️ Basic | High | Needs dynamic keys, touchpad gestures |
| P0 | Settings Screen | ✅ Complete | ❌ Missing | Medium | Centralized config |
| P1 | Gamepad Mode | ✅ Complete | ❌ Missing | Very High | Complex, multiple layouts |
| P1 | Shortcut Hub | ✅ Partial | ⚠️ Basic | High | Needs profiles, drag-drop |
| P1 | Numpad Overlay | ✅ Complete | ❌ Missing | Medium | Viewport navigation |
| P2 | Macros | ✅ Complete | ❌ Missing | High | Recording/playback |
| P2 | Voice Input | ✅ Complete | ❌ Missing | High | Whisper API integration |
| P3 | Orientation Support | ✅ Auto | ⚠️ Partial | Low | Lock/unlock rotation |

---

## 🚀 Phase 1: Foundation (Week 1-2)

### 1.1 Launch Panel
**Goal:** Create mode selection screen as app entry point

**iOS Reference:** `LaunchPanelView.swift`

**Tasks:**
- [ ] Create `LaunchPanelActivity.java` or Fragment
- [ ] Design 6 mode cards (Keyboard, Gamepad, Numpad, Shortcuts, Macros, Voice)
- [ ] Implement "Remember choice" preference
- [ ] Add smooth transitions to each mode
- [ ] Update `MainActivity` to launch LaunchPanel first

**UI Components:**
```
- 3x2 grid of mode cards
- Each card: Icon + Title + Selected state
- "Remember this choice" checkbox
- "Start" button (primary) + "Skip for now" (secondary)
```

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/LaunchPanelActivity.java`
- `app/src/main/res/layout/activity_launch_panel.xml`
- `app/src/main/res/drawable/mode_card_*.xml` (6 icons)

---

### 1.2 Settings Screen
**Goal:** Centralized settings management

**iOS Reference:** `SettingsView.swift`

**Tasks:**
- [ ] Create `SettingsActivity.java` with tabbed interface
- [ ] Implement 4 tabs: General, Voice Input, AI Settings, History
- [ ] Migrate existing SharedPreferences to structured settings
- [ ] Add API key test functionality
- [ ] Add request history viewer

**Tabs:**
1. **General:** Connection preferences, display options
2. **Voice Input:** Whisper API key, language, sensitivity
3. **AI Settings:** API endpoint, model selection, test connection
4. **History:** AI request log with clear/retry options

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/SettingsActivity.java`
- `app/src/main/java/com/openterface/keymod/fragments/GeneralSettingsFragment.java`
- `app/src/main/java/com/openterface/keymod/fragments/VoiceSettingsFragment.java`
- `app/src/main/java/com/openterface/keymod/fragments/AISettingsFragment.java`
- `app/src/main/java/com/openterface/keymod/fragments/HistoryFragment.java`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/layout/fragment_settings_*.xml` (4 files)

---

### 1.3 Keyboard & Mouse Enhancement
**Goal:** Match iOS keyboard UX

**iOS Reference:** `KeyboardMouseView.swift`

**Current State:** `KeyboardFragment.java`, `CustomKeyboardView.java`, `TouchPadView.java`

**Tasks:**
- [ ] Add dynamic key display (shift/caps state awareness)
- [ ] Implement symbol variants on shift (1→!, 2→@, etc.)
- [ ] Add touchpad gesture support:
  - Single tap → Click
  - Double tap → Double click
  - Two-finger tap → Right click
  - Two-finger drag → Scroll
  - Long press → Toggle drag mode
- [ ] Add display mode toggle (Keyboard/Touchpad/Both)
- [ ] Add extra keys panel (101-key layout): PrtSc, Scroll Lock, Pause, Insert, Home, PgUp/Dn
- [ ] Implement numpad overlay for portrait mode

**Files to Modify:**
- `app/src/main/java/com/openterface/keymod/CustomKeyboardView.java`
- `app/src/main/java/com/openterface/keymod/TouchPadView.java`
- `app/src/main/java/com/openterface/fragment/KeyboardFragment.java`

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/ExtraKeysPanel.java`
- `app/src/main/res/layout/panel_extra_keys.xml`

---

## 🎮 Phase 2: Gamepad Mode (Week 3-4)

### 2.1 Gamepad Core
**Goal:** Full gamepad emulation with multiple layouts

**iOS Reference:** `GamepadView.swift`

**Tasks:**
- [ ] Create `GamepadFragment.java`
- [ ] Implement 3 layouts: Xbox, PlayStation, NES
- [ ] Add draggable components (edit mode)
- [ ] Implement button mapping system
- [ ] Add analog stick support (WASD + mouse)
- [ ] Support portrait/landscape orientations
- [ ] Add haptic feedback on button press

**Layouts:**
```
Xbox: D-Pad, ABXY, 2 sticks, 4 shoulders, 2 center
PlayStation: D-Pad, △○×□, 2 sticks, 4 shoulders, 2 center
NES: D-Pad, A/B, no sticks, Select/Start
```

**Files to Create:**
- `app/src/main/java/com/openterface/fragment/GamepadFragment.java`
- `app/src/main/java/com/openterface/keymod/GamepadView.java`
- `app/src/main/java/com/openterface/keymod/GamepadConfigManager.java`
- `app/src/main/java/com/openterface/keymod/GamepadLayout.java` (enum)
- `app/src/main/res/layout/fragment_gamepad.xml`
- `app/src/main/res/layout/view_gamepad_button.xml`
- `app/src/main/res/layout/view_analog_stick.xml`
- `app/src/main/res/layout/view_dpad.xml`

---

### 2.2 Button Configuration Popup
**Goal:** Allow custom key mapping per button

**iOS Reference:** `ButtonConfigPopup.swift`

**Tasks:**
- [ ] Create `ButtonConfigDialog.java`
- [ ] Key selection interface (keyboard picker)
- [ ] Mouse action selection (click, scroll, drag)
- [ ] Save/load per-layout configurations
- [ ] Reset to default option

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/ButtonConfigDialog.java`
- `app/src/main/res/layout/dialog_button_config.xml`

---

## ⭐ Phase 3: Shortcut Hub (Week 5-6)

### 3.1 Profile System
**Goal:** App-specific shortcut profiles

**iOS Reference:** `ShortcutHubView.swift`, `ShortcutProfileManager.swift`

**Current State:** `ShortcutFragment.java` (basic)

**Tasks:**
- [ ] Create `ShortcutProfileManager.java`
- [ ] Define JSON schema for profiles
- [ ] Add built-in profiles: Blender, KiCAD, Nomad Sculpt
- [ ] Implement profile import/export (JSON)
- [ ] Add "My Shortcuts" favorites per profile
- [ ] Implement drag-and-drop to favorites
- [ ] Add category tabs within profiles
- [ ] Add numpad overlay for viewport navigation

**Profile JSON Structure:**
```json
{
  "id": "blender-v1",
  "name": "Blender",
  "icon": "cube",
  "themeColorHex": "#E87D0D",
  "categories": [
    {
      "id": "navigation",
      "name": "Navigation",
      "icon": "move",
      "colorHex": "#007AFF",
      "shortcuts": [...]
    }
  ],
  "numpad": [...]
}
```

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/ShortcutProfileManager.java`
- `app/src/main/java/com/openterface/keymod/ShortcutProfileData.java`
- `app/src/main/java/com/openterface/keymod/ShortcutCategoryData.java`
- `app/src/main/java/com/openterface/keymod/ShortcutItem.java`
- `app/src/main/java/com/openterface/fragment/ShortcutHubFragment.java`
- `app/src/main/java/com/openterface/keymod/ShortcutCardView.java`
- `app/src/main/res/layout/fragment_shortcut_hub.xml`
- `app/src/main/res/layout/view_shortcut_card.xml`
- `app/src/main/res/raw/profiles_*.json` (built-in profiles)

---

## 🔢 Phase 4: Numpad Overlay (Week 7)

### 4.1 Viewport Numpad
**Goal:** Collapsible numpad for 3D viewport navigation

**iOS Reference:** NumpadGridView.swift (within ShortcutHubView)

**Tasks:**
- [ ] Create `NumpadOverlayView.java`
- [ ] Implement expand/collapse animation
- [ ] Add hide/show toggle
- [ ] Map keys to viewport controls (7=Numpad7, etc.)
- [ ] Support drag to reposition
- [ ] Integrate with Shortcut Hub profiles

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/NumpadOverlayView.java`
- `app/src/main/res/layout/view_numpad_overlay.xml`

---

## 🎙️ Phase 5: Advanced Features (Week 8-10)

### 5.1 Macros
**Goal:** Record and playback key sequences

**iOS Reference:** MacrosManager.swift

**Tasks:**
- [ ] Create `MacrosManager.java`
- [ ] Recording interface (start/stop)
- [ ] Playback controls
- [ ] Macro library with search
- [ ] Edit macro (add delays, modify keys)
- [ ] Export/import macros

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/MacrosManager.java`
- `app/src/main/java/com/openterface/fragment/MacrosFragment.java`
- `app/src/main/res/layout/fragment_macros.xml`

---

### 5.2 Voice Input
**Goal:** Speech-to-text via Whisper API

**iOS Reference:** WhisperManager.swift, VoiceInputView.swift

**Tasks:**
- [ ] Create `VoiceInputManager.java`
- [ ] Integrate Whisper API (or local Whisper.cpp)
- [ ] Voice input UI with waveform visualization
- [ ] Language selection
- [ ] Insert text to keyboard buffer
- [ ] Voice commands (e.g., "press enter", "copy")

**Files to Create:**
- `app/src/main/java/com/openterface/keymod/VoiceInputManager.java`
- `app/src/main/java/com/openterface/fragment/VoiceInputFragment.java`
- `app/src/main/res/layout/fragment_voice_input.xml`

---

## 📱 Phase 6: Polish & Optimization (Week 11-12)

### 6.1 Orientation Support
**Tasks:**
- [ ] Lock/unlock rotation per mode
- [ ] Save orientation preference per mode
- [ ] Smooth transition animations
- [ ] Handle camera cutout in landscape

### 6.2 Haptic Feedback
**Tasks:**
- [ ] Add `HapticFeedbackManager.java`
- [ ] Button press vibration
- [ ] Mode switch feedback
- [ ] Configurable intensity

### 6.3 Performance
**Tasks:**
- [ ] Profile memory usage
- [ ] Optimize view recycling
- [ ] Reduce input latency
- [ ] Add battery usage monitoring

---

## 🛠️ Technical Debt & Considerations

### Kotlin Migration?
**Current:** 100% Java  
**Recommendation:** Consider gradual Kotlin migration for new features

**Pros:**
- Cleaner syntax, null safety
- Better coroutine support for async
- Easier to match iOS Swift code structure

**Cons:**
- Learning curve
- Mixed Java/Kotlin build complexity

**Decision:** Start with Java, migrate incrementally if team approves

---

### Jetpack Compose?
**Current:** XML layouts + Views  
**Recommendation:** Evaluate for future major refactor

**Pros:**
- Declarative UI (matches SwiftUI)
- Less boilerplate
- Better preview tooling

**Cons:**
- Significant rewrite
- Team training required

**Decision:** Stick with Views for now, plan Compose migration for v2.0

---

## 📦 Dependencies to Add

```gradle
// Gesture detection
implementation 'androidx.core:core-ktx:1.12.0'

// JSON parsing (profiles, shortcuts)
implementation 'com.google.code.gson:gson:2.10.1'

// Preferences (settings, profiles)
implementation 'androidx.preference:preference:1.2.1'

// Drag-and-drop (shortcut hub)
// Built into Android 5.0+, no extra dep needed

// Haptic feedback
// Built into Android framework

// Whisper API (voice input)
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
```

---

## 📋 Immediate Next Steps

1. **Review this plan with Kevin** - Confirm priorities and timeline
2. **Set up project structure** - Create placeholder files for Phase 1
3. **Design assets** - Icons for 6 modes, gamepad layouts
4. **Start with Launch Panel** - Foundation for all other features

---

**Estimated Total Effort:** 12 weeks (3 months) for full parity  
**MVP (Phase 1-2):** 4 weeks - Launch Panel, Settings, Keyboard enhancements, Gamepad

**Questions?** Let me know which phase to start with! 🦾
