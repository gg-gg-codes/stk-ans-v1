package com.example.demo.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.example.demo.service.EarningsData;
import com.example.demo.service.EarningsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.stream.Collectors;

@RestController
public class Controllerv1 {

    @Autowired
    private EarningsService earningsService;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;


    @GetMapping("/earnings")
    public String getEarningsDataAsHtml(
            @RequestParam(required = false) String beforeMarketOpenDateKey,
            @RequestParam(required = false) String afterMarketCloseDateKey,
            @RequestParam(required = false) String priceDataDateKey) throws JsonProcessingException, ParseException {
// Start timing

        long startTime = System.currentTimeMillis();

        // Get today's date dynamically with each request
        LocalDate today = LocalDate.now();

        // Set defaults to today's date and adjust for Monday
        String defaultBeforeMarketOpenDate = today.toString();
        String defaultAfterMarketCloseDate = today.minusDays(1).toString(); // Yesterday by default
        String defaultPriceDataDate = today.toString();

        // Adjust dates if it's Monday
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            defaultBeforeMarketOpenDate = today.toString();
            defaultPriceDataDate = today.toString();
            defaultAfterMarketCloseDate = today.minusDays(3).toString(); // Previous Friday
        }

        // Use query params if provided, otherwise use computed defaults
        String effectiveBeforeMarketOpenDate = beforeMarketOpenDateKey != null ? beforeMarketOpenDateKey : defaultBeforeMarketOpenDate;
        String effectiveAfterMarketCloseDate = afterMarketCloseDateKey != null ? afterMarketCloseDateKey : defaultAfterMarketCloseDate;
        String effectivePriceDataDate = priceDataDateKey != null ? priceDataDateKey : defaultPriceDataDate;

        // Log the effective dates
        System.out.println("effectiveBeforeMarketOpenDate=" + effectiveBeforeMarketOpenDate);
        System.out.println("effectiveAfterMarketCloseDate=" + effectiveAfterMarketCloseDate);
        System.out.println("effectivePriceDataDate=" + effectivePriceDataDate);

        List<String> bmoResults = nasdaqSpecificEarningCalendergoogleScraper(effectiveBeforeMarketOpenDate, "before");
        List<String> amcResults = nasdaqSpecificEarningCalendergoogleScraper(effectiveAfterMarketCloseDate, "after");

        System.out.println("#######NASDAQ bmoResults#######\n" + bmoResults);
        System.out.println("#######NASDAQ amcResults#######\n" + amcResults);

        System.out.println("########Input Received At Controller Level#######");
//        System.out.println("beforeMarketOpenDateKey=" + beforeMarketOpenDateKey);
//        System.out.println("priceDataDateKey=" + priceDataDateKey);
//        System.out.println("afterMarketCloseDateKey=" + afterMarketCloseDateKey);
        System.out.println("##################################################");
        List<EarningsData> earningsBeforeMarketOpen = earningsService.getEarningsBeforeMarketOpen(
                effectiveBeforeMarketOpenDate);
        List<EarningsData> earningsAfterMarketClose = earningsService.getEarningsAfterMarketClose(
                effectiveAfterMarketCloseDate);
        // Combine and sort by decreasing Rev % (Revenue beat %)
        List<EarningsData> combinedEarnings = new ArrayList<>();
        combinedEarnings.addAll(earningsBeforeMarketOpen);
        combinedEarnings.addAll(earningsAfterMarketClose);
        combinedEarnings.sort(Comparator.comparingDouble((EarningsData data) -> {
            try {
                String revPercentage = calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate());
                // Remove the "%" and parse as double, handling "N/A"
                if ("N/A".equals(revPercentage)) return Double.NEGATIVE_INFINITY;
                return Double.parseDouble(revPercentage.replace("%", ""));
            } catch (NumberFormatException e) {
                return Double.NEGATIVE_INFINITY; // Default for invalid data
            }
        }).reversed());
