package com.example.javaamse;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // Constants
    private static final float COLLISION_THRESHOLD = 0.2f;
    private static final int COLLISION_CHECK_INTERVAL = 10; // milliseconds
    private static final int TIE_FIGHTER_MOVEMENT_INTERVAL = 10; // milliseconds
    private static final float TIE_FIGHTER_SPEED = 15.0f;
    private static final int MIN_ASTEROID_ANIMATION_DURATION = 4000;
    private static final int ADDITIONAL_ASTEROID_ANIMATION_DURATION = 3000;
    private static final int EXPLOSION_ANIMATION_DURATION = 500;

    // Physics constants
    private static final float MIN_ASTEROID_SPEED = 8.0f;
    private static final float MAX_ASTEROID_SPEED = 12.0f;
    private static final int PHYSICS_UPDATE_INTERVAL = 16; // ~60 FPS

    private final Random random = new Random();
    // Handlers and Runnables
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Screen dimensions
    private int screenWidth;
    private int screenHeight;
    // UI Elements
    private ImageView joystickPad;
    private ImageView tieFighterImageView;
    private ImageView joystickBase;
    private ConstraintLayout mainLayout;
    private View gameOverLayout;
    // Joystick properties
    private boolean joystickIsPressed = false;
    private float joystickCenterX;
    private float joystickCenterY;
    private float maxJoystickOffset;
    // Game state
    private boolean isGameActive = true;
    private Runnable movingTieRunnable;
    private Runnable collisionRunnable;

    // Asteroid array for easier management
    private ImageView[] asteroids;

    // Physics fields
    private AsteroidPhysics[] asteroidPhysics;
    private Runnable physicsRunnable;
    private int asteroidCount;
    private float asteroidSpeedFactor;
    private float tieSpeed;
    // Add these fields to the class
    private boolean isInvulnerable = true;
    private static final int INVULNERABILITY_DURATION = 2000; // 2 seconds

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
            // Reset visibility to ensure the ship is fully visible
            tieFighterImageView.setAlpha(1.0f);
        }, INVULNERABILITY_DURATION);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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

            // Set size (adjust as needed)
            int size = 100; // pixels, adjust as needed
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
        handler.removeCallbacks(movingTieRunnable);
        handler.removeCallbacks(collisionRunnable);
        handler.removeCallbacks(physicsRunnable);

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

        // Set up restart button
        Button restartButton = gameOverLayout.findViewById(R.id.restartButton);
        restartButton.setOnClickListener(v -> restartGame());
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
}