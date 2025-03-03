package com.example.javaamse;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

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
}