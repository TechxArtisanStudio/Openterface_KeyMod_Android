package com.openterface.keymod.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.openterface.keymod.R;
import com.openterface.keymod.ShortcutProfileManager;

/**
 * Shared row binding for "My shortcuts" list rows (strip picker, reorder sheet, Hub list).
 */
public final class ShortcutFavoriteRowViews {

    private ShortcutFavoriteRowViews() {
    }

    public static int resolveShortcutIconRes(@NonNull Context ctx, String iconName) {
        if (iconName == null) {
            return 0;
        }
        String raw = iconName.trim();
        if (raw.isEmpty() || isEmojiIcon(raw)) {
            return 0;
        }
        return ctx.getResources().getIdentifier(raw, "drawable", ctx.getPackageName());
    }

    public static boolean isEmojiIcon(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        return !raw.matches("^[A-Za-z0-9_]+$");
    }

    private static int listRowIconTint(@NonNull Context ctx) {
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? 0xFFFFFFFF : 0xFF000000;
    }

    /**
     * Binds {@link R.layout#dialog_row_top_strip_favorite} (or any layout with the same ids).
     */
    public static void bindFavoriteStripRow(
            @NonNull Context ctx,
            @NonNull View row,
            @NonNull ShortcutProfileManager.Shortcut shortcut,
            @NonNull String targetOs
    ) {
        ImageView iconDrawable = row.findViewById(R.id.favorite_row_icon_drawable);
        TextView iconEmoji = row.findViewById(R.id.favorite_row_icon_emoji);
        TextView nameTv = row.findViewById(R.id.favorite_row_name);
        TextView chordTv = row.findViewById(R.id.favorite_row_chord);

        String chord = "";
        if (shortcut.label != null && !shortcut.label.trim().isEmpty()) {
            chord = KeyParser.displayLabel(shortcut.label.trim(), targetOs);
        }
        chordTv.setText(chord);

        String displayName = "";
        if (!TextUtils.isEmpty(shortcut.name)) {
            displayName = shortcut.name.trim();
        } else if (shortcut.label != null) {
            displayName = shortcut.label.trim();
        }
        if (displayName.isEmpty()) {
            displayName = "Key";
        }
        nameTv.setText(displayName);

        int iconRes = resolveShortcutIconRes(ctx, shortcut.icon);
        if (iconRes != 0) {
            iconDrawable.setImageResource(iconRes);
            iconDrawable.setColorFilter(listRowIconTint(ctx), PorterDuff.Mode.SRC_IN);
            iconDrawable.setVisibility(View.VISIBLE);
            iconEmoji.setVisibility(View.GONE);
        } else {
            iconDrawable.setImageDrawable(null);
            iconDrawable.clearColorFilter();
            iconDrawable.setVisibility(View.GONE);
            String raw = shortcut.icon != null ? shortcut.icon.trim() : "";
            if (isEmojiIcon(raw)) {
                iconEmoji.setText(raw);
                iconEmoji.setVisibility(View.VISIBLE);
            } else {
                iconEmoji.setVisibility(View.GONE);
            }
        }
    }
}
