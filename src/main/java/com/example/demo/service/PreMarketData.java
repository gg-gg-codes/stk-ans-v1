package com.example.demo.service;

public class PreMarketData {
    private double c; // Current price
    private double pc; // Previous close price

    // Getters and Setters
    public double getC() {
        return c;
    }

    public void setC(double c) {
        this.c = c;
    }

    public double getPc() {
        return pc;
    }

    public void setPc(double pc) {
        this.pc = pc;
    }
}