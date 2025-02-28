package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class EarningsService {

    @Value("${finnhub.api.key}")
    private String finnhubApiKey;

    @Value("${beforeMarketOpenDateKey}")
    private String beforeMarketOpenDate;

    @Value("${afterMarketCloseDateKey}")
    private String afterMarketCloseDate;

    @Value("${priceDataDateKey}")
    private String priceDataDate;

    // Track API calls within a second
    private final AtomicInteger callsInCurrentSecond = new AtomicInteger(0);
    private long lastSecondResetTime = System.currentTimeMillis();
    private static final int MAX_CALLS_PER_SECOND = 30;

    // Track API calls within a minute
    private final AtomicInteger callsInCurrentMinute = new AtomicInteger(0);
    private long lastMinuteResetTime = System.currentTimeMillis();
    private static final int MAX_CALLS_PER_MINUTE = 60;
    private final RestTemplate restTemplate;

    private final AtomicInteger twelveDataCallsInMinute = new AtomicInteger(0);
    private long twelveDataLastMinuteReset = System.currentTimeMillis();
    private static final int TWELVE_DATA_MAX_CALLS_PER_MINUTE = 8;
    private static final String TWELVE_DATA_API_KEY = "f540bfd5adcc4068a5a689bd690122da";

    public EarningsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<EarningsData> getEarningsBeforeMarketOpenWithPrices() {

        List<EarningsData> earnings = getEarningsBeforeMarketOpen();
        return earnings.stream()
                .map(this::enrichWithPriceData)
                .collect(Collectors.toList());
    }

    public List<EarningsData> getEarningsAfterMarketCloseWithPrices() {
        List<EarningsData> earnings = getEarningsAfterMarketClose();
        return earnings.stream()
                .map(this::enrichWithPriceData)
                .collect(Collectors.toList());
    }

    public List<EarningsData> getEarningsBeforeMarketOpen() {
        System.out.println("getEarningsBeforeMarketOpen=");
        System.out.println("beforeMarketOpenDate="+ beforeMarketOpenDate);
        System.out.println("afterMarketCloseDate="+ afterMarketCloseDate);
        System.out.println("priceDataDate="+ priceDataDate);
        List<EarningsData> beforeMarketOpenEarnings = new ArrayList<>();
    //    LocalDate beforeMarketOpenDate = LocalDate.now();
//        2025-02-21, 9:30 AM EST → UNIX Timestamp: 1740148200
//        2025-02-21, 9:36 AM EST → UNIX Timestamp: 1740148560
        //beforeMarketOpenDate = "2025-02-11";

        String earningsUrl = String.format("https://finnhub.io/api/v1/calendar/earnings?from=%s&to=%s&token=%s",
                beforeMarketOpenDate, beforeMarketOpenDate, finnhubApiKey);

        try {
            //throttleApiCall();
            EarningsCalendarResponse response = restTemplate.getForObject(earningsUrl, EarningsCalendarResponse.class);

            if (response != null && response.getEarningsCalendar() != null) {
                beforeMarketOpenEarnings = response.getEarningsCalendar().stream()
                        .filter(data -> data != null && "bmo".equalsIgnoreCase(data.getHour()) &&
                                ((Objects.nonNull(data.getRevenueActual()) && Objects.nonNull(data.getRevenueEstimate()) && data.getRevenueActual() > data.getRevenueEstimate()) ||
                                        (Objects.nonNull(data.getEpsActual()) && Objects.nonNull(data.getEpsEstimate()) && data.getEpsActual() > data.getEpsEstimate())))
                        .map(this::enrichEarningsData) // Enrich with additional data
                        .sorted((d1, d2) -> {
                            double epsBeat1 = calculatePercentageChangeDouble(d1.getEpsActual(), d1.getEpsEstimate());
                            double revBeat1 = calculatePercentageChangeDouble(d1.getRevenueActual(), d1.getRevenueEstimate());
                            double epsBeat2 = calculatePercentageChangeDouble(d2.getEpsActual(), d2.getEpsEstimate());
                            double revBeat2 = calculatePercentageChangeDouble(d2.getRevenueActual(), d2.getRevenueEstimate());

                            boolean bothPositive1 = epsBeat1 > 0 && revBeat1 > 0;
                            boolean bothPositive2 = epsBeat2 > 0 && revBeat2 > 0;

                            if (bothPositive1 && !bothPositive2) return -1;
                            if (!bothPositive1 && bothPositive2) return 1;

                            if (d1.getRevenueEstimate() == null && d2.getRevenueEstimate() == null) return 0;
                            if (d1.getRevenueEstimate() == null) return 1;
                            if (d2.getRevenueEstimate() == null) return -1;
                            return d2.getRevenueEstimate().compareTo(d1.getRevenueEstimate());
                        })
                        .collect(Collectors.toList());

                printEarningsTable(beforeMarketOpenEarnings);
                System.out.println("ok");
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error fetching earnings data: " + e.getMessage());
        }

        return beforeMarketOpenEarnings;
    }

    public List<EarningsData> getEarningsAfterMarketClose() {
        System.out.println("getEarningsAfterMarketClose=");
        System.out.println("beforeMarketOpenDate="+ beforeMarketOpenDate);
        System.out.println("afterMarketCloseDate="+ afterMarketCloseDate);
        System.out.println("priceDataDate="+ priceDataDate);
        List<EarningsData> afterMarketOpenEarnings = new ArrayList<>();
        //LocalDate afterMarketCloseDate = LocalDate.now().minusDays(1);
       // afterMarketCloseDate = "2025-02-10";
        String earningsUrl = String.format("https://finnhub.io/api/v1/calendar/earnings?from=%s&to=%s&token=%s",
                afterMarketCloseDate, afterMarketCloseDate, finnhubApiKey);

        try {
            throttleApiCall();
            EarningsCalendarResponse response = restTemplate.getForObject(earningsUrl, EarningsCalendarResponse.class);

            if (response != null && response.getEarningsCalendar() != null) {
                afterMarketOpenEarnings = response.getEarningsCalendar().stream()
                        .filter(data -> data != null && "amc".equalsIgnoreCase(data.getHour()) &&
                                ((Objects.nonNull(data.getRevenueActual()) && Objects.nonNull(data.getRevenueEstimate()) && data.getRevenueActual() > data.getRevenueEstimate()) ||
                                        (Objects.nonNull(data.getEpsActual()) && Objects.nonNull(data.getEpsEstimate()) && data.getEpsActual() > data.getEpsEstimate())))
                        .map(this::enrichEarningsData) // Enrich with additional data
                        .sorted((d1, d2) -> {
                            double epsBeat1 = calculatePercentageChangeDouble(d1.getEpsActual(), d1.getEpsEstimate());
                            double revBeat1 = calculatePercentageChangeDouble(d1.getRevenueActual(), d1.getRevenueEstimate());
                            double epsBeat2 = calculatePercentageChangeDouble(d2.getEpsActual(), d2.getEpsEstimate());
                            double revBeat2 = calculatePercentageChangeDouble(d2.getRevenueActual(), d2.getRevenueEstimate());

                            boolean bothPositive1 = epsBeat1 > 0 && revBeat1 > 0;
                            boolean bothPositive2 = epsBeat2 > 0 && revBeat2 > 0;

                            if (bothPositive1 && !bothPositive2) return -1;
                            if (!bothPositive1 && bothPositive2) return 1;

                            if (d1.getRevenueEstimate() == null && d2.getRevenueEstimate() == null) return 0;
                            if (d1.getRevenueEstimate() == null) return 1;
                            if (d2.getRevenueEstimate() == null) return -1;
                            return d2.getRevenueEstimate().compareTo(d1.getRevenueEstimate());
                        })
                        .collect(Collectors.toList());

                printEarningsTable(afterMarketOpenEarnings);
                System.out.println("ok");
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error fetching earnings data: " + e.getMessage());
        }

        return afterMarketOpenEarnings;
    }



    private EarningsData enrichWithPriceData(EarningsData data) {
        String symbol = data.getSymbol().toUpperCase();
        //priceDataDate = "2025-02-11"; // As per requirement
        String url = String.format("https://api.twelvedata.com/time_series?symbol=%s&interval=1min&start_date=%s&apikey=%s",
                symbol, priceDataDate, TWELVE_DATA_API_KEY);

        try {
            twelveDataAPIThrottle();
            // Use TwelveDataResponse.StockData as the target class, as per your successful approach
            TwelveDataResponse.StockData stockData = restTemplate.getForObject(url, TwelveDataResponse.StockData.class);

            if (stockData != null && stockData.getValues() != null) {
                for (TwelveDataResponse.TimeSeries ts : stockData.getValues()) {
                    String time = ts.getDatetime().split(" ")[1];
                    Double price = ts.getOpen() != null ? Double.parseDouble(ts.getOpen()) : 0.0;

                    switch (time) {
                        case "09:30:00": data.setPriceAt930(price); break;
                        case "09:35:00": data.setPriceAt935(price); break;
                        case "09:40:00": data.setPriceAt940(price); break;
                        case "09:45:00": data.setPriceAt945(price); break;
                        case "09:50:00": data.setPriceAt950(price); break;
                        case "10:00:00": data.setPriceAt1000(price); break;
                        case "11:00:00": data.setPriceAt1100(price); break;
                        case "11:05:00": data.setPriceAt1105(price); break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching price data for " + symbol + ": " + e.getMessage());
        }
        return data;
    }


    private EarningsData enrichEarningsData(EarningsData data) {
        String symbol = data.getSymbol();

        // Fetch Company Profile
        String profileUrl = String.format("https://finnhub.io/api/v1/stock/profile2?symbol=%s&token=%s", symbol, finnhubApiKey);
        try {
            throttleApiCall();
            CompanyProfile profile = restTemplate.getForObject(profileUrl, CompanyProfile.class);
            if (profile != null) {
                data.setName(profile.getName());
                data.setMarketCapitalization(profile.getMarketCapitalization());
                data.setFinnhubIndustry(profile.getFinnhubIndustry());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error fetching profile for " + symbol + ": " + e.getMessage());
        }

        // Fetch Stock Metrics
        String metricsUrl = String.format("https://finnhub.io/api/v1/stock/metric?symbol=%s&metric=all&token=%s", symbol, finnhubApiKey);
        try {
            throttleApiCall();
            StockMetricsResponse metricsResponse = restTemplate.getForObject(metricsUrl, StockMetricsResponse.class);
            if (metricsResponse != null && metricsResponse.getMetric() != null) {
                StockMetrics metrics = metricsResponse.getMetric();
                data.setRevenueGrowth3Y(metrics.getRevenueGrowth3Y());
                data.setEpsGrowth3Y(metrics.getEpsGrowth3Y());
                data.setRoeTTM(metrics.getRoeTTM());
                data.setRoaTTM(metrics.getRoaTTM());
            }
        } catch (HttpClientErrorException e) {
            System.err.println("Error fetching metrics for " + symbol + ": " + e.getMessage());
        }

        return data;
    }
    private void throttleApiCall() {
        synchronized (this) {
            long currentTime = System.currentTimeMillis();

            // Reset minute counter if a minute has passed
            if (currentTime - lastMinuteResetTime >= 60000) {
                callsInCurrentMinute.set(0);
                lastMinuteResetTime = currentTime;
            }

            // Reset second counter if a second has passed
            if (currentTime - lastSecondResetTime >= 1000) {
                callsInCurrentSecond.set(0);
                lastSecondResetTime = currentTime;
            }

            // Check minute limit
            if (callsInCurrentMinute.get() >= MAX_CALLS_PER_MINUTE) {
                long sleepTime = 60000 - (currentTime - lastMinuteResetTime);
                if (sleepTime > 0) {
                    try {
                        System.out.println("Minute limit reached, sleeping for " + sleepTime + " ms");
                        Thread.sleep(sleepTime); // Wait until the next minute
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Interrupted while rate limiting: " + e.getMessage());
                    }
                }
                callsInCurrentMinute.set(0);
                callsInCurrentSecond.set(0);
                lastMinuteResetTime = System.currentTimeMillis();
                lastSecondResetTime = System.currentTimeMillis();
            }

            // Check second limit
            if (callsInCurrentSecond.get() >= MAX_CALLS_PER_SECOND) {
                long sleepTime = 1000 - (currentTime - lastSecondResetTime);
                if (sleepTime > 0) {
                    try {
                        System.out.println("Second limit reached, sleeping for " + sleepTime + " ms");
                        Thread.sleep(sleepTime); // Wait until the next second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Interrupted while rate limiting: " + e.getMessage());
                    }
                }
                callsInCurrentSecond.set(0);
                lastSecondResetTime = System.currentTimeMillis();
            }

            // Increment both counters
            callsInCurrentSecond.incrementAndGet();
            callsInCurrentMinute.incrementAndGet();
        }
    }
    private void printEarningsTable(List<EarningsData> earningsDataList) {
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.printf("| %-10s | %-5s | %-8s | %-8s | %-12s | %-12s | %-8s | %-8s | %-20s | %-12s | %-15s | %-15s | %-10s | %-10s | %-8s | %-8s |\n",
                "Symbol", "Hour", "EPS Act.", "EPS Est.", "Rev. Act.", "Rev. Est.", "EPS %", "Rev. %", "Name", "Market Cap", "Industry", "Search Query", "RevGrth3Y", "EPSGrth3Y", "ROE TTM", "ROA TTM");
        System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

        for (EarningsData data : earningsDataList) {
            System.out.printf("| %-10s | %-5s | %-8s | %-8s | %-12s | %-12s | %-8s | %-8s | %-20s | %-12s | %-15s | %-15s | %-10s | %-10s | %-8s | %-8s |\n",
                    data.getSymbol(),
                    data.getHour(),
                    formatDouble(data.getEpsActual()),
                    formatDouble(data.getEpsEstimate()),
                    formatLong(data.getRevenueActual()),
                    formatLong(data.getRevenueEstimate()),
                    calculatePercentageChange(data.getEpsActual(), data.getEpsEstimate()),
                    calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate()),
                    truncateString(data.getName(), 20),
                    formatDouble(data.getMarketCapitalization()),
                    truncateString(data.getFinnhubIndustry(), 15),
                    data.getSymbol() + " share price",
                    formatPercentage(data.getRevenueGrowth3Y()),
                    formatPercentage(data.getEpsGrowth3Y()),
                    formatPercentage(data.getRoeTTM() != null ? data.getRoeTTM() / 100 : null), // Already in %, adjust to decimal
                    formatPercentage(data.getRoaTTM() != null ? data.getRoaTTM() / 100 : null)); // Already in %, adjust to decimal
        }

        System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private String formatDouble(Double value) {
        return value != null ? String.format("%.2f", value) : "N/A";
    }

    private String formatLong(Long value) {
        return value != null ? String.format("%,d", value) : "N/A";
    }

    private String formatPercentage(Double value) {
        return value != null ? String.format("%.2f%%", value * 100) : "N/A";
    }

    private String calculatePercentageChange(Number actual, Number estimate) {
        if (actual == null || estimate == null || estimate.doubleValue() == 0) {
            return "N/A";
        }
        double percentageChange = ((actual.doubleValue() - estimate.doubleValue()) / estimate.doubleValue()) * 100;
        return String.format("%+.2f%%", percentageChange);
    }

    private double calculatePercentageChangeDouble(Number actual, Number estimate) {
        if (actual == null || estimate == null || estimate.doubleValue() == 0) {
            return 0.0;
        }
        return ((actual.doubleValue() - estimate.doubleValue()) / estimate.doubleValue()) * 100;
    }

    private String truncateString(String value, int maxLength) {
        if (value == null) return "N/A";
        return value.length() > maxLength ? value.substring(0, maxLength - 3) + "..." : value;
    }

    private PreMarketData getPreMarketData(String ticker) {
        // Fetch pre-market data for the given ticker
        String preMarketUrl = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", ticker, finnhubApiKey);
        PreMarketData preMarketData = null;

        try {
            preMarketData = restTemplate.getForObject(preMarketUrl, PreMarketData.class);
            Object o1 =
                    restTemplate
                            .getForObject("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=AAPL&interval=1min&apikey=P4O3P4STL9DXX4EG", Object.class);

            Object o2 =
                    restTemplate
                            .getForObject("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=AAPL&interval=1min&apikey=QZEBF8ZKHUYPN69S", Object.class);
        } catch (HttpClientErrorException e) {
            // Handle HTTP errors
            System.err.println("Error fetching pre-market data for " + ticker + ": " + e.getMessage());
        }

        return preMarketData;
    }


    private void twelveDataAPIThrottle() {
        synchronized (this) {
            long currentTime = System.currentTimeMillis();

            if (currentTime - twelveDataLastMinuteReset >= 60000) {
                twelveDataCallsInMinute.set(0);
                twelveDataLastMinuteReset = currentTime;
            }

            if (twelveDataCallsInMinute.get() >= TWELVE_DATA_MAX_CALLS_PER_MINUTE) {
                long sleepTime = 60000 - (currentTime - twelveDataLastMinuteReset);
                if (sleepTime > 0) {
                    try {
                        System.out.println("TwelveData API limit reached, sleeping for " + sleepTime + " ms");
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Interrupted while rate limiting TwelveData API: " + e.getMessage());
                    }
                }
                twelveDataCallsInMinute.set(0);
                twelveDataLastMinuteReset = System.currentTimeMillis();
            }

            twelveDataCallsInMinute.incrementAndGet();
        }
    }

}