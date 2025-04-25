package com.example.dual_modekeyboard;

import android.os.Bundle;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText editText = findViewById(R.id.editText);
        CustomKeyboardView keyboardView = findViewById(R.id.keyboard_view);
        keyboardView.setEditText(editText);
        editText.requestFocus();
    }
}