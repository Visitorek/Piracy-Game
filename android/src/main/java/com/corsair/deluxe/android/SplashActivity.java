package com.corsair.deluxe.android;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.corsair.deluxe.R;

public class SplashActivity extends ImmersiveActivity {
    private static final int SPLASH_DURATION_MS = 1500;

    private ObjectAnimator progressAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        ImageView splashImage = findViewById(R.id.splashImage);
        ProgressBar progressBar = findViewById(R.id.splashProgress);

        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, progressBar.getMax());
        progressAnimator.setDuration(SPLASH_DURATION_MS);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.start();

        splashImage.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainMenuActivity.class);
                startActivity(intent);
                finish();
            }
        }, SPLASH_DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        super.onDestroy();
    }
}
