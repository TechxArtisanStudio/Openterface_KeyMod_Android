package com.example.dual_modekeyboard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private EditText editText;
    private TouchPadView touchPad;
    private CustomKeyboardView keyboardView;
    private Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUIComponents(R.layout.activity_main);
    }

    private void initializeUIComponents(int layoutResId) {
        // Fullscreen and Immersive mode setup
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Initialize UI components
        editText = findViewById(R.id.editText);
        keyboardView = findViewById(R.id.keyboard_view);
        spinner = findViewById(R.id.planets_spinner);

        // Initialize TouchPad only if it exists in the layout
        touchPad = findViewById(R.id.touchPad);

        // Setup TouchPad if present
        if (touchPad != null) {
            touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
                @Override
                public void onTouchMove(float deltaX, float deltaY) {
                    Log.d("TouchPad", "Move: " + deltaX + ", " + deltaY);
                }

                @Override
                public void onTouchClick() {
                    Log.d("TouchPad", "Click");
                }

                @Override
                public void onTouchDoubleClick() {
                    Log.d("TouchPad", "Double Click");
                }

                @Override
                public void onTouchRightClick() {
                    Log.d("TouchPad", "Right Click");
                }
            });
        }

        // Setup keyboard and EditText if present
        if (keyboardView != null && editText != null) {
            keyboardView.setEditText(editText);
            editText.requestFocus();
        }

        // Setup Spinner
        if (spinner != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this,
                    R.array.planets_array,
                    android.R.layout.simple_spinner_item
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(this);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String selectedPlanet = parent.getItemAtPosition(position).toString();
        Toast.makeText(this, "Selected: " + selectedPlanet, Toast.LENGTH_SHORT).show();
        Log.d("Spinner", "Selected planet: " + selectedPlanet);

        if (selectedPlanet.equals("Touchpad + Keyboard")) {
            // Already on activity_main, no need to change
            if (getContentViewLayoutId() != R.layout.activity_main) {
                setContentView(R.layout.activity_main);
                initializeUIComponents(R.layout.activity_main);
                spinner.setSelection(position); // Restore spinner selection
            }
        } else if (selectedPlanet.equals("Full KeyBoard")) {
            // Switch to layout_keyboard_only
            if (getContentViewLayoutId() != R.layout.layout_keyboard_only) {
                setContentView(R.layout.layout_keyboard_only);
                initializeUIComponents(R.layout.layout_keyboard_only);
                spinner.setSelection(position); // Restore spinner selection
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Log.d("Spinner", "Nothing selected");
    }

    // Helper method to get current layout ID (not directly available, so we assume based on logic)
    private int getContentViewLayoutId() {
        // Since Android doesn't provide a direct way to get the current layout ID,
        // we assume the layout based on the presence of touchPad (unique to activity_main)
        return (touchPad != null) ? R.layout.activity_main : R.layout.layout_keyboard_only;
    }
}