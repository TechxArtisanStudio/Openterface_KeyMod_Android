package com.openterface.keymod;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.openterface.keymod.util.KeyParser;
import com.openterface.keymod.util.MyShortcutsReorderHelpReadModeDialog;

/**
 * Quick-create shortcut from the keyboard strip: modifiers + key chips (or advanced token string),
 * then save to General and Favorites.
 */
public final class CreateShortcutBottomSheet {

    private CreateShortcutBottomSheet() {
    }

    private static int dpToPx(Context ctx, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }

    @NonNull
    private static String targetOsDisplayName(@NonNull Context ctx, @Nullable String targetOs) {
        if ("windows".equals(targetOs)) {
            return ctx.getString(R.string.target_os_windows);
        }
        if ("linux".equals(targetOs)) {
            return ctx.getString(R.string.target_os_linux);
        }
        return ctx.getString(R.string.target_os_macos);
    }

    /**
     * Same read-mode help UX as {@link MyShortcutsReorderHelpReadModeDialog} for reorder Favorites.
     */
    @NonNull
    private static CharSequence formatCreateShortcutHelpReadText(@NonNull Context ctx, @NonNull String targetOs) {
        final int htmlMode = HtmlCompat.FROM_HTML_MODE_COMPACT;
        String osName = targetOsDisplayName(ctx, targetOs);
        String title = ctx.getString(R.string.create_shortcut_help_read_mode_title, osName);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.12f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block1), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block2), htmlMode));
        sb.append("\n\n");
        sb.append(HtmlCompat.fromHtml(
                ctx.getString(R.string.create_shortcut_help_read_mode_block3), htmlMode));
        return sb;
    }

    /**
     * Labels match {@link com.openterface.keymod.CustomKeyboardView#buildTopPanelModifierKey} / app Target OS.
     */
    private static void applyModifierButtonLabelsForTargetOs(
            @NonNull Context ctx,
            @Nullable String targetOs,
            @NonNull MaterialButton modCmd,
            @NonNull MaterialButton modCtrl,
            @NonNull MaterialButton modShift,
            @NonNull MaterialButton modAlt
    ) {
        String os = targetOs != null ? targetOs : "macos";
        String shift = ctx.getString(R.string.Shift);
        if ("macos".equals(os)) {
            modCmd.setText("⌘");
            modCmd.setContentDescription(ctx.getString(R.string.modifier_command));
            modCtrl.setText(ctx.getString(R.string.modifier_control));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_control));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_option));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_option));
        } else if ("linux".equals(os)) {
            modCmd.setText(ctx.getString(R.string.modifier_sup));
            modCmd.setContentDescription(ctx.getString(R.string.modifier_sup));
            modCtrl.setText(ctx.getString(R.string.modifier_ctrl));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_ctrl));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_alt));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_alt));
        } else {
            modCmd.setText(ctx.getString(R.string.modifier_win));
            modCmd.setContentDescription(ctx.getString(R.string.modifier_win));
            modCtrl.setText(ctx.getString(R.string.modifier_ctrl));
            modCtrl.setContentDescription(ctx.getString(R.string.modifier_ctrl));
            modShift.setText(shift);
            modShift.setContentDescription(shift);
            modAlt.setText(ctx.getString(R.string.modifier_alt));
            modAlt.setContentDescription(ctx.getString(R.string.modifier_alt));
        }
    }

    private static String buildMacroData(int modifierMask, String innerKeyToken) {
        StringBuilder sb = new StringBuilder();
        if ((modifierMask & 0x08) != 0) {
            sb.append("<CMD>");
        }
        if ((modifierMask & 0x01) != 0) {
            sb.append("<CTRL>");
        }
        if ((modifierMask & 0x02) != 0) {
            sb.append("<SHIFT>");
        }
        if ((modifierMask & 0x04) != 0) {
            sb.append("<ALT>");
        }
        sb.append(innerKeyToken);
        if ((modifierMask & 0x04) != 0) {
            sb.append("</ALT>");
        }
        if ((modifierMask & 0x02) != 0) {
            sb.append("</SHIFT>");
        }
        if ((modifierMask & 0x01) != 0) {
            sb.append("</CTRL>");
        }
        if ((modifierMask & 0x08) != 0) {
            sb.append("</CMD>");
        }
        return sb.toString();
    }

    private static int readModifierMask(
            MaterialButton cmd,
            MaterialButton ctrl,
            MaterialButton shift,
            MaterialButton alt
    ) {
        int m = 0;
        if (cmd.isChecked()) {
            m |= 0x08;
        }
        if (ctrl.isChecked()) {
            m |= 0x01;
        }
        if (shift.isChecked()) {
            m |= 0x02;
        }
        if (alt.isChecked()) {
            m |= 0x04;
        }
        return m;
    }

    @Nullable
    private static String selectedKeyToken(ChipGroup keyChips) {
        for (int i = 0; i < keyChips.getChildCount(); i++) {
            View v = keyChips.getChildAt(i);
            if (v instanceof Chip && ((Chip) v).isChecked()) {
                Object tag = v.getTag();
                return tag instanceof String ? (String) tag : null;
            }
        }
        return null;
    }

    private static void addKeyChip(
            AppCompatActivity activity,
            ChipGroup group,
            int layoutRes,
            String label,
            String token
    ) {
        LayoutInflater inflater = activity.getLayoutInflater();
        Chip chip = (Chip) inflater.inflate(layoutRes, group, false);
        chip.setText(label);
        chip.setTag(token);
        chip.setContentDescription(label);
        group.addView(chip);
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            @Nullable Runnable onSaved
    ) {
        ShortcutProfileManager.ShortcutProfile profile = profileManager.getProfileById(profileId);
        if (profile == null) {
            Toast.makeText(activity, R.string.create_shortcut_no_profile, Toast.LENGTH_SHORT).show();
            return;
        }

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_create_shortcut, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        ImageButton info = root.findViewById(R.id.create_shortcut_info);
        if (info != null) {
            info.setOnClickListener(v -> MyShortcutsReorderHelpReadModeDialog.show(
                    activity, formatCreateShortcutHelpReadText(activity, targetOs)));
        }

        TextInputEditText advanced = root.findViewById(R.id.create_shortcut_advanced_input);
        TextView preview = root.findViewById(R.id.create_shortcut_preview);
        MaterialButton modCmd = root.findViewById(R.id.create_shortcut_mod_cmd);
        MaterialButton modCtrl = root.findViewById(R.id.create_shortcut_mod_ctrl);
        MaterialButton modShift = root.findViewById(R.id.create_shortcut_mod_shift);
        MaterialButton modAlt = root.findViewById(R.id.create_shortcut_mod_alt);
        applyModifierButtonLabelsForTargetOs(activity, targetOs, modCmd, modCtrl, modShift, modAlt);
        ChipGroup keyChips = root.findViewById(R.id.create_shortcut_key_chips);
        MaterialButton cancel = root.findViewById(R.id.create_shortcut_cancel);
        MaterialButton save = root.findViewById(R.id.create_shortcut_save);

        int letterLayout = R.layout.item_create_shortcut_key_chip;
        int specialLayout = R.layout.item_create_shortcut_key_chip_small;

        for (char c = 'A'; c <= 'Z'; c++) {
            String display = String.valueOf(c);
            // Lowercase token so KeyParser does not auto-add Shift for A–Z (needsShift on uppercase).
            String token = String.valueOf((char) ('a' + (c - 'A')));
            addKeyChip(activity, keyChips, letterLayout, display, token);
        }

        String[][] tokens = {
                {"Esc", "<ESC>"}, {"Back", "<BACK>"}, {"Enter", "<ENTER>"}, {"Space", "<SPACE>"},
                {"←", "<LEFT>"}, {"→", "<RIGHT>"}, {"↑", "<UP>"}, {"↓", "<DOWN>"},
                {"Home", "<HOME>"}, {"End", "<END>"}, {"Tab", "<TAB>"}, {"Del", "<DEL>"},
                {"F1", "<F1>"}, {"F2", "<F2>"}, {"F3", "<F3>"}, {"F4", "<F4>"},
                {"F5", "<F5>"}, {"F6", "<F6>"}, {"F7", "<F7>"}, {"F8", "<F8>"},
                {"F9", "<F9>"}, {"F10", "<F10>"}, {"F11", "<F11>"}, {"F12", "<F12>"}
        };
        for (String[] entry : tokens) {
            addKeyChip(activity, keyChips, specialLayout, entry[0], entry[1]);
        }

        Runnable updatePreview = () -> {
            String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
            String data;
            if (!adv.isEmpty()) {
                data = adv;
            } else {
                String token = selectedKeyToken(keyChips);
                if (token == null) {
                    preview.setText(activity.getString(R.string.create_shortcut_preview_empty));
                    save.setEnabled(false);
                    return;
                }
                data = buildMacroData(
                        readModifierMask(modCmd, modCtrl, modShift, modAlt),
                        token);
            }
            KeyParser.ParsedKey parsed = KeyParser.parse(data);
            if (parsed.keyCode < 0) {
                preview.setText(activity.getString(R.string.create_shortcut_preview_invalid));
                save.setEnabled(false);
            } else {
                String label = KeyParser.toLabelForTargetOs(parsed.keyCode, parsed.modifiers, targetOs);
                preview.setText(KeyParser.displayLabel(label, targetOs));
                save.setEnabled(true);
            }
        };

        keyChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
                if (!adv.isEmpty()) {
                    advanced.setText("");
                }
            }
            updatePreview.run();
        });

        View.OnClickListener modClick = v -> updatePreview.run();
        modCmd.setOnClickListener(modClick);
        modCtrl.setOnClickListener(modClick);
        modShift.setOnClickListener(modClick);
        modAlt.setOnClickListener(modClick);

        advanced.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.toString().trim().length() > 0) {
                    keyChips.clearCheck();
                }
                updatePreview.run();
            }
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        save.setOnClickListener(v -> {
            String adv = advanced.getText() != null ? advanced.getText().toString().trim() : "";
            String data;
            if (!adv.isEmpty()) {
                data = adv;
            } else {
                String token = selectedKeyToken(keyChips);
                if (token == null) {
                    Toast.makeText(activity, R.string.create_shortcut_need_key, Toast.LENGTH_SHORT).show();
                    return;
                }
                data = buildMacroData(
                        readModifierMask(modCmd, modCtrl, modShift, modAlt),
                        token);
            }
            KeyParser.ParsedKey parsed = KeyParser.parse(data);
            if (parsed.keyCode < 0) {
                Toast.makeText(activity, R.string.create_shortcut_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            if (profileManager.profileHasChord(profileId, parsed.keyCode, parsed.modifiers, targetOs)) {
                Toast.makeText(activity, R.string.create_shortcut_duplicate, Toast.LENGTH_LONG).show();
                return;
            }
            ShortcutProfileManager.Shortcut created = profileManager.addQuickShortcutToGeneralAndFavorites(
                    profileId, parsed.keyCode, parsed.modifiers, targetOs);
            if (created == null) {
                Toast.makeText(activity, R.string.create_shortcut_save_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(
                    activity,
                    activity.getString(R.string.create_shortcut_saved, created.label),
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        updatePreview.run();
        dialog.show();
    }
}
