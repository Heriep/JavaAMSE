package com.example.javaamse;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // Game constants
    private static final float COLLISION_THRESHOLD = 0.2f;
    private static final int COLLISION_CHECK_INTERVAL = 10; // milliseconds
    private static final int TIE_FIGHTER_MOVEMENT_INTERVAL = 10; // milliseconds
    private static final int EXPLOSION_ANIMATION_DURATION = 500;
    private static final int INVULNERABILITY_DURATION = 2000; // 2 seconds
    private static final int SCORE_INCREMENT_INTERVAL = 1000; // 1 second

    // Physics constants
    private static final float MIN_ASTEROID_SPEED = 8.0f;
    private static final float MAX_ASTEROID_SPEED = 12.0f;
    private static final int PHYSICS_UPDATE_INTERVAL = 16; // ~60 FPS

    // Token constants
    private static final int MAX_POINT_TOKENS = 1;
    private static final int TOKEN_SPAWN_INTERVAL = 2000; // 2 seconds
    private static final int TOKEN_LIFETIME = 8000; // 8 seconds
    private static final int TOKEN_POINTS = 20;

    // Gyroscope constants
    private static final float GYROSCOPE_SENSITIVITY = 0.05f;

    // Screen properties
    private int screenWidth;
    private int screenHeight;

    // UI Elements
    private ConstraintLayout mainLayout;
    private ImageView joystickPad;
    private ImageView tieFighterImageView;
    private ImageView joystickBase;
    private View gameOverLayout;
    private TextView scoreTextView;

    // Joystick properties
    private boolean joystickIsPressed = false;
    private float joystickCenterX;
    private float joystickCenterY;
    private float maxJoystickOffset;

    // Game state
    private final Random random = new Random();
    private boolean isGameActive = true;
    private boolean isInvulnerable = true;
    private int currentScore = 0;
    private int bestScore = 0;

    // Asteroid properties
    private ImageView[] asteroids;
    private AsteroidPhysics[] asteroidPhysics;
    private int asteroidCount;
    private float asteroidSpeedFactor;
    private float tieSpeed;

    // Token properties
    private ArrayList<PointToken> pointTokens;

    // Sensor properties
    private boolean useGyroscope = false;
    private SensorManager sensorManager;
    private Sensor gyroscopeSensor;
    private SensorEventListener gyroscopeEventListener;
    private float[] gyroscopeValues = new float[3];

    // Handlers and Runnables
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Handler tokenHandler = new Handler(Looper.getMainLooper());
    private Runnable movingTieRunnable;
    private Runnable collisionRunnable;
    private Runnable physicsRunnable;
    private Runnable scoreRunnable;
    private Runnable tokenSpawnRunnable;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        setupWindowInsets();

        // Get parameters from LaunchActivity
        Intent intent = getIntent();
        asteroidCount = intent.getIntExtra("ASTEROID_COUNT", 4);
        asteroidSpeedFactor = intent.getFloatExtra("ASTEROID_SPEED_FACTOR", 1.0f);
        tieSpeed = intent.getFloatExtra("TIE_SPEED", 10.0f);
        useGyroscope = intent.getBooleanExtra("USE_GYROSCOPE", false);

        // Initialize sensors if gyroscope is enabled
        if (useGyroscope) {
            initializeGyroscope();
        }

        // Initialize point tokens system
        initializePointTokens();

        initializeScreenDimensions();
        initializeGameElements();
        setupJoystick();
        startCollisionDetection();
        startAsteroidPhysics();

        // Start invulnerability
        startInvulnerabilityEffect();

        // Schedule end of invulnerability
        handler.postDelayed(() -> {
            isInvulnerable = false;
            tieFighterImageView.setAlpha(1.0f);
        }, INVULNERABILITY_DURATION);
    }
    private void initializeGyroscope() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        if (gyroscopeSensor == null) {
            // Fall back to accelerometer if gyroscope isn't available
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (gyroscopeSensor != null) {
            gyroscopeEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (!isGameActive) return;

                    gyroscopeValues[0] = event.values[0]; // X axis (tilt left/right)
                    gyroscopeValues[1] = event.values[1]; // Y axis (tilt forward/backward)

                    if (useGyroscope && !joystickIsPressed) {
                        moveShipWithGyroscope();
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    // Not needed for this implementation
                }
            };
        } else {
            // No sensors available, disable gyroscope control
            useGyroscope = false;
        }
    }

    private void moveShipWithGyroscope() {
        // Y-axis controls left-right movement (negative is right tilt)
        float tiltX = -gyroscopeValues[0];
        // X-axis controls up-down movement (positive is forward tilt)
        float tiltY = gyroscopeValues[1];

        // Apply sensitivity and calculate new position
        float tieX = tieFighterImageView.getX() + tiltX * tieSpeed * 0.1f * GYROSCOPE_SENSITIVITY;
        float tieY = tieFighterImageView.getY() + tiltY * tieSpeed * 0.1f * GYROSCOPE_SENSITIVITY;

        // Keep within screen bounds
        tieX = Math.max(0, Math.min(tieX, screenWidth - tieFighterImageView.getWidth()));
        tieY = Math.max(0, Math.min(tieY, screenHeight - tieFighterImageView.getHeight()));

        tieFighterImageView.setX(tieX);
        tieFighterImageView.setY(tieY);

        // Continue updating if game is active
        if (isGameActive && useGyroscope) {
            handler.postDelayed(() -> moveShipWithGyroscope(), TIE_FIGHTER_MOVEMENT_INTERVAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (useGyroscope && gyroscopeSensor != null && gyroscopeEventListener != null) {
            sensorManager.registerListener(gyroscopeEventListener, gyroscopeSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (useGyroscope && sensorManager != null && gyroscopeEventListener != null) {
            sensorManager.unregisterListener(gyroscopeEventListener);
        }
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void initializePointTokens() {
        pointTokens = new ArrayList<>();

        // Start spawning tokens
        tokenSpawnRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isGameActive) return;

                // Only spawn if we're below the maximum
                if (pointTokens.size() < MAX_POINT_TOKENS) {
                    spawnPointToken();
                }

                // Schedule next spawn
                if (isGameActive) {
                    tokenHandler.postDelayed(this, TOKEN_SPAWN_INTERVAL);
                }
            }
        };

        tokenHandler.postDelayed(tokenSpawnRunnable, TOKEN_SPAWN_INTERVAL);
    }
    private void spawnPointToken() {
        // Create token ImageView
        ImageView tokenView = new ImageView(this);
        tokenView.setImageResource(R.drawable.point_token); // You'll need to add this drawable

        // Set size (50dp x 50dp)
        int size = dpToPx(50);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(size, size);
        tokenView.setLayoutParams(params);

        // Find a safe position (away from ship and asteroids)
        float tokenX, tokenY;
        boolean validPosition;
        int attempts = 0;

        do {
            tokenX = random.nextFloat() * (screenWidth - size);
            tokenY = random.nextFloat() * (screenHeight - size);
            validPosition = true;

            // Check distance from TIE fighter
            float distToShip = calculateDistance(
                    tokenX + size/2, tokenY + size/2,
                    tieFighterImageView.getX() + tieFighterImageView.getWidth()/2,
                    tieFighterImageView.getY() + tieFighterImageView.getHeight()/2
            );

            if (distToShip < 200) {
                validPosition = false;
            }

            // Check distance from all asteroids
            if (validPosition) {
                for (ImageView asteroid : asteroids) {
                    float distToAsteroid = calculateDistance(
                            tokenX + size/2, tokenY + size/2,
                            asteroid.getX() + asteroid.getWidth()/2,
                            asteroid.getY() + asteroid.getHeight()/2
                    );

                    if (distToAsteroid < 150) {
                        validPosition = false;
                        break;
                    }
                }
            }

            attempts++;
        } while (!validPosition && attempts < 50);

        // If we couldn't find a good spot after many attempts, just pick one
        if (!validPosition) {
            tokenX = random.nextFloat() * (screenWidth - size);
            tokenY = random.nextFloat() * (screenHeight - size);
        }

        // Set position
        tokenView.setX(tokenX);
        tokenView.setY(tokenY);

        // Add to layout
        mainLayout.addView(tokenView);

        // Create token object
        PointToken token = new PointToken(tokenView, TOKEN_POINTS);
        pointTokens.add(token);

        // Create pulsating animation
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tokenView, "scaleX", 0.8f, 1.2f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tokenView, "scaleY", 0.8f, 1.2f);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setDuration(800);
        scaleY.setDuration(800);
        scaleX.start();
        scaleY.start();

        // Auto-remove after lifetime
        tokenHandler.postDelayed(() -> {
            if (isGameActive && token.isActive) {
                removePointToken(token);
            }
        }, TOKEN_LIFETIME);
    }
    private void checkTokenCollisions() {
        if (!isGameActive) return;

        Iterator<PointToken> iterator = pointTokens.iterator();
        while (iterator.hasNext()) {
            PointToken token = iterator.next();
            if (token.isActive && isCollision(tieFighterImageView, token.imageView, COLLISION_THRESHOLD)) {
                collectToken(token);
            }
        }
    }

    private void collectToken(PointToken token) {
        // Update score
        currentScore += token.value;
        scoreTextView.setText("Score: " + currentScore);

        // Show collection animation
        showTokenCollectEffect(token);

        // Remove token
        token.isActive = false;
        removePointToken(token);
    }

    private void removePointToken(PointToken token) {
        mainLayout.removeView(token.imageView);
        pointTokens.remove(token);
    }

    private void showTokenCollectEffect(PointToken token) {
        // Create score popup text
        TextView scorePopup = new TextView(this);
        scorePopup.setText("+" + token.value);
        scorePopup.setTextColor(Color.YELLOW);
        scorePopup.setTextSize(20);

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        scorePopup.setLayoutParams(params);

        // Position at token location
        scorePopup.setX(token.imageView.getX());
        scorePopup.setY(token.imageView.getY());

        mainLayout.addView(scorePopup);

        // Animate floating up and fading
        ObjectAnimator moveUp = ObjectAnimator.ofFloat(scorePopup, "translationY",
                scorePopup.getTranslationY(), scorePopup.getTranslationY() - 100);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(scorePopup, "alpha", 1f, 0f);

        moveUp.setDuration(700);
        fadeOut.setDuration(700);

        moveUp.start();
        fadeOut.start();

        // Remove after animation
        handler.postDelayed(() -> mainLayout.removeView(scorePopup), 700);
    }

    private float calculateDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void initializeScreenDimensions() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
    }

    private void initializeGameElements() {
        mainLayout = findViewById(R.id.main);

        // Initialize game elements
        joystickPad = findViewById(R.id.Pad_center);
        tieFighterImageView = findViewById(R.id.Tie);
        joystickBase = findViewById(R.id.Pad_exterior);

        // Create asteroids programmatically
        asteroids = new ImageView[asteroidCount];
        for (int i = 0; i < asteroidCount; i++) {
            ImageView asteroid = new ImageView(this);
            int[] asteroidDrawables = {R.drawable.asteroid1, R.drawable.asteroid2, R.drawable.asteroid3, R.drawable.asteroid4};
            asteroid.setImageResource(asteroidDrawables[random.nextInt(asteroidDrawables.length)]);

            // Set size (adjust as needed)can you clean the code ?
            int size = 120; // pixels, adjust as needed
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(size, size);
            asteroid.setLayoutParams(params);

            // Add to layout
            mainLayout.addView(asteroid);
            asteroids[i] = asteroid;
        }

        // Set initial position of the Tie Fighter
        tieFighterImageView.setX(0);
        tieFighterImageView.setY(-screenHeight / 3f);

        // Adjust TIE fighter speed based on seekbar value
        final float tieSpeedAdjustment = tieSpeed / 10.0f; // Normalize to 1.0 at center value

        // Calculate joystick center
        joystickBase.post(() -> {
            joystickCenterX = joystickBase.getX() + joystickBase.getWidth() / 2f;
            joystickCenterY = joystickBase.getY() + joystickBase.getHeight() / 2f;
            maxJoystickOffset = joystickBase.getWidth() / 2f;
        });

        // Create score display
        scoreTextView = new TextView(this);
        scoreTextView.setTextColor(Color.WHITE);
        scoreTextView.setTextSize(24);
        scoreTextView.setText("Score: 0");

        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.setMargins(40, 40, 0, 0);
        scoreTextView.setLayoutParams(params);

        mainLayout.addView(scoreTextView);

        // Load best score
        loadBestScore();

        // Start score timer
        startScoreTimer();
    }

    private void startScoreTimer() {
        scoreRunnable = new Runnable() {
            @Override
            public void run() {
                if (isGameActive) {
                    currentScore += 10;
                    scoreTextView.setText("Score: " + currentScore);
                    handler.postDelayed(this, SCORE_INCREMENT_INTERVAL);
                }
            }
        };
        handler.post(scoreRunnable);
    }

    private void saveBestScore() {
        if (currentScore > bestScore) {
            bestScore = currentScore;

            // Get the difficulty from the intent
            int asteroidCount = getIntent().getIntExtra("ASTEROID_COUNT", 4);
            String difficultyKey;

            // Determine difficulty key based on asteroid count
            if (asteroidCount == 3) {
                difficultyKey = "BestScoreEasy";
            } else if (asteroidCount == 4) {
                difficultyKey = "BestScoreNormal";
            } else {
                difficultyKey = "BestScoreHard";
            }

            SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(difficultyKey, bestScore);
            editor.putInt("BestScore", bestScore); // Keep this for backward compatibility
            editor.apply();
        }
    }

    private void loadBestScore() {
        SharedPreferences prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE);

        // Get the difficulty from the intent
        int asteroidCount = getIntent().getIntExtra("ASTEROID_COUNT", 4);
        String difficultyKey;

        // Determine difficulty key based on asteroid count
        if (asteroidCount == 3) {
            difficultyKey = "BestScoreEasy";
        } else if (asteroidCount == 4) {
            difficultyKey = "BestScoreNormal";
        } else {
            difficultyKey = "BestScoreHard";
        }

        bestScore = prefs.getInt(difficultyKey, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupJoystick() {
        // Initialize the movement runnable
        movingTieRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isGameActive) return;

                // Calculate movement based on joystick position
                float offsetX = joystickPad.getX() + joystickPad.getWidth() / 2f - joystickCenterX;
                float offsetY = joystickPad.getY() + joystickPad.getHeight() / 2f - joystickCenterY;

                // Normalize offset
                float normalizedOffsetX = offsetX / maxJoystickOffset;
                float normalizedOffsetY = offsetY / maxJoystickOffset;

                // Calculate new position using tieSpeed from intent
                float tieX = tieFighterImageView.getX() + normalizedOffsetX * tieSpeed;
                float tieY = tieFighterImageView.getY() + normalizedOffsetY * tieSpeed;

                // Keep within screen bounds
                tieX = Math.max(0, Math.min(tieX, screenWidth - tieFighterImageView.getWidth()));
                tieY = Math.max(0, Math.min(tieY, screenHeight - tieFighterImageView.getHeight()));

                tieFighterImageView.setX(tieX);
                tieFighterImageView.setY(tieY);

                if (joystickIsPressed && isGameActive) {
                    handler.postDelayed(this, TIE_FIGHTER_MOVEMENT_INTERVAL);
                }
            }
        };

        // Set up joystick touch listener
        joystickPad.setOnTouchListener((v, event) -> {
            if (!isGameActive) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    joystickIsPressed = true;
                    handler.post(movingTieRunnable);
                    moveJoystickPad(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    moveJoystickPad(event);
                    break;
                case MotionEvent.ACTION_UP:
                    joystickIsPressed = false;
                    // Reset joystick position
                    joystickPad.setX(joystickCenterX - joystickPad.getWidth() / 2f);
                    joystickPad.setY(joystickCenterY - joystickPad.getHeight() / 2f);
                    // Resume gyroscope control if enabled
                    if (useGyroscope) {
                        handler.post(() -> moveShipWithGyroscope());
                    }
                    break;
                default:
                    return false;
            }
            return true;
        });
    }

    private void startCollisionDetection() {
        collisionRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isGameActive) return;

                // Only check collisions if not invulnerable
                if (!isInvulnerable) {
                    // Check collisions with all asteroids
                    for (ImageView asteroid : asteroids) {
                        if (isCollision(tieFighterImageView, asteroid, COLLISION_THRESHOLD)) {
                            handleCollision();
                            return;
                        }
                    }
                }

                // Continue checking if game is active
                if (isGameActive) {
                    handler.postDelayed(this, COLLISION_CHECK_INTERVAL);
                }
            }
        };
        handler.post(collisionRunnable);
    }
    private void startInvulnerabilityEffect() {
        final int blinkInterval = 200; // milliseconds
        final Runnable blinkRunnable = new Runnable() {
            boolean visible = true;
            @Override
            public void run() {
                if (!isGameActive) return;

                if (isInvulnerable) {
                    visible = !visible;
                    tieFighterImageView.setAlpha(visible ? 1.0f : 0.3f);
                    handler.postDelayed(this, blinkInterval);
                } else {
                    // Ensure visibility when invulnerability ends
                    tieFighterImageView.setAlpha(1.0f);
                }
            }
        };

        handler.post(blinkRunnable);
    }

    private void moveJoystickPad(MotionEvent event) {
        float touchX = event.getRawX();
        float touchY = event.getRawY();

        // Calculate distance from joystick center
        float distance = (float) Math.sqrt(Math.pow(touchX - joystickCenterX, 2) + Math.pow(touchY - joystickCenterY, 2));
        float angle = (float) Math.atan2(touchY - joystickCenterY, touchX - joystickCenterX);

        // Clamp to joystick boundaries
        if (distance > maxJoystickOffset) {
            touchX = joystickCenterX + maxJoystickOffset * (float) Math.cos(angle);
            touchY = joystickCenterY + maxJoystickOffset * (float) Math.sin(angle);
        }

        // Update joystick position
        joystickPad.setX(touchX - joystickPad.getWidth() / 2f);
        joystickPad.setY(touchY - joystickPad.getHeight() / 2f);
    }

    // New physics-based asteroid methods

    private void initializeAsteroidPhysics() {
        // Get the initial TIE fighter position
        float tieFighterX = tieFighterImageView.getX();
        float tieFighterY = tieFighterImageView.getY();
        float safeZoneRadius = 200.0f; // Size of safe zone around player's spawn

        asteroidPhysics = new AsteroidPhysics[asteroids.length];
        for (int i = 0; i < asteroids.length; i++) {
            float radius = asteroids[i].getWidth() / 2f;
            float randomVelocityX = getRandomVelocity() * asteroidSpeedFactor;
            float randomVelocityY = getRandomVelocity() * asteroidSpeedFactor;
            asteroidPhysics[i] = new AsteroidPhysics(randomVelocityX, randomVelocityY, radius);
        }

        // Set initial positions - distribute randomly around the screen
        for (int i = 0; i < asteroids.length; i++) {
            float x, y;
            boolean validPosition;
            int attempts = 0;
            do {
                x = random.nextFloat() * (screenWidth - 2 * asteroids[i].getWidth()) + asteroids[i].getWidth();
                y = random.nextFloat() * (screenHeight - 2 * asteroids[i].getHeight()) + asteroids[i].getHeight();
                validPosition = true;

                // Ensure asteroids do not spawn too close to each other
                for (int j = 0; j < i; j++) {
                    float otherX = asteroids[j].getX();
                    float otherY = asteroids[j].getY();
                    float distance = (float) Math.sqrt(Math.pow(x - otherX, 2) + Math.pow(y - otherY, 2));
                    if (distance < asteroids[i].getWidth() * 2) {
                        validPosition = false;
                        break;
                    }
                }

                // Ensure asteroids do not spawn too close to the TIE fighter
                float distanceToTie = (float) Math.sqrt(Math.pow(x - tieFighterX, 2) +
                        Math.pow(y - tieFighterY, 2));
                if (distanceToTie < safeZoneRadius) {
                    validPosition = false;
                }

                attempts++;
                // Prevent infinite loop
                if (attempts > 100) {
                    // If we can't find a suitable position after many attempts,
                    // just place it somewhere away from the TIE fighter
                    x = (tieFighterX < screenWidth/2) ? screenWidth - 200 : 200;
                    y = (tieFighterY < screenHeight/2) ? screenHeight - 200 : 200;
                    validPosition = true;
                }
            } while (!validPosition);

            asteroids[i].setX(x);
            asteroids[i].setY(y);
        }
    }

    private float getRandomVelocity() {
        float velocity = random.nextFloat() * (MAX_ASTEROID_SPEED - MIN_ASTEROID_SPEED) + MIN_ASTEROID_SPEED;
        return random.nextBoolean() ? velocity : -velocity;
    }

    private void startAsteroidPhysics() {
        // Initialize physics properties
        initializeAsteroidPhysics();

        physicsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isGameActive) return;

                updateAsteroidPositions();
                checkAsteroidBoundaryCollisions();
                checkAsteroidCollisions();
                checkTokenCollisions(); // Add this line

                if (isGameActive) {
                    handler.postDelayed(this, PHYSICS_UPDATE_INTERVAL);
                }
            }
        };
        handler.post(physicsRunnable);
    }

    private void updateAsteroidPositions() {
        for (int i = 0; i < asteroids.length; i++) {
            ImageView asteroid = asteroids[i];
            AsteroidPhysics physics = asteroidPhysics[i];

            // Update position
            float newX = asteroid.getX() + physics.velocityX;
            float newY = asteroid.getY() + physics.velocityY;
            asteroid.setX(newX);
            asteroid.setY(newY);

            // Update rotation
            physics.rotationAngle += physics.rotationSpeed;
            if (physics.rotationAngle > 360) {
                physics.rotationAngle -= 360;
            } else if (physics.rotationAngle < 0) {
                physics.rotationAngle += 360;
            }
            asteroid.setRotation(physics.rotationAngle);
        }
    }

    private void checkAsteroidBoundaryCollisions() {
        for (int i = 0; i < asteroids.length; i++) {
            ImageView asteroid = asteroids[i];
            AsteroidPhysics physics = asteroidPhysics[i];

            float left = asteroid.getX();
            float right = left + asteroid.getWidth();
            float top = asteroid.getY();
            float bottom = top + asteroid.getHeight();

            // Left or right wall collision
            if (left <= 0 || right >= screenWidth) {
                physics.velocityX = -physics.velocityX; // Reverse X velocity

                // Adjust position to prevent sticking at the boundary
                if (left <= 0) {
                    asteroid.setX(0);
                } else if (right >= screenWidth) {
                    asteroid.setX(screenWidth - asteroid.getWidth());
                }
            }

            // Top or bottom wall collision
            if (top <= 0 || bottom >= screenHeight) {
                physics.velocityY = -physics.velocityY; // Reverse Y velocity

                // Adjust position to prevent sticking at the boundary
                if (top <= 0) {
                    asteroid.setY(0);
                } else if (bottom >= screenHeight) {
                    asteroid.setY(screenHeight - asteroid.getHeight());
                }
            }
        }
    }

    private void checkAsteroidCollisions() {
        for (int i = 0; i < asteroids.length - 1; i++) {
            for (int j = i + 1; j < asteroids.length; j++) {
                ImageView asteroid1 = asteroids[i];
                ImageView asteroid2 = asteroids[j];
                AsteroidPhysics physics1 = asteroidPhysics[i];
                AsteroidPhysics physics2 = asteroidPhysics[j];

                // Use the same collision detection as the TIE fighter
                if (isCollision(asteroid1, asteroid2, COLLISION_THRESHOLD)) {
                    // Exchange velocities (simplified physics)
                    float tempVelocityX = physics1.velocityX;
                    float tempVelocityY = physics1.velocityY;
                    physics1.velocityX = physics2.velocityX;
                    physics1.velocityY = physics2.velocityY;
                    physics2.velocityX = tempVelocityX;
                    physics2.velocityY = tempVelocityY;

                    // Add a bit of randomness to prevent repetitive patterns
                    physics1.velocityX *= 0.95f + random.nextFloat() * 0.1f;
                    physics1.velocityY *= 0.95f + random.nextFloat() * 0.1f;
                    physics2.velocityX *= 0.95f + random.nextFloat() * 0.1f;
                    physics2.velocityY *= 0.95f + random.nextFloat() * 0.1f;

                    // Separate asteroids to prevent sticking
                    float separationX = (asteroid2.getX() > asteroid1.getX()) ? 2.0f : -2.0f;
                    float separationY = (asteroid2.getY() > asteroid1.getY()) ? 2.0f : -2.0f;

                    asteroid1.setX(asteroid1.getX() - separationX);
                    asteroid1.setY(asteroid1.getY() - separationY);
                    asteroid2.setX(asteroid2.getX() + separationX);
                    asteroid2.setY(asteroid2.getY() + separationY);
                }
            }
        }
    }

    // Keep existing methods

    private boolean isCollision(ImageView object1, ImageView object2, float collisionThresholdPercentage) {
        // Get center points
        float obj1CenterX = object1.getX() + object1.getWidth() / 2f;
        float obj1CenterY = object1.getY() + object1.getHeight() / 2f;
        float obj2CenterX = object2.getX() + object2.getWidth() / 2f;
        float obj2CenterY = object2.getY() + object2.getHeight() / 2f;

        // Calculate distance between centers
        float deltaX = obj1CenterX - obj2CenterX;
        float deltaY = obj1CenterY - obj2CenterY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        // Calculate collision radius (average of width and height divided by 2)
        float obj1Radius = (object1.getWidth() + object1.getHeight()) / 4f;
        float obj2Radius = (object2.getWidth() + object2.getHeight()) / 4f;

        // Apply threshold adjustment
        float collisionDistance = (obj1Radius + obj2Radius) * (1 - collisionThresholdPercentage * 0.5f);

        // Check if distance is less than the collision distance
        return distance < collisionDistance;
    }

    private Rect getImageViewRect(ImageView imageView) {
        int[] location = new int[2];
        imageView.getLocationOnScreen(location);
        return new Rect(location[0], location[1],
                location[0] + imageView.getWidth(),
                location[1] + imageView.getHeight());
    }

    private void handleCollision() {
        if (!isGameActive) return;

        // End the game
        isGameActive = false;
        joystickIsPressed = false;

        // Clean up handlers and save score
        handler.removeCallbacks(scoreRunnable);
        saveBestScore();
        handler.removeCallbacks(movingTieRunnable);
        handler.removeCallbacks(collisionRunnable);
        handler.removeCallbacks(physicsRunnable);

        // Clean up token handler
        tokenHandler.removeCallbacks(tokenSpawnRunnable);

        // Display explosion
        displayExplosion(tieFighterImageView);

        // Show game over screen after explosion
        handler.postDelayed(this::showGameOverScreen, EXPLOSION_ANIMATION_DURATION);
    }

    private void displayExplosion(ImageView tieFighter) {
        // Create explosion ImageView
        ImageView explosionImageView = new ImageView(this);
        explosionImageView.setImageResource(R.drawable.explosion);
        explosionImageView.setLayoutParams(new ViewGroup.LayoutParams(tieFighter.getWidth(), tieFighter.getHeight()));

        // Add explosion to layout
        mainLayout.addView(explosionImageView);

        // Position explosion
        int[] tieLocation = new int[2];
        tieFighter.getLocationOnScreen(tieLocation);
        int tieCenterX = tieLocation[0];
        int tieCenterY = tieLocation[1] - tieFighter.getHeight() / 2;

        explosionImageView.setX(tieCenterX);
        explosionImageView.setY(tieCenterY);

        // Fade out animation
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(explosionImageView, "alpha", 1f, 0f);
        fadeOut.setDuration(EXPLOSION_ANIMATION_DURATION);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mainLayout.removeView(explosionImageView);
            }
        });
        fadeOut.start();
    }

    private void showGameOverScreen() {
        // Inflate the game over layout
        LayoutInflater inflater = LayoutInflater.from(this);
        gameOverLayout = inflater.inflate(R.layout.game_over_layout, mainLayout, false);
        mainLayout.addView(gameOverLayout);

        // Set scores
        TextView finalScoreText = gameOverLayout.findViewById(R.id.finalScoreText);
        TextView bestScoreText = gameOverLayout.findViewById(R.id.bestScoreText);

        if (finalScoreText != null) {
            finalScoreText.setText("Score: " + currentScore);
        }

        if (bestScoreText != null) {
            bestScoreText.setText("Meilleur: " + bestScore);
        }

        // Set up restart button
        Button restartButton = gameOverLayout.findViewById(R.id.restartButton);
        restartButton.setOnClickListener(v -> restartGame());

        // Set up back to menu button
        Button menuButton = gameOverLayout.findViewById(R.id.menuButton);
        menuButton.setOnClickListener(v -> goToMainMenu());
    }

    private void goToMainMenu() {
        // Create intent for LaunchActivity
        Intent intent = new Intent(this, LaunchActivity.class);
        startActivity(intent);
        finish(); // Close current activity
    }

    private void restartGame() {
        // Restart activity properly
        Intent intent = getIntent();
        finish();
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up handlers
        handler.removeCallbacks(movingTieRunnable);
        handler.removeCallbacks(collisionRunnable);
        handler.removeCallbacks(physicsRunnable);
        handler.removeCallbacksAndMessages(null);
    }

    // Inner class for asteroid physics
    private static class AsteroidPhysics {
        public float velocityX;
        public float velocityY;
        public float radius;
        public float rotationAngle;
        public float rotationSpeed;

        public AsteroidPhysics(float velocityX, float velocityY, float radius) {
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.radius = radius;
            this.rotationAngle = 0f;
            this.rotationSpeed = (float) (Math.random() * 6.0 - 3.0); // Random rotation between -3 and 3 degrees per frame
        }
    }

    private static class PointToken {
        public ImageView imageView;
        public int value;
        public boolean isActive;

        public PointToken(ImageView imageView, int value) {
            this.imageView = imageView;
            this.value = value;
            this.isActive = true;
        }
    }
}