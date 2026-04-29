package com.openterface.keymod;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AlternatePopupGeometryTest {

    private static boolean[] allOn() {
        boolean[] o = new boolean[6];
        java.util.Arrays.fill(o, true);
        return o;
    }

    @Test
    public void insideMinRadius_returnsDefault() {
        int r = AlternatePopupGeometry.pickSlot(3f, 2f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.RESULT_DEFAULT, r);
    }

    @Test
    public void beyondCancel_returnsCancel() {
        int r = AlternatePopupGeometry.pickSlot(500f, 0f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.RESULT_CANCEL, r);
    }

    @Test
    public void straightUp_selectsAlt0() {
        int r = AlternatePopupGeometry.pickSlot(0f, -80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_ALT0, r);
    }

    @Test
    public void straightDown_selectsBase() {
        int r = AlternatePopupGeometry.pickSlot(0f, 80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_BASE, r);
    }

    @Test
    public void leftSideUp_selectsAlt1() {
        int r = AlternatePopupGeometry.pickSlot(-80f, -80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_ALT1, r);
    }

    @Test
    public void rightSideUp_selectsAlt2() {
        int r = AlternatePopupGeometry.pickSlot(80f, -80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_ALT2, r);
    }

    @Test
    public void leftSideDown_selectsAlt3() {
        int r = AlternatePopupGeometry.pickSlot(-80f, 80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_ALT3, r);
    }

    @Test
    public void rightSideDown_selectsAlt4() {
        int r = AlternatePopupGeometry.pickSlot(80f, 80f, 10f, 200f, allOn());
        assertEquals(AlternatePopupGeometry.SLOT_ALT4, r);
    }

    @Test
    public void emptySlot_returnsCancel() {
        boolean[] o = allOn();
        o[AlternatePopupGeometry.SLOT_ALT2] = false;
        int r = AlternatePopupGeometry.pickSlot(80f, -80f, 10f, 200f, o);
        assertEquals(AlternatePopupGeometry.RESULT_CANCEL, r);
    }
}