// Step 1: Extract the first word of company names from combinedEarnings
        Set<String> combinedCompanyFirstWords = combinedEarnings.stream()
                .map(data -> getFirstWord(data.getName()))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());

        // Step 2: Remove duplicates from bmoResults based on the first word
        bmoResults = bmoResults.stream()
                .filter(result -> {
                    String firstWord = extractFirstWordFromResult(result);
                    System.out.println("firstWord=" + firstWord);
                    System.out.println("combinedCompanyFirstWords.toString=" + combinedCompanyFirstWords.toString());
                    return !combinedCompanyFirstWords.toString().contains(firstWord);
                })
                .collect(Collectors.toList());

        // Step 3: Remove duplicates from amcResults based on the first word
        amcResults = amcResults.stream()
                .filter(result -> {
                    String firstWord = extractFirstWordFromResult(result);
                    System.out.println("firstWord=" + firstWord);
                    System.out.println("combinedCompanyFirstWords.toString=" + combinedCompanyFirstWords.toString());
                    return !combinedCompanyFirstWords.toString().contains(firstWord);
                })
                .collect(Collectors.toList());


        // End timing
        long endTime = System.currentTimeMillis();
        long timeTaken = (endTime - startTime) / 60000; // Time in min

        // Build HTML table
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><title>Earnings Report</title>");
        html.append("<style>");
        html.append("table { border-collapse: collapse; width: 100%; font-family: Arial, sans-serif; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #4CAF50; color: white; }");
        html.append("tr:nth-child(even) { background-color: #ADD8E6; }"); // Light blue for even rows
        html.append("tr:nth-child(odd) { background-color: #FFFFFF; }");  // White for odd rows
        html.append("</style>");
        html.append("</head><body>");
        html.append("<h2>Earnings Report (Time Taken: ").append(timeTaken).append(" minutes)</h2>");
        html.append("<h2>effectiveBeforeMarketOpenDate=").append(effectiveBeforeMarketOpenDate).append("</h2>");
        html.append("<h2>effectiveAfterMarketCloseDate=").append(effectiveAfterMarketCloseDate).append("</h2>");
        html.append("<table>");

        // Table header
        html.append("<tr>");
        html.append("<th>Symbol</th>");
        html.append("<th>Hour</th>");
        html.append("<th>EPS Act.</th>");
        html.append("<th>EPS Est.</th>");
        html.append("<th>Rev. Act.</th>");
        html.append("<th>Rev. Est.</th>");
        html.append("<th>EPS %</th>");
        html.append("<th>Rev. %</th>");
        html.append("<th>Name</th>");
        html.append("<th>Market Cap</th>");
        html.append("<th>Industry</th>");
        html.append("<th>Search Query</th>");
        html.append("<th>search query for guidance</th>");
        html.append("<th>RevGrth3Y</th>");
        html.append("<th>EPSGrth3Y</th>");
        html.append("<th>ROE TTM</th>");
        html.append("<th>ROA TTM</th>");

        html.append("</tr>");

        // Table rows
        for (EarningsData data : combinedEarnings) {
            html.append("<tr>");
            html.append("<td>").append(data.getSymbol()).append("</td>");
            html.append("<td>").append(data.getHour()).append("</td>");
            html.append("<td>").append(formatDouble(data.getEpsActual())).append("</td>");
            html.append("<td>").append(formatDouble(data.getEpsEstimate())).append("</td>");
            html.append("<td>").append(formatLong(data.getRevenueActual())).append("</td>");
            html.append("<td>").append(formatLong(data.getRevenueEstimate())).append("</td>");
            html.append("<td>").append(calculatePercentageChange(data.getEpsActual(), data.getEpsEstimate())).append("</td>");
            html.append("<td>").append(calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate())).append("</td>");
            html.append("<td>").append(truncateString(data.getName(), 20)).append("</td>");
            html.append("<td>").append(formatDouble(data.getMarketCapitalization())).append("</td>");
            html.append("<td>").append(truncateString(data.getFinnhubIndustry(), 15)).append("</td>");
            html.append("<td>").append(data.getName() + " share price").append("</td>");
            html.append("<td>").append(data.getName() + " \"guidance\" \"earnings release\" investing.com").append("</td>");
            html.append("<td>").append(formatPercentage(data.getRevenueGrowth3Y())).append("</td>");
            html.append("<td>").append(formatPercentage(data.getEpsGrowth3Y())).append("</td>");
            html.append("<td>").append(formatPercentage(data.getRoeTTM() != null ? data.getRoeTTM() / 100 : null)).append("</td>");
            html.append("<td>").append(formatPercentage(data.getRoaTTM() != null ? data.getRoaTTM() / 100 : null)).append("</td>");

            html.append("</tr>");
        }

        html.append("</table></body></html>");

        // Add Combined Search Query heading and list with new lines
        html.append("<p>Combined Search Query:<br>");
        html.append(combinedEarnings.stream()
                .map(data -> "\"" + data.getName() + " share price\"")
                .collect(Collectors.joining("<br>")));
        html.append("</p>");

        // Add a new line after Combined Search Query
        html.append("<br>");

        // Add two new lines and formatted rules
        html.append("<br>");
        html.append("<pre style='font-family: Arial, sans-serif; white-space: pre-wrap;'>");
        html.append("if premarket around 5% & rev%>3.5 and eps % >=21 or eps % > 12 or eps%>=9 and industry not equal to real estate and construction and market cap > 605\n");
        html.append("if premarket around 5% & rev>8 and eps% > -21  or eps%>1.5% & industry not equal to Metals and mining\n");
        html.append("if premarket around 5% & rev>-3.70 & eps % around 500% and market cap>500\n");
        html.append("</pre>");
        html.append("<br>");
        html.append("#####NASDAQ:bmoResults#####");
        html.append("<br>");
        html.append(bmoResults.stream().collect(Collectors.joining("<br>")));
        html.append("<br>");
        html.append("<br>");
        html.append("<br>");
        html.append("#####NASDAQ:amcResults#####");
        html.append("<br>");
        html.append(amcResults.stream().collect(Collectors.joining("<br>")));
        html.append("<br>");
        html.append("<br>");
        System.out.println("DONE");
        return html.toString();
    }

    private String getFirstWord(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] words = name.trim().split("\\s+");
        return words.length > 0 ? words[0] : "";
    }

    // Helper method to extract the first word of the company name from a result string
    private String extractFirstWordFromResult(String result) {
        // Split the result on the first ":" to get the title
        String[] parts = result.split(":", 2);
        if (parts.length < 1) return "";

        String title = parts[0].trim();

        // Extract the company name from the title
        // Example title: "Dollar Tree Inc. Common Stock (DLTR) Earnings Report Date ..."
        // We want the first word of the company name, e.g., "Dollar"
        // Remove the ticker and everything after it
        int tickerStart = title.indexOf("(");
        if (tickerStart != -1) {
            title = title.substring(0, tickerStart).trim();
        }

        // Remove "Common Stock" if present
        title = title.replace("Common Stock", "").trim();

        // Remove "Earnings Report Date ..." if present
        int earningsIndex = title.indexOf("Earnings Report Date");
        if (earningsIndex != -1) {
            title = title.substring(0, earningsIndex).trim();
        }

        // Get the first word of the remaining title
        return getFirstWord(title);
    }

    private List<String> nasdaqSpecificEarningCalendergoogleScraper(String nasdaqGoogleScrapperDate, String beforeOrAfter) throws JsonProcessingException, ParseException {
        List<String> results = new ArrayList<>();
        System.out.println("#####nasdaqSpecificEarningCalendergoogleScraper#####");
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd/yyyy");

        // Parse the input date string to a Date object
        Date date = inputFormat.parse(nasdaqGoogleScrapperDate);

        // Format the Date object to the desired output format
        String nasdaqDate = outputFormat.format(date);
        String querytosearch = "\"" + nasdaqDate + " " + beforeOrAfter + "\"";
        // this is api key "AIzaSyBxF0DeQLwzTlTGryFprAMoXIGZwI2i4-4" and this is searchengineID 130eddca42c1b4cb1
        String url = "https://www.googleapis.com/customsearch/v1?key=" + "AIzaSyBxF0DeQLwzTlTGryFprAMoXIGZwI2i4-4" +
                "&cx=" + "130eddca42c1b4cb1" + "&q=" + querytosearch;
        String forObject = restTemplate.getForObject(url, String.class);
// Parse the JSON response
        JsonNode rootNode = objectMapper.readTree(forObject);
        JsonNode itemsNode = rootNode.path("items");
        if (itemsNode.isArray()) {
            // Iterate over each item in the "items" array
            for (JsonNode item : itemsNode) {
                // Extract the "snippet" field
                String title = item.path("title").asText();
                String snippet = item.path("snippet").asText();
                if (!title.isEmpty() && !snippet.isEmpty()) {
                    String combinedResult = title + ":" + snippet + "\n";
                    results.add(combinedResult);
                }
            }
        }
        return results;
    }

