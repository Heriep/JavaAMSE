package com.example.javaamse;

import static com.example.javaamse.R.id.Pad_exterior;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private int screenWidth;
    private int screenHeight;
    private final Random random = new Random();

    private ImageView joystickPad;
    private ImageView tieFighterImageView;
    private ImageView joystickBase;
    private Handler handlerForMovingTie;
    private Runnable movingTie;
    private boolean joystickIsPressed = false;
    private float joystickCenterX;
    private float joystickCenterY;
    private float maxJoystickOffset;
    private Handler collisionHandler;
    private Runnable collisionRunnable;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Get screen dimensions
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;

        ImageView asteroid1 = findViewById(R.id.Asteroid1);
        ImageView asteroid2 = findViewById(R.id.Asteroid2);
        ImageView asteroid3 = findViewById(R.id.Asteroid3);
        ImageView asteroid4 = findViewById(R.id.Asteroid4);

        joystickPad = findViewById(R.id.Pad_center);
        tieFighterImageView = findViewById(R.id.Tie);
        joystickBase = findViewById(R.id.Pad_exterior);

        // Calculate the center of the joystick base
        joystickBase.post(() -> {
            joystickCenterX = joystickBase.getX() + joystickBase.getWidth() / 2f;
            joystickCenterY = joystickBase.getY() + joystickBase.getHeight() / 2f;
            maxJoystickOffset = joystickBase.getWidth() / 2f;
        });

        collisionHandler = new Handler();
        collisionRunnable = new Runnable() {
            @Override
            public void run() {
                // Détection de collision
                if (isCollision(tieFighterImageView, asteroid1, 0.3f)) {
                    handleCollision(tieFighterImageView);
                } else if (isCollision(tieFighterImageView, asteroid2, 0.3f)) {
                    handleCollision(tieFighterImageView);
                } else if (isCollision(tieFighterImageView, asteroid3, 0.3f)) {
                    handleCollision(tieFighterImageView);
                } else if (isCollision(tieFighterImageView, asteroid4, 0.3f)) {
                    handleCollision(tieFighterImageView);
                }

                // Planifier la prochaine vérification de collision
                collisionHandler.postDelayed(this, 10); // Vérifier toutes les 10 ms (ajuster si nécessaire)
            }
        };
        collisionHandler.post(collisionRunnable);

        handlerForMovingTie = new Handler();
        movingTie = new Runnable() {
            @Override
            public void run() {
                // Calculate the direction and speed based on the joystick position
                float offsetX = joystickPad.getX() + joystickPad.getWidth() / 2f - joystickCenterX;
                float offsetY = joystickPad.getY() + joystickPad.getHeight() / 2f - joystickCenterY;

                // Normalize the offset to a range of -1 to 1
                float normalizedOffsetX = offsetX / maxJoystickOffset;
                float normalizedOffsetY = offsetY / maxJoystickOffset;

                // Adjust the speed based on how far the joystick is moved
                float speed = 5f; // Adjust this value to control the speed
                float tieX = tieFighterImageView.getX() + normalizedOffsetX * speed;
                float tieY = tieFighterImageView.getY() + normalizedOffsetY * speed;

                // Keep the Tie Fighter within the screen bounds
                tieX = Math.max(0, Math.min(tieX, getResources().getDisplayMetrics().widthPixels - tieFighterImageView.getWidth()));
                tieY = Math.max(0, Math.min(tieY, getResources().getDisplayMetrics().heightPixels - tieFighterImageView.getHeight()));

                tieFighterImageView.setX(tieX);
                tieFighterImageView.setY(tieY);

                if (joystickIsPressed) {
                    handlerForMovingTie.postDelayed(this, 10);
                }
            }
        };

        joystickPad.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        joystickIsPressed = true;
                        handlerForMovingTie.post(movingTie);
                        moveJoystickPad(event);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        moveJoystickPad(event);
                        break;
                    case MotionEvent.ACTION_UP:
                        joystickIsPressed = false;
                        // Reset the joystick pad to the center
                        joystickPad.setX(joystickCenterX - joystickPad.getWidth() / 2f);
                        joystickPad.setY(joystickCenterY - joystickPad.getHeight() / 2f);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });

        animateAsteroid(asteroid1);
        animateAsteroid(asteroid2);
        animateAsteroid(asteroid3);
        animateAsteroid(asteroid4);
    }

    private void animateAsteroid(ImageView asteroid) {
        final Point[] currentStartPoint = {getRandomPoint()};
        final Point[] currentEndPoint = {getRandomPoint()};
        final Path[] currentPath = {createSmoothRandomPath(currentStartPoint[0], currentEndPoint[0])};
        final PathMeasure[] pathMeasure = {new PathMeasure(currentPath[0], false)};
        final float[] length = {pathMeasure[0].getLength()};
        final int[] currentDuration = {random.nextInt(3000) + 4000};

        ValueAnimator animator = ValueAnimator.ofFloat(0f, length[0]);
        animator.setDuration(currentDuration[0]);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float distance = (float) animation.getAnimatedValue();
            float[] aCoordinates = new float[2];
            pathMeasure[0].getPosTan(distance, aCoordinates, null);
            asteroid.setX(aCoordinates[0]);
            asteroid.setY(aCoordinates[1]);
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset the path to create a new random path
                currentStartPoint[0] = currentEndPoint[0];
                currentEndPoint[0] = getRandomPoint();
                currentPath[0] = createSmoothRandomPath(currentStartPoint[0], currentEndPoint[0]);
                pathMeasure[0].setPath(currentPath[0], false);
                length[0] = pathMeasure[0].getLength();
                animator.setFloatValues(0f, length[0]);
                int newDuration = random.nextInt(3000) + 4000;
                ValueAnimator durationAnimator = ValueAnimator.ofInt(currentDuration[0], newDuration);
                durationAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                durationAnimator.addUpdateListener(animation1 -> {
                    animator.setDuration((int) animation1.getAnimatedValue());
                });
                durationAnimator.start();
                currentDuration[0] = newDuration;
                animator.start();
            }
        });

        // Set initial position
        asteroid.setX(currentStartPoint[0].x);
        asteroid.setY(currentStartPoint[0].y);

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
        // Ensure points are within screen bounds, considering asteroid size
        int x = random.nextInt(screenWidth);
        int y = random.nextInt(screenHeight);
        return new Point(x, y);
    }

    private void moveJoystickPad(MotionEvent event) {
        float touchX = event.getRawX();
        float touchY = event.getRawY();

        // Calculate the distance from the center of the joystick base to the touch point
        float distance = (float) Math.sqrt(Math.pow(touchX - joystickCenterX, 2) + Math.pow(touchY - joystickCenterY, 2));

        // Calculate the angle between the center of the joystick base and the touch point
        float angle = (float) Math.atan2(touchY - joystickCenterY, touchX - joystickCenterX);

        // If the touch point is outside the joystick base, clamp it to the edge of the base
        if (distance > maxJoystickOffset) {
            touchX = joystickCenterX + maxJoystickOffset * (float) Math.cos(angle);
            touchY = joystickCenterY + maxJoystickOffset * (float) Math.sin(angle);
        }

        // Set the position of the joystick pad
        joystickPad.setX(touchX - joystickPad.getWidth() / 2f);
        joystickPad.setY(touchY - joystickPad.getHeight() / 2f);
    }

    private boolean isCollision(ImageView tieFighter, ImageView asteroid, float collisionThresholdPercentage) {
        // Get the bounding rectangles
        Rect tieRect = getImageViewRect(tieFighter);
        Rect asteroidRect = getImageViewRect(asteroid);

        // Check if the rectangles overlap at all
        if (!Rect.intersects(tieRect, asteroidRect)) {
            return false;
        }

        // Calculate the intersection rectangle
        Rect intersection = new Rect(
                Math.max(tieRect.left, asteroidRect.left),
                Math.max(tieRect.top, asteroidRect.top),
                Math.min(tieRect.right, asteroidRect.right),
                Math.min(tieRect.bottom, asteroidRect.bottom)
        );

        // Calculate the area of the intersection
        int intersectionArea = intersection.width() * intersection.height();

        // Calculate the area of the smaller object
        int smallerObjectArea = Math.min(tieRect.width() * tieRect.height(), asteroidRect.width() * asteroidRect.height());

        // Calculate the threshold area
        int thresholdArea = (int) (smallerObjectArea * collisionThresholdPercentage);

        // Check if the intersection area is greater than the threshold
        return intersectionArea >= thresholdArea;
    }

    private Rect getImageViewRect(ImageView imageView) {
        int[] location = new int[2];
        imageView.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + imageView.getWidth(), location[1] + imageView.getHeight());
    }

    private void handleCollision(ImageView tieFighter) {
        // Stop moving the Tie Fighter
        joystickIsPressed = false;
        handlerForMovingTie.removeCallbacks(movingTie);

        // Display the explosion animation
        displayExplosion(tieFighter);
    }

    private void displayExplosion(ImageView tieFighter) {
        // Create a new ImageView for the explosion
        ImageView explosionImageView = new ImageView(this);
        explosionImageView.setImageResource(R.drawable.explosion); // Replace with your explosion drawable

        // Set the size of the explosion to match the Tie Fighter
        explosionImageView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(tieFighter.getWidth(), tieFighter.getHeight()));

        // Add the explosion to the layout
        ((android.view.ViewGroup) findViewById(R.id.main)).addView(explosionImageView);

        // Position the explosion at the center of the Tie Fighter
        int[] tieLocation = new int[2];
        tieFighter.getLocationOnScreen(tieLocation);

        // Calculate the center of the Tie Fighter
        int tieCenterX = tieLocation[0] ;
        int tieCenterY = tieLocation[1] - tieFighter.getHeight() / 2;

        // Calculate the center of the explosion

        explosionImageView.setX(tieCenterX);
        explosionImageView.setY(tieCenterY);

        // Create a fade-out animation
        android.animation.ObjectAnimator fadeOut = android.animation.ObjectAnimator.ofFloat(explosionImageView, "alpha", 1f, 0f);
        fadeOut.setDuration(500); // Adjust duration as needed
        fadeOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Remove the explosion after the animation
                ((android.view.ViewGroup) findViewById(R.id.main)).removeView(explosionImageView);
            }
        });
        fadeOut.start();
    }
}