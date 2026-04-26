package com.openterface.keymod;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * Central theme resolver for color family + light/dark mode behavior.
 */
public final class ThemeManager {

    public static final String PREF_THEME_COLOR_FAMILY = "theme_color_family";
    public static final String PREF_THEME_FOLLOW_SYSTEM = "theme_follow_system";
    public static final String PREF_THEME_MODE_OVERRIDE = "theme_mode_override";

    public static final String FAMILY_ORANGE = "orange";
    public static final String FAMILY_BLUE = "blue";
    public static final String FAMILY_GREEN = "green";
    public static final String FAMILY_PINK = "pink";
    public static final String FAMILY_PURPLE = "purple";
    public static final String FAMILY_RED = "red";
    public static final String FAMILY_TEAL = "teal";
    public static final String FAMILY_INDIGO = "indigo";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";

    private ThemeManager() {
    }

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean followSystem = prefs.getBoolean(PREF_THEME_FOLLOW_SYSTEM, false);
        String modeOverride = prefs.getString(PREF_THEME_MODE_OVERRIDE, MODE_DARK);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);

        int desiredNightMode;
        if (followSystem) {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if (MODE_LIGHT.equals(modeOverride)) {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else {
            desiredNightMode = AppCompatDelegate.MODE_NIGHT_YES;
        }

        if (AppCompatDelegate.getDefaultNightMode() != desiredNightMode) {
            AppCompatDelegate.setDefaultNightMode(desiredNightMode);
        }

        activity.setTheme(resolveTheme(family));
    }

    public static void savePreferences(Context context, String family, boolean followSystem, String modeOverride) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_THEME_COLOR_FAMILY, family)
                .putBoolean(PREF_THEME_FOLLOW_SYSTEM, followSystem)
                .putString(PREF_THEME_MODE_OVERRIDE, modeOverride)
                .apply();
    }

    public static int getSelectedThemeResId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String family = prefs.getString(PREF_THEME_COLOR_FAMILY, FAMILY_ORANGE);
        return resolveTheme(family);
    }

    private static int resolveTheme(String family) {
        if (FAMILY_BLUE.equals(family)) {
            return R.style.Theme_KeyMod_Blue;
        }
        if (FAMILY_GREEN.equals(family)) {
            return R.style.Theme_KeyMod_Green;
        }
        if (FAMILY_PINK.equals(family)) {
            return R.style.Theme_KeyMod_Pink;
        }
        if (FAMILY_PURPLE.equals(family)) {
            return R.style.Theme_KeyMod_Purple;
        }
        if (FAMILY_RED.equals(family)) {
            return R.style.Theme_KeyMod_Red;
        }
        if (FAMILY_TEAL.equals(family)) {
            return R.style.Theme_KeyMod_Teal;
        }
        if (FAMILY_INDIGO.equals(family)) {
            return R.style.Theme_KeyMod_Indigo;
        }
        return R.style.Theme_KeyMod_Orange;
    }
}
