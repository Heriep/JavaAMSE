package com.example.javaamse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LaunchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // Load best scores for each difficulty
        SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
        int bestScoreEasy = prefs.getInt("BestScoreEasy", 0);
        int bestScoreNormal = prefs.getInt("BestScoreNormal", 0);
        int bestScoreHard = prefs.getInt("BestScoreHard", 0);

        // Update radio button texts to include best scores
        RadioButton easyButton = findViewById(R.id.easyRadioButton);
        RadioButton mediumButton = findViewById(R.id.mediumRadioButton);
        RadioButton hardButton = findViewById(R.id.hardRadioButton);

        easyButton.setText(String.format("Facile (3 asteroïdes à basse vitesse) - Meilleur: %d", bestScoreEasy));
        mediumButton.setText(String.format("Normal (4 asteroïdes à vitesse normale) - Meilleur: %d", bestScoreNormal));
        hardButton.setText(String.format("Difficile (5 asteroïdes à haute vitesse) - Meilleur: %d", bestScoreHard));

        // Set up SeekBar for TIE speed
        SeekBar tieSpeedSeekBar = findViewById(R.id.tieSpeedSeekBar);
        TextView tieSpeedValue = findViewById(R.id.tieSpeedValue);

        tieSpeedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tieSpeedValue.setText("Vitesse : " + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up start button
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> startGame());
    }

    private void startGame() {
        Intent intent = new Intent(this, MainActivity.class);

        // Set difficulty parameters based on selected radio button
        RadioGroup difficultyRadioGroup = findViewById(R.id.difficultyRadioGroup);
        int selectedId = difficultyRadioGroup.getCheckedRadioButtonId();

        int asteroidCount;
        float speedFactor;

        if (selectedId == R.id.easyRadioButton) {
            asteroidCount = 3;
            speedFactor = 0.7f;
        } else if (selectedId == R.id.hardRadioButton) {
            asteroidCount = 5;
            speedFactor = 1.3f;
        } else {
            asteroidCount = 4;
            speedFactor = 1.0f;
        }

        // Get TIE speed from SeekBar
        SeekBar tieSpeedSeekBar = findViewById(R.id.tieSpeedSeekBar);
        float tieSpeed = tieSpeedSeekBar.getProgress();

        // Get gyroscope preference
        CheckBox gyroscopeCheckBox = findViewById(R.id.gyroscopeCheckBox);
        boolean useGyroscope = gyroscopeCheckBox.isChecked();

        // Pass parameters to MainActivity
        intent.putExtra("ASTEROID_COUNT", asteroidCount);
        intent.putExtra("ASTEROID_SPEED_FACTOR", speedFactor);
        intent.putExtra("TIE_SPEED", tieSpeed);
        intent.putExtra("USE_GYROSCOPE", useGyroscope);

        startActivity(intent);
    }
}