package com.example.dual_modekeyboard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hidden navigation bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // Hidden status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); // Make the gesture return

        TouchPadView touchPad = findViewById(R.id.touchPad);
        EditText editText = findViewById(R.id.editText);
        CustomKeyboardView keyboardView = findViewById(R.id.keyboard_view);

        touchPad.setOnTouchPadListener(new TouchPadView.OnTouchPadListener() {
            @Override
            public void onTouchMove(float deltaX, float deltaY) {
                // deal mouse movement
                // versionCode
                Log.d("TouchPad", "Move: " + deltaX + ", " + deltaY);
            }

            @Override
            public void onTouchClick() {
                // deal mouse click
                Log.d("TouchPad", "Click");
            }

            @Override
            public void onTouchDoubleClick() {
                // deal mouse double click
                Log.d("TouchPad", "Double Click");
            }

            @Override
            public void onTouchRightClick() {
                // deal mouse right click
                Log.d("TouchPad", "Right Click");
            }
        });

        // Set the keyboard view
//        keyboardView.setEditText(new EditText(this));
        keyboardView.setEditText(editText);
        editText.requestFocus();
    }
}