//    @GetMapping("/earningsWithPrice")
//    public String getEarningsDataWithPriceAsHtml() {
//        long startTime = System.currentTimeMillis();
//
//        List<EarningsData> earningsBeforeMarketOpen = earningsService.getEarningsBeforeMarketOpenWithPrices();
//        List<EarningsData> earningsAfterMarketClose = earningsService.getEarningsAfterMarketCloseWithPrices();
//
//        // Combine and sort by decreasing Rev % (Revenue beat %)
//        List<EarningsData> combinedEarnings = new ArrayList<>();
//        combinedEarnings.addAll(earningsBeforeMarketOpen);
//        combinedEarnings.addAll(earningsAfterMarketClose);
//        combinedEarnings.sort(Comparator.comparingDouble((EarningsData data) -> {
//            try {
//                String revPercentage = calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate());
//                // Remove the "%" and parse as double, handling "N/A"
//                if ("N/A".equals(revPercentage)) return Double.NEGATIVE_INFINITY;
//                return Double.parseDouble(revPercentage.replace("%", ""));
//            } catch (NumberFormatException e) {
//                return Double.NEGATIVE_INFINITY; // Default for invalid data
//            }
//        }).reversed());
//
//        long endTime = System.currentTimeMillis();
//        long timeTaken = (endTime - startTime) / 60000;
//
//        StringBuilder html = new StringBuilder();
//        html.append("<!DOCTYPE html><html><head><title>Earnings Report with Prices</title>");
//        html.append("<style>table { border-collapse: collapse; width: 100%; font-family: Arial, sans-serif; }");
//        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
//        html.append("th { background-color: #4CAF50; color: white; }");
//        html.append("tr:nth-child(even) { background-color: #ADD8E6; }");
//        html.append("tr:nth-child(odd) { background-color: #FFFFFF; }");
//        html.append("</style></head><body>");
//        html.append("<h2>Earnings Report with Prices (Time Taken: ").append(timeTaken).append(" minutes)</h2>");
//        html.append("<table>");
//
//        // Table header with new price columns
//        html.append("<tr><th>Symbol</th><th>Hour</th><th>EPS Act.</th><th>EPS Est.</th><th>Rev. Act.</th><th>Rev. Est.</th>");
//        html.append("<th>EPS %</th><th>Rev. %</th><th>Name</th><th>Market Cap</th><th>Industry</th><th>Search Query</th>");
//        html.append("<th>RevGrth3Y</th><th>EPSGrth3Y</th><th>ROE TTM</th><th>ROA TTM</th>");
//        html.append("<th>Price 9:30</th><th>Price 9:35</th><th>Price 9:40</th><th>Price 9:45</th>");
//        html.append("<th>Price 9:50</th><th>Price 10:00</th><th>Price 11:00</th><th>Price 11:05</th></tr>");
//
//        // Table rows
//        StringBuilder csvContent = new StringBuilder();
//        csvContent.append("Symbol,Hour,EPS Act.,EPS Est.,Rev. Act.,Rev. Est.,EPS %,Rev %,Name,Market Cap,Industry,Search Query,RevGrth3Y,EPSGrth3Y,ROE TTM,ROA TTM,Price 9:30,Price 9:35,Price 9:40,Price 9:45,Price 9:50,Price 10:00,Price 11:00,Price 11:05\n");
//
//        for (EarningsData data : combinedEarnings) {
//            html.append("<tr>");
//            html.append("<td>").append(data.getSymbol()).append("</td>");
//            html.append("<td>").append(data.getHour()).append("</td>");
//            html.append("<td>").append(formatDouble(data.getEpsActual())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getEpsEstimate())).append("</td>");
//            html.append("<td>").append(formatLong(data.getRevenueActual())).append("</td>");
//            html.append("<td>").append(formatLong(data.getRevenueEstimate())).append("</td>");
//            html.append("<td>").append(calculatePercentageChange(data.getEpsActual(), data.getEpsEstimate())).append("</td>");
//            html.append("<td>").append(calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate())).append("</td>");
//            html.append("<td>").append(truncateString(data.getName(), 20)).append("</td>");
//            html.append("<td>").append(formatDouble(data.getMarketCapitalization())).append("</td>");
//            html.append("<td>").append(truncateString(data.getFinnhubIndustry(), 15)).append("</td>");
//            html.append("<td>").append(data.getName() + " share price").append("</td>");
//            html.append("<td>").append(formatPercentage(data.getRevenueGrowth3Y())).append("</td>");
//            html.append("<td>").append(formatPercentage(data.getEpsGrowth3Y())).append("</td>");
//            html.append("<td>").append(formatPercentage(data.getRoeTTM() != null ? data.getRoeTTM() / 100 : null)).append("</td>");
//            html.append("<td>").append(formatPercentage(data.getRoaTTM() != null ? data.getRoaTTM() / 100 : null)).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt930())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt935())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt940())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt945())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt950())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt1000())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt1100())).append("</td>");
//            html.append("<td>").append(formatDouble(data.getPriceAt1105())).append("</td>");
//            html.append("</tr>");
//
//            // CSV row
//            csvContent.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
//                    data.getSymbol(),
//                    data.getHour(),
//                    formatDouble(data.getEpsActual()),
//                    formatDouble(data.getEpsEstimate()),
//                    formatLong(data.getRevenueActual()),
//                    formatLong(data.getRevenueEstimate()),
//                    calculatePercentageChange(data.getEpsActual(), data.getEpsEstimate()),
//                    calculatePercentageChange(data.getRevenueActual(), data.getRevenueEstimate()),
//                    truncateString(data.getName(), 20),
//                    formatDouble(data.getMarketCapitalization()),
//                    truncateString(data.getFinnhubIndustry(), 15),
//                    data.getName() + " share price",
//                    formatPercentage(data.getRevenueGrowth3Y()),
//                    formatPercentage(data.getEpsGrowth3Y()),
//                    formatPercentage(data.getRoeTTM() != null ? data.getRoeTTM() / 100 : null),
//                    formatPercentage(data.getRoaTTM() != null ? data.getRoaTTM() / 100 : null),
//                    formatDouble(data.getPriceAt930()),
//                    formatDouble(data.getPriceAt935()),
//                    formatDouble(data.getPriceAt940()),
//                    formatDouble(data.getPriceAt945()),
//                    formatDouble(data.getPriceAt950()),
//                    formatDouble(data.getPriceAt1000()),
//                    formatDouble(data.getPriceAt1100()),
//                    formatDouble(data.getPriceAt1105())
//            ));
//        }
//
//        html.append("</table></body></html>");
//
//        // Add Combined Search Query heading and list with new lines
//        html.append("<p>Combined Search Query:<br>");
//        html.append(combinedEarnings.stream()
//                .map(data -> "\"" + data.getName() + " share price\"")
//                .collect(Collectors.joining("<br>")));
//        html.append("</p>");
//
//        // Add a new line after Combined Search Query
//        html.append("<br>");
//
//        // Add two new lines and formatted rules
//        html.append("<br>");
//        html.append("<pre style='font-family: Arial, sans-serif; white-space: pre-wrap;'>");
//        html.append("if premarket around 5% & rev%>3.5 and eps % >=21 or eps % > 12 or eps%>=9 and industry not equal to real estate and construction and market cap > 605\n");
//        html.append("if premarket around 5% & rev>8 and eps% > -21  or eps%>1.5% & industry not equal to Metals and mining\n");
//        html.append("if premarket around 5% & rev>-3.70 & eps % around 500% and market cap>500\n");
//        html.append("</pre>");
//
////        String baseDir = System.getProperty("user.home");
////        String filePath = baseDir + "/downloads/demo/StockAnalysisV1.csv";
////        Path path = Path.of(filePath);
////        try {
////            // Create directories if they don't exist
////            Files.createDirectories(path.getParent());
////            // Create the file if it doesn't exist
////            if (!Files.exists(path)) {
////                Files.createFile(path);
////            }
////
////            // Write to CSV file
////            try (FileWriter fileWriter = new FileWriter(path.toFile())) {
////                fileWriter.write(csvContent.toString());
////                System.out.println("CSV file written successfully to: " + filePath);
////            }
////        } catch (IOException e) {
////            System.err.println("Error creating or writing CSV file: " + e.getMessage());
////        }
//        System.out.println("DONE");
//        return html.toString();
//    }

    // Formatting methods duplicated from EarningsService for simplicity
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

    private String truncateString(String value, int maxLength) {
        if (value == null) return "N/A";
        return value.length() > maxLength ? value.substring(0, maxLength - 3) + "..." : value;
    }

    @GetMapping("/dummy")
    public String dummy() {
        String baseDir = System.getProperty("user.home");
        String filePath = baseDir + "/downloads/demo/StockAnalysisV1.csv";
        Path path = Path.of(filePath);
        try {
            // Create directories if they don't exist
            Files.createDirectories(path.getParent());
            // Create the file if it doesn't exist
            if (!Files.exists(path)) {
                Files.createFile(path);
            }

            // Write to CSV file
            try (FileWriter fileWriter = new FileWriter(path.toFile())) {
                fileWriter.write("csvContent".toString());
                System.out.println("CSV file written successfully to: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error creating or writing CSV file: " + e.getMessage());
        }
        return "ok";
    }
}