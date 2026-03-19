package com.openterface.keymod;

/**
 * Gamepad Layout Enum
 * Defines supported gamepad configurations
 */
public enum GamepadLayout {
    XBOX("Xbox", 11),      // 11 components: D-Pad, ABXY, 2 sticks, 4 shoulders, 2 center
    PLAYSTATION("PlayStation", 11),  // 11 components: D-Pad, △○×□, 2 sticks, 4 shoulders, 2 center
    NES("NES", 6);         // 6 components: D-Pad, A/B, Select/Start, no sticks

    private final String displayName;
    private final int componentCount;

    GamepadLayout(String displayName, int componentCount) {
        this.displayName = displayName;
        this.componentCount = componentCount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getComponentCount() {
        return componentCount;
    }
}
