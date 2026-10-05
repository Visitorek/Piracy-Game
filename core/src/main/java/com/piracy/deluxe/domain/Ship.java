package com.piracy.deluxe.domain;

public class Ship {
    private final String name;
    private int hullIntegrity = 100;
    private int crew = 20;
    private int cargo = 0;

    public Ship(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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
}
