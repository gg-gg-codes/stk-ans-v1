package com.example.demo.service;

import java.util.List;

public class EarningsCalendarResponse {
    private List<EarningsData> earningsCalendar;

    public List<EarningsData> getEarningsCalendar() {
        return earningsCalendar;
    }

    public void setEarningsCalendar(List<EarningsData> earningsCalendar) {
        this.earningsCalendar = earningsCalendar;
    }
}