package com.corsair.deluxe.presentation;

import com.badlogic.gdx.graphics.OrthographicCamera;

public class CameraManager {
    private final OrthographicCamera camera;

    public CameraManager(OrthographicCamera camera) {
        this.camera = camera;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public void update() {
        camera.update();
    }

    public void pan(float deltaX, float deltaY) {
        camera.position.x -= deltaX;
        camera.position.y -= deltaY;
    }

    public void zoom(float amount) {
        camera.zoom = Math.max(0.75f, Math.min(2.0f, camera.zoom + amount));
    }
}

