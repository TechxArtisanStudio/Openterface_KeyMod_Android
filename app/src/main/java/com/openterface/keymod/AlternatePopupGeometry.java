package com.openterface.keymod;

/**
 * Maps finger delta (from long-press start) to alternate popup slots using six 60° sectors.
 * Screen Y increases downward. {@code atan2(dx, -dy)} is 0° for straight up (Alt0), then sweeps
 * clockwise: Alt2, Alt4, Base, Alt3, Alt1.
 */
final class AlternatePopupGeometry {
    /** Release commits default (Alt0 if present, else base) when finger stayed inside min radius. */
    static final int RESULT_DEFAULT = -1;
    /** Oversized movement or empty sector — release does not send a character. */
    static final int RESULT_CANCEL = -2;

    static final int SLOT_ALT0 = 0;
    static final int SLOT_ALT1 = 1;
    static final int SLOT_ALT2 = 2;
    static final int SLOT_ALT3 = 3;
    static final int SLOT_ALT4 = 4;
    static final int SLOT_BASE = 5;

    /**
     * Sector index 0 = angle ~0° (up), then clockwise. Maps to logical slots (see product spec).
     */
    private static final int[] SECTOR_TO_SLOT = {
            SLOT_ALT0, SLOT_ALT2, SLOT_ALT4, SLOT_BASE, SLOT_ALT3, SLOT_ALT1
    };

    private AlternatePopupGeometry() {
    }

    /**
     * @param dx           finger X minus start X (px)
     * @param dy           finger Y minus start Y (px), positive downward
     * @param rMinPx       inside this radius → {@link #RESULT_DEFAULT}
     * @param rCancelPx    beyond this radius → {@link #RESULT_CANCEL}
     * @param slotOccupied length 6; {@code slotOccupied[i]} true if slot {@code i} has a selectable character
     * @return {@link #RESULT_DEFAULT}, {@link #RESULT_CANCEL}, or slot {@code 0}–{@code 5}
     */
    static int pickSlot(float dx, float dy, float rMinPx, float rCancelPx, boolean[] slotOccupied) {
        double r = Math.hypot(dx, dy);
        if (r > rCancelPx) {
            return RESULT_CANCEL;
        }
        if (r <= rMinPx) {
            return RESULT_DEFAULT;
        }
        double angleDeg = (Math.toDegrees(Math.atan2(dx, -dy)) + 360.0) % 360.0;
        int sector = (int) Math.floor((angleDeg + 30.0) / 60.0) % 6;
        int slot = SECTOR_TO_SLOT[sector];
        if (slotOccupied != null && slot >= 0 && slot < slotOccupied.length && !slotOccupied[slot]) {
            return RESULT_CANCEL;
        }
        return slot;
    }
}
