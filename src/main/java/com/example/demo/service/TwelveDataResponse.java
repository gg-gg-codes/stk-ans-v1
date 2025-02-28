// Add to package com.example.demo.service
package com.example.demo.service;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TwelveDataResponse {
    private Map<String, StockData> response;

    @Data
    public static class StockData {
        private Meta meta;
        private List<TimeSeries> values;
        private String status;
    }

    @Data
    public static class Meta {
        private String symbol;
        private String interval;
        private String currency;
        private String exchange_timezone;
        private String exchange;
        private String type;
    }

    @Data
    public static class TimeSeries {
        private String datetime;
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;
    }
}