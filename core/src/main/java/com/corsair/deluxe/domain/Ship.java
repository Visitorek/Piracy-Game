package com.corsair.deluxe.domain;

public class Ship {
    private final String name;
    private HexCoordinate position;
    private int hullIntegrity = 100;
    private int crew = 20;
    private int cargo = 0;
    private int movementPoints = 6;
    private int foodSupplies = 12;
    private int rum = 8;

    public Ship(String name) {
        this.name = name;
        this.position = new HexCoordinate(0, 0);
    }

    public String getName() {
        return name;
    }

    public HexCoordinate getPosition() {
        return position;
    }

    public void setPosition(HexCoordinate position) {
        this.position = position;
    }

    public int getHullIntegrity() {
        return hullIntegrity;
    }

    public void setHullIntegrity(int hullIntegrity) {
        this.hullIntegrity = hullIntegrity;
    }

    public int getCrew() {
        return crew;
    }

    public void setCrew(int crew) {
        this.crew = crew;
    }

    public int getCargo() {
        return cargo;
    }

    public void setCargo(int cargo) {
        this.cargo = cargo;
    }

    public int getMovementPoints() {
        return movementPoints;
    }

    public void setMovementPoints(int movementPoints) {
        this.movementPoints = movementPoints;
    }

    public int getFoodSupplies() {
        return foodSupplies;
    }

    public void setFoodSupplies(int foodSupplies) {
        this.foodSupplies = foodSupplies;
    }

    public int getRum() {
        return rum;
    }

    public void setRum(int rum) {
        this.rum = rum;
    }
}


