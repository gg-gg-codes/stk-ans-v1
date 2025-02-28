package com.example.demo.service;

public class EarningsReport {
    private EarningsData earningsData;
    private PreMarketData preMarketData;

    public EarningsReport(EarningsData earningsData, PreMarketData preMarketData) {
        this.earningsData = earningsData;
        this.preMarketData = preMarketData;
    }

    public String getTicker() {
        return earningsData.getSymbol();
    }

    public double getPreMarketChange() {
        if (preMarketData != null) {
            return preMarketData.getC() - preMarketData.getPc();
        }
        return 0;
    }

    public boolean isPositive() {
        return getPreMarketChange() > 0;
    }

    // Other getters if needed
}