package com.example.javaamse;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LaunchActivity extends AppCompatActivity {

    private RadioGroup difficultyRadioGroup;
    private SeekBar tieSpeedSeekBar;
    private TextView tieSpeedValue;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_launch);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        tieSpeedSeekBar = findViewById(R.id.tieSpeedSeekBar);
        tieSpeedValue = findViewById(R.id.tieSpeedValue);
        startButton = findViewById(R.id.startButton);

        // Set up SeekBar listener
        tieSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tieSpeedValue.setText("Speed: " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up Start button listener
        startButton.setOnClickListener(v -> startGame());
    }

    private void startGame() {
        Intent intent = new Intent(this, MainActivity.class);

        // Get selected difficulty
        int selectedId = difficultyRadioGroup.getCheckedRadioButtonId();
        int asteroidCount;
        float asteroidSpeedFactor;

        if (selectedId == R.id.easyRadioButton) {
            asteroidCount = 3;
            asteroidSpeedFactor = 0.7f;
        } else if (selectedId == R.id.mediumRadioButton) {
            asteroidCount = 4;
            asteroidSpeedFactor = 1.0f;
        } else {
            asteroidCount = 5;
            asteroidSpeedFactor = 1.3f;
        }

        // Get TIE fighter speed
        float tieSpeed = tieSpeedSeekBar.getProgress();

        // Pass values to MainActivity
        intent.putExtra("ASTEROID_COUNT", asteroidCount);
        intent.putExtra("ASTEROID_SPEED_FACTOR", asteroidSpeedFactor);
        intent.putExtra("TIE_SPEED", tieSpeed);

        startActivity(intent);
    }
}