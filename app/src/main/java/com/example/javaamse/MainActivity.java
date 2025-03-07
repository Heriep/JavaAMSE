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
    private static final float COLLISION_THRESHOLD = 0.3f;
    private static final int COLLISION_CHECK_INTERVAL = 10; // milliseconds
    private static final int TIE_FIGHTER_MOVEMENT_INTERVAL = 10; // milliseconds
    private static final float TIE_FIGHTER_SPEED = 10.0f;
    private static final int MIN_ASTEROID_ANIMATION_DURATION = 4000;
    private static final int ADDITIONAL_ASTEROID_ANIMATION_DURATION = 3000;
    private static final int EXPLOSION_ANIMATION_DURATION = 500;
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        setupWindowInsets();
        initializeScreenDimensions();
        initializeGameElements();
        setupJoystick();
        startCollisionDetection();
        startAsteroidAnimations();
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

        // Initialize asteroid array
        asteroids = new ImageView[4];
        asteroids[0] = findViewById(R.id.Asteroid1);
        asteroids[1] = findViewById(R.id.Asteroid2);
        asteroids[2] = findViewById(R.id.Asteroid3);
        asteroids[3] = findViewById(R.id.Asteroid4);

        // Set initial position of the Tie Fighter
        tieFighterImageView.setX(0);
        tieFighterImageView.setY(-screenHeight / 3f);

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

                // Calculate new position
                float tieX = tieFighterImageView.getX() + normalizedOffsetX * TIE_FIGHTER_SPEED;
                float tieY = tieFighterImageView.getY() + normalizedOffsetY * TIE_FIGHTER_SPEED;

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

                // Check collisions with all asteroids
                for (ImageView asteroid : asteroids) {
                    if (isCollision(tieFighterImageView, asteroid, COLLISION_THRESHOLD)) {
                        handleCollision();
                        return;
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

    private void startAsteroidAnimations() {
        // Start asteroid animations with different positions
        animateAsteroid(asteroids[0], screenWidth * 0.15f - asteroids[0].getWidth() / 2f, screenHeight / 2f);
        animateAsteroid(asteroids[1], screenWidth * 0.65f, screenHeight / 2f);
        animateAsteroid(asteroids[2], screenWidth * 0.15f, 3 * screenHeight / 4f);
        animateAsteroid(asteroids[3], screenWidth * 0.65f, 3 * screenHeight / 4f);
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

    private void animateAsteroid(ImageView asteroid, float startX, float startY) {
        final Point[] currentStartPoint = {new Point((int) startX, (int) startY)};
        final Point[] currentEndPoint = {getRandomPoint()};
        final Path[] currentPath = {createSmoothRandomPath(currentStartPoint[0], currentEndPoint[0])};
        final PathMeasure[] pathMeasure = {new PathMeasure(currentPath[0], false)};
        final float[] length = {pathMeasure[0].getLength()};
        final int[] currentDuration = {random.nextInt(ADDITIONAL_ASTEROID_ANIMATION_DURATION) + MIN_ASTEROID_ANIMATION_DURATION};

        // Set initial position
        asteroid.setX(startX);
        asteroid.setY(startY);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, length[0]);
        animator.setDuration(currentDuration[0]);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            if (!isGameActive) return;

            float distance = (float) animation.getAnimatedValue();
            float[] aCoordinates = new float[2];
            pathMeasure[0].getPosTan(distance, aCoordinates, null);
            asteroid.setX(aCoordinates[0]);
            asteroid.setY(aCoordinates[1]);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isGameActive) return;

                // Create new random path
                currentStartPoint[0] = currentEndPoint[0];
                currentEndPoint[0] = getRandomPoint();
                currentPath[0] = createSmoothRandomPath(currentStartPoint[0], currentEndPoint[0]);
                pathMeasure[0].setPath(currentPath[0], false);
                length[0] = pathMeasure[0].getLength();
                animator.setFloatValues(0f, length[0]);

                // Calculate new duration
                int newDuration = random.nextInt(ADDITIONAL_ASTEROID_ANIMATION_DURATION) + MIN_ASTEROID_ANIMATION_DURATION;
                ValueAnimator durationAnimator = ValueAnimator.ofInt(currentDuration[0], newDuration);
                durationAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                durationAnimator.addUpdateListener(animation1 -> {
                    animator.setDuration((int) animation1.getAnimatedValue());
                });
                durationAnimator.start();
                currentDuration[0] = newDuration;

                if (isGameActive) {
                    animator.start();
                }
            }
        });

        animator.start();
    }

    private Path createSmoothRandomPath(Point startPoint, Point endPoint) {
        Path path = new Path();
        path.moveTo(startPoint.x, startPoint.y);

        Point controlPoint1 = getRandomPoint();
        Point controlPoint2 = getRandomPoint();

        path.cubicTo(controlPoint1.x, controlPoint1.y, controlPoint2.x, controlPoint2.y, endPoint.x, endPoint.y);

        return path;
    }

    private Point getRandomPoint() {
        int x = random.nextInt(screenWidth);
        int y = random.nextInt(screenHeight);
        return new Point(x, y);
    }

    private boolean isCollision(ImageView tieFighter, ImageView asteroid, float collisionThresholdPercentage) {
        // Get bounding rectangles
        Rect tieRect = getImageViewRect(tieFighter);
        Rect asteroidRect = getImageViewRect(asteroid);

        // Check for rectangle intersection
        if (!Rect.intersects(tieRect, asteroidRect)) {
            return false;
        }

        // Calculate intersection area
        Rect intersection = new Rect(Math.max(tieRect.left, asteroidRect.left), Math.max(tieRect.top, asteroidRect.top), Math.min(tieRect.right, asteroidRect.right), Math.min(tieRect.bottom, asteroidRect.bottom));

        int intersectionArea = intersection.width() * intersection.height();
        int smallerObjectArea = Math.min(tieRect.width() * tieRect.height(), asteroidRect.width() * asteroidRect.height());
        int thresholdArea = (int) (smallerObjectArea * collisionThresholdPercentage);

        return intersectionArea >= thresholdArea;
    }

    private Rect getImageViewRect(ImageView imageView) {
        int[] location = new int[2];
        imageView.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + imageView.getWidth(), location[1] + imageView.getHeight());
    }

    private void handleCollision() {
        if (!isGameActive) return;

        // End the game
        isGameActive = false;
        joystickIsPressed = false;
        handler.removeCallbacks(movingTieRunnable);
        handler.removeCallbacks(collisionRunnable);

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
        handler.removeCallbacksAndMessages(null);
    }
}