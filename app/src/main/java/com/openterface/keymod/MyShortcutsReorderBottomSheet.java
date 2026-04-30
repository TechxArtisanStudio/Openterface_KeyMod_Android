package com.openterface.keymod;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet: drag-reorder My shortcuts; Done persists strip order.
 */
public final class MyShortcutsReorderBottomSheet {

    private MyShortcutsReorderBottomSheet() {
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
        if (data.isEmpty()) {
            Toast.makeText(activity, R.string.top_strip_favorite_picker_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        View root = activity.getLayoutInflater().inflate(R.layout.bottomsheet_my_shortcuts_reorder, null, false);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(root);

        TextView subtitle = root.findViewById(R.id.reorder_sheet_subtitle);
        String profileTitle = ap.name != null ? ap.name.trim() : "";
        if (profileTitle.isEmpty()) {
            profileTitle = "—";
        }
        subtitle.setText(activity.getString(
                R.string.my_shortcuts_reorder_sheet_subtitle_with_profile,
                profileTitle,
                data.size()));

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
        MyShortcutsReorderAdapter adapter = new MyShortcutsReorderAdapter(activity, targetOs, data);
        recycler.setAdapter(adapter);

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
                adapter.moveItem(from, to);
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
        adapter.setDragHelper(touchHelper);
        touchHelper.attachToRecyclerView(recycler);

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
            profileManager.reorderMyShortcuts(profileId, new ArrayList<>(adapter.getItems()));
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
