package com.openterface.keymod;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.openterface.keymod.util.MyShortcutsReorderHelpReadModeDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet: scrollable section tabs (Favorites + profile categories or All), drag-reorder list,
 * bookmark control on browse rows to stage copies in Favorites or remove them from the staged list; Done persists once.
 */
public final class MyShortcutsReorderBottomSheet {

    private MyShortcutsReorderBottomSheet() {
    }

    private static final class BrowsePage {
        final String title;
        final List<ShortcutProfileManager.Shortcut> shortcuts;

        BrowsePage(String title, List<ShortcutProfileManager.Shortcut> shortcuts) {
            this.title = title;
            this.shortcuts = shortcuts;
        }
    }

    private static List<BrowsePage> buildBrowsePages(
            AppCompatActivity activity,
            ShortcutProfileManager.ShortcutProfile ap
    ) {
        List<BrowsePage> out = new ArrayList<>();
        if (ap.categories != null && !ap.categories.isEmpty()) {
            for (ShortcutProfileManager.ShortcutCategory cat : ap.categories) {
                if (cat == null || cat.shortcuts == null || cat.shortcuts.isEmpty()) {
                    continue;
                }
                String label = cat.name != null && !cat.name.trim().isEmpty() ? cat.name.trim() : cat.id;
                out.add(new BrowsePage(label, new ArrayList<>(cat.shortcuts)));
            }
        } else {
            List<ShortcutProfileManager.Shortcut> flat = ap.getAllShortcutsFlat();
            if (flat != null && !flat.isEmpty()) {
                out.add(new BrowsePage(
                        activity.getString(R.string.my_shortcuts_tab_all),
                        new ArrayList<>(flat)));
            }
        }
        return out;
    }

    /** Removes first shortcut with matching id from the working list (staged My). */
    private static boolean removeShortcutFromWorkingListById(
            @NonNull List<ShortcutProfileManager.Shortcut> working,
            @Nullable String shortcutId
    ) {
        if (shortcutId == null || shortcutId.isEmpty()) {
            return false;
        }
        for (int i = working.size() - 1; i >= 0; i--) {
            ShortcutProfileManager.Shortcut s = working.get(i);
            if (s != null && shortcutId.equals(s.id)) {
                working.remove(i);
                return true;
            }
        }
        return false;
    }

    public static void show(
            @NonNull AppCompatActivity activity,
            @NonNull ShortcutProfileManager profileManager,
            @NonNull String profileId,
            @NonNull String targetOs,
            int scrollToIndex,
            @Nullable Runnable onSaved
    ) {
        ShortcutProfileManager.ShortcutProfile ap = profileManager.getProfileById(profileId);
        if (ap == null) {
            return;
        }
        List<ShortcutProfileManager.Shortcut> data = new ArrayList<>(
                profileManager.getOrderedShortcutsForTopStrip(profileId));
        List<BrowsePage> browsePages = buildBrowsePages(activity, ap);
        if (data.isEmpty() && browsePages.isEmpty()) {
            Toast.makeText(activity, R.string.my_shortcuts_sheet_empty_profile, Toast.LENGTH_SHORT).show();
            return;
        }

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_my_shortcuts_reorder, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        TextView emptyMyHint = root.findViewById(R.id.reorder_my_empty_hint);
        TabLayout tabLayout = root.findViewById(R.id.reorder_tab_layout);
        ImageButton infoButton = root.findViewById(R.id.reorder_sheet_info);
        String profileTitleRaw = ap.name != null ? ap.name.trim() : "";
        final String profileTitleForHelp = profileTitleRaw.isEmpty() ? "—" : profileTitleRaw;

        RecyclerView recycler = root.findViewById(R.id.reorder_recycler);
        int listHeightPx = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.52f);
        ViewGroup.LayoutParams lpRv = recycler.getLayoutParams();
        if (lpRv instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lpRv).height = listHeightPx;
            recycler.setLayoutParams(lpRv);
        } else {
            recycler.setMinimumHeight(listHeightPx);
        }
        recycler.setNestedScrollingEnabled(true);
        recycler.setLayoutManager(new LinearLayoutManager(activity));

        if (infoButton != null) {
            infoButton.setOnClickListener(v -> MyShortcutsReorderHelpReadModeDialog.show(
                    activity,
                    MyShortcutsReorderHelpReadModeDialog.formatHelpText(
                            activity, profileTitleForHelp, data.size())));
        }

        MyShortcutsReorderAdapter myAdapter = new MyShortcutsReorderAdapter(activity, targetOs, data);
        ShortcutSectionPickAdapter pickAdapter = new ShortcutSectionPickAdapter(activity, targetOs, new ArrayList<>());

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                myAdapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        myAdapter.setDragHelper(touchHelper);

        Runnable updateEmptyHint = () -> {
            int idx = tabLayout.getSelectedTabPosition();
            emptyMyHint.setVisibility(
                    idx == 0 && myAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        };

        Runnable selectMyTab = () -> {
            touchHelper.attachToRecyclerView(null);
            recycler.setAdapter(myAdapter);
            myAdapter.setDragHelper(touchHelper);
            touchHelper.attachToRecyclerView(recycler);
            myAdapter.notifyDataSetChanged();
            updateEmptyHint.run();
        };

        pickAdapter.setFavoriteMembershipChecker(s -> {
            if (s == null || s.id == null || s.id.isEmpty()) {
                return false;
            }
            for (ShortcutProfileManager.Shortcut x : data) {
                if (x != null && s.id.equals(x.id)) {
                    return true;
                }
            }
            return false;
        });

        pickAdapter.setBookmarkListener(new ShortcutSectionPickAdapter.OnBookmarkActionListener() {
            @Override
            public void onAddToFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                if (profileManager.appendCloneIfAbsent(data, shortcut)) {
                    myAdapter.notifyDataSetChanged();
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_added_to_my, Toast.LENGTH_SHORT).show();
                } else {
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_already_in_my, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRemoveFromFavorites(@NonNull ShortcutProfileManager.Shortcut shortcut) {
                if (removeShortcutFromWorkingListById(data, shortcut.id)) {
                    myAdapter.notifyDataSetChanged();
                    pickAdapter.notifyDataSetChanged();
                    Toast.makeText(activity, R.string.my_shortcuts_removed_from_my, Toast.LENGTH_SHORT).show();
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int idx = tab.getPosition();
                if (idx == 0) {
                    selectMyTab.run();
                } else {
                    touchHelper.attachToRecyclerView(null);
                    BrowsePage page = browsePages.get(idx - 1);
                    pickAdapter.setItems(page.shortcuts);
                    recycler.setAdapter(pickAdapter);
                    updateEmptyHint.run();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        tabLayout.addTab(tabLayout.newTab().setText(R.string.my_shortcuts_tab_favorites));
        for (BrowsePage page : browsePages) {
            tabLayout.addTab(tabLayout.newTab().setText(page.title));
        }

        selectMyTab.run();

        recycler.post(() -> {
            int n = data.size();
            if (n > 0 && scrollToIndex >= 0) {
                int pos = Math.min(scrollToIndex, n - 1);
                LinearLayoutManager lm = (LinearLayoutManager) recycler.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(pos, 0);
                }
            }
        });

        Button cancel = root.findViewById(R.id.reorder_cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button done = root.findViewById(R.id.reorder_done);
        done.setOnClickListener(v -> {
            profileManager.reorderMyShortcuts(profileId, new ArrayList<>(myAdapter.getItems()));
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
    }
}
