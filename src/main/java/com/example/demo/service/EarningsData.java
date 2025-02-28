package com.example.demo.service;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
//@Setter
//@Getter
public class EarningsData {
    private String date; // Date of earnings report
    private double epsActual; // Earnings estimate
    private double epsEstimate; // Actual earnings (if available)
    private String hour; // Actual earnings (if available)
    private Integer quarter; // Actual earnings (if available)
    private Long revenueActual; // Actual earnings (if available)
    private Long revenueEstimate; // Actual earnings (if available)
    private String symbol; // Actual earnings (if available)
    private Integer year; // Actual earnings (if available)
    private String name;
    private Double marketCapitalization;
    private String finnhubIndustry;
    private Double revenueGrowth3Y; // In decimal (e.g., 0.15 for 15%)
    private Double epsGrowth3Y;     // In decimal
    private Double roeTTM;          // In percentage
    private Double roaTTM;
    private Double priceAt930;
    private Double priceAt935;
    private Double priceAt940;
    private Double priceAt945;
    private Double priceAt950;
    private Double priceAt1000;
    private Double priceAt1100;
    private Double priceAt1105;
    // Getters and Setters
//
//    @Override
//    public String toString() {
//        return "EarningsData{" +
//                "date='" + date + '\'' +
//                ", epsActual=" + epsActual +
//                ", epsEstimate=" + epsEstimate +
//                ", hour='" + hour + '\'' +
//                ", quarter=" + quarter +
//                ", revenueActual=" + revenueActual +
//                ", revenueEstimate=" + revenueEstimate +
//                ", symbol='" + symbol + '\'' +
//                ", year=" + year +
//                '}';
//    }
//
//    public String getDate() {
//        return date;
//    }
//
//    public void setDate(String date) {
//        this.date = date;
//    }
//
//    public double getEpsActual() {
//        return epsActual;
//    }
//
//    public void setEpsActual(double epsActual) {
//        this.epsActual = epsActual;
//    }
//
//    public double getEpsEstimate() {
//        return epsEstimate;
//    }
//
//    public void setEpsEstimate(double epsEstimate) {
//        this.epsEstimate = epsEstimate;
//    }
//
//    public String getHour() {
//        return hour;
//    }
//
//    public void setHour(String hour) {
//        this.hour = hour;
//    }
//
//    public Integer getQuarter() {
//        return quarter;
//    }
//
//    public void setQuarter(Integer quarter) {
//        this.quarter = quarter;
//    }
//
//    public Integer getRevenueActual() {
//        return revenueActual;
//    }
//
//    public void setRevenueActual(Integer revenueActual) {
//        this.revenueActual = revenueActual;
//    }
//
//    public Integer getRevenueEstimate() {
//        return revenueEstimate;
//    }
//
//    public void setRevenueEstimate(Integer revenueEstimate) {
//        this.revenueEstimate = revenueEstimate;
//    }
//
//    public String getSymbol() {
//        return symbol;
//    }
//
//    public void setSymbol(String symbol) {
//        this.symbol = symbol;
//    }
//
//    public Integer getYear() {
//        return year;
//    }
//
//    public void setYear(Integer year) {
//        this.year = year;
//    }
}