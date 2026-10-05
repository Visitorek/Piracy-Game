package com.piracy.deluxe.core;

public class WorldState {
    private int day = 1;
    private int gold = 500;
    private int currentTurn = 1;

    public int getDay() {
        return day;
    }

    public void advanceDay() {
        day += 1;
    }

    public int getGold() {
        return gold;
    }

    public void addGold(int amount) {
        gold += amount;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public void nextTurn() {
        currentTurn += 1;
    }
}
