package com.openterface.keymod;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.openterface.keymod.fragments.GeneralSettingsFragment;
import com.openterface.keymod.fragments.VoiceSettingsFragment;
import com.openterface.keymod.fragments.AISettingsFragment;
import com.openterface.keymod.fragments.HistoryFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings Activity - Centralized settings management
 * Matches iOS SettingsView.swift functionality
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private SettingsPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        // Keep Settings status bar neutral across theme families.
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.background_light));

        // Setup ActionBar with back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("Settings");
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        initializeViews();
        setupViewPager();
    }

    private void initializeViews() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
    }

    private void setupViewPager() {
        adapter = new SettingsPagerAdapter(getSupportFragmentManager());
        
        // Add tabs
        adapter.addFragment(new GeneralSettingsFragment(), "General");
        adapter.addFragment(new VoiceSettingsFragment(), "Voice Input");
        adapter.addFragment(new AISettingsFragment(), "AI Settings");
        adapter.addFragment(new HistoryFragment(), "History");

        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        // Set tab icons (optional)
        tabLayout.getTabAt(0).setIcon(R.drawable.ic_settings);
        tabLayout.getTabAt(1).setIcon(R.drawable.ic_voice);
        tabLayout.getTabAt(2).setIcon(R.drawable.ic_ai);
        tabLayout.getTabAt(3).setIcon(R.drawable.ic_history);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Settings are auto-saved by individual preference listeners.
        // If this activity is the root (e.g. process was recreated), route users back into app.
        if (isTaskRoot()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    /**
     * Pager Adapter for Settings Tabs
     */
    private static class SettingsPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> fragmentList = new ArrayList<>();
        private final List<String> fragmentTitleList = new ArrayList<>();

        public SettingsPagerAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitleList.get(position);
        }

        public void addFragment(Fragment fragment, String title) {
            fragmentList.add(fragment);
            fragmentTitleList.add(title);
        }
    }
}
