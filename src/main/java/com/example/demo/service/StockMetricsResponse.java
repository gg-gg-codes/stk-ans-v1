package com.example.demo.service;

import lombok.Data;

@Data
public class StockMetricsResponse {
    private StockMetrics metric;

    public StockMetrics getMetric() {
        return metric;
    }

    public void setMetric(StockMetrics metric) {
        this.metric = metric;
    }
}

@Data
 class StockMetrics {
    private Double revenueGrowth3Y;
    private Double epsGrowth3Y;
    private Double roeTTM;
    private Double roaTTM;

    // Getters and Setters are provided by @Data
}