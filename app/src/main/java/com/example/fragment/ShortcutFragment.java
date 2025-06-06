package com.example.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;

import com.example.dual_modekeyboard.R;

public class ShortcutFragment extends Fragment {

    public ShortcutFragment() {
        // Required empty public constructor
    }

    public static ShortcutFragment newInstance() {
        return new ShortcutFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create a simple View with no specific layout (blank)
        return inflater.inflate(R.layout.shortcut_image, container, false);
    }
}