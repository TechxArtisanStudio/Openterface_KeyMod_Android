package com.openterface.keymod.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.core.content.ContextCompat;

import com.openterface.keymod.R;

/**
 * Rich text for the on-pad status overlay and gesture help overlay.
 */
public final class TouchPadTipsFormatter {

    private TouchPadTipsFormatter() {}

    /** Title + drag line only (shown on the touchpad surface). */
    public static CharSequence buildCompact(Context context, boolean dragModeOn) {
        String title = context.getString(R.string.touch_pad_title);
        String drag = dragModeOn
                ? context.getString(R.string.touch_pad_drag_on)
                : context.getString(R.string.touch_pad_drag_off);
        String full = title + "\n" + drag;
        SpannableStringBuilder ssb = new SpannableStringBuilder(full);

        int titleEnd = title.length();
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_primary)),
                0,
                titleEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new RelativeSizeSpan(1.08f), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int dragStart = titleEnd + 1;
        int dragEnd = dragStart + drag.length();
        int dragColor = dragModeOn
                ? ContextCompat.getColor(context, R.color.primary)
                : ContextCompat.getColor(context, R.color.text_secondary);
        ssb.setSpan(new ForegroundColorSpan(dragColor), dragStart, dragEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), dragStart, dragEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }

    /** Plain full message (no spans); useful for tests or sharing. */
    public static String buildGestureHelpMessage(Context context) {
        String title = context.getString(R.string.touch_pad_help_overlay_title);
        String one = context.getString(R.string.touch_pad_help_overlay_one_finger);
        String oneD = context.getString(R.string.touch_pad_help_overlay_one_finger_detail);
        String two = context.getString(R.string.touch_pad_help_overlay_two_fingers);
        String twoD = context.getString(R.string.touch_pad_help_overlay_two_fingers_detail);
        return title + "\n\n" + one + "\n" + oneD + "\n\n" + two + "\n" + twoD;
    }

    /** Title + body: bold title, bold “One finger” / “Two fingers” lines; body uses theme primary color. */
    public static CharSequence buildGestureHelpOverlayText(Context context) {
        String title = context.getString(R.string.touch_pad_help_overlay_title);
        String one = context.getString(R.string.touch_pad_help_overlay_one_finger);
        String oneD = context.getString(R.string.touch_pad_help_overlay_one_finger_detail);
        String two = context.getString(R.string.touch_pad_help_overlay_two_fingers);
        String twoD = context.getString(R.string.touch_pad_help_overlay_two_fingers_detail);

        String full = title + "\n\n" + one + "\n" + oneD + "\n\n" + two + "\n" + twoD;
        SpannableStringBuilder ssb = new SpannableStringBuilder(full);
        int primary = ContextCompat.getColor(context, R.color.text_primary);

        int titleEnd = title.length();
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(primary), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int oneStart = titleEnd + 2;
        int oneEnd = oneStart + one.length();
        ssb.setSpan(new StyleSpan(Typeface.BOLD), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(primary), oneStart, oneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int twoStart = oneEnd + 1 + oneD.length() + 2;
        int twoEnd = twoStart + two.length();
        ssb.setSpan(new StyleSpan(Typeface.BOLD), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(primary), twoStart, twoEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }
}
