package com.corsair.deluxe.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.corsair.deluxe.R;
import com.corsair.deluxe.core.GameSettings;

public class MainMenuActivity extends ImmersiveActivity {
    private View optionsOverlay;
    private Button soundButton;
    private Button musicButton;
    private Button difficultyButton;
    private Button debugButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_menu);

        Button startButton = findViewById(R.id.startGameButton);
        Button optionsButton = findViewById(R.id.optionsButton);
        Button exitButton = findViewById(R.id.exitGameButton);

        optionsOverlay = findViewById(R.id.optionsOverlay);
        soundButton = findViewById(R.id.optionSoundButton);
        musicButton = findViewById(R.id.optionMusicButton);
        difficultyButton = findViewById(R.id.optionDifficultyButton);
        debugButton = findViewById(R.id.optionDebugButton);
        Button closeButton = findViewById(R.id.optionCloseButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainMenuActivity.this, AndroidLauncher.class);
                startActivity(intent);
            }
        });

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        optionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOptionsVisible(true);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setOptionsVisible(false);
            }
        });

        soundButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameSettings.toggleSound();
                refreshOptionLabels();
            }
        });

        musicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameSettings.toggleMusic();
                refreshOptionLabels();
            }
        });

        difficultyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameSettings.cycleDifficulty();
                refreshOptionLabels();
            }
        });

        debugButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameSettings.toggleDebugMode();
                refreshOptionLabels();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (optionsOverlay.getVisibility() == View.VISIBLE) {
            setOptionsVisible(false);
            return;
        }
        super.onBackPressed();
    }

    private void setOptionsVisible(boolean visible) {
        if (visible) {
            refreshOptionLabels();
            optionsOverlay.setVisibility(View.VISIBLE);
        } else {
            optionsOverlay.setVisibility(View.GONE);
        }
    }

    private void refreshOptionLabels() {
        soundButton.setText(optionLabel(R.string.option_sound, toggleLabel(GameSettings.isSoundEnabled())));
        musicButton.setText(optionLabel(R.string.option_music, toggleLabel(GameSettings.isMusicEnabled())));
        difficultyButton.setText(optionLabel(R.string.option_difficulty,
                difficultyLabel(GameSettings.getDifficulty())));
        debugButton.setText(optionLabel(R.string.option_debug, toggleLabel(GameSettings.isDebugMode())));
    }

    private String optionLabel(int nameRes, String value) {
        return getString(R.string.options_entry_format, getString(nameRes), value);
    }

    private String toggleLabel(boolean enabled) {
        return getString(enabled ? R.string.toggle_on : R.string.toggle_off);
    }

    private String difficultyLabel(GameSettings.Difficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return getString(R.string.difficulty_easy);
            case HARD:
                return getString(R.string.difficulty_hard);
            default:
                return getString(R.string.difficulty_normal);
        }
    }
}

