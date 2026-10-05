package com.corsair.deluxe.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.corsair.deluxe.R;

public class MainMenuActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_menu);

        Button startButton = findViewById(R.id.startGameButton);
        Button exitButton = findViewById(R.id.exitGameButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchGame();
            }
        });

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        startButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                launchGame();
            }
        }, 2000);
    }

    private void launchGame() {
        Intent intent = new Intent(MainMenuActivity.this, AndroidLauncher.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
