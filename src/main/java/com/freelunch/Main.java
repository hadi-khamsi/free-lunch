package com.freelunch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.freelunch.polymarket.Event;
import com.freelunch.matching.ContractMatcher;
import com.freelunch.matching.MatchResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Main {

    static List<com.freelunch.kalshi.Event> fetchKalshiEvents() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/events?limit=200&status=open"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        com.freelunch.kalshi.EventsResponse data = mapper.readValue(resp.body(), com.freelunch.kalshi.EventsResponse.class);

        List<com.freelunch.kalshi.Event> filtered = new ArrayList<>();
        for (com.freelunch.kalshi.Event e : data.events) {
            if (e.category.equals("Politics") || e.category.equals("Elections")) {
                filtered.add(e);
            }
        }
        return filtered;
    }
    static List<com.freelunch.kalshi.Series> fetchCryptoSeries() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/series?category=Crypto"))
            .GET()
            .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    com.freelunch.kalshi.SeriesResponse data = mapper.readValue(resp.body(), com.freelunch.kalshi.SeriesResponse.class);
    return data.series;
}
static com.freelunch.kalshi.MarketsResponse fetchMarketsForSeries(String seriesTicker) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/markets?series_ticker=" + seriesTicker + "&status=open&limit=1000"))
            .GET()
            .build();

    for (int attempt = 1; attempt <= 4; attempt++) {
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            return mapper.readValue(resp.body(), com.freelunch.kalshi.MarketsResponse.class);
        }
        if (resp.statusCode() != 429 || attempt == 4) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " for " + seriesTicker + ": " + resp.body());
        }
        Thread.sleep(300L * attempt);
    }
    throw new RuntimeException("unreachable");
}

    static List<Event> fetchPolymarketEvents() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<Event> allEvents = new ArrayList<>();
        int pageSize = 100;
        int offset = 0;

        while (true) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://gamma-api.polymarket.com/events?tag_slug=crypto&limit=" + pageSize + "&offset=" + offset + "&active=true&closed=false"))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(resp.body());
            if (!root.isArray() || root.isEmpty()) {
                break; // Gamma signals "past the end" with a non-array object, not an empty list
            }
            Event[] page = mapper.treeToValue(root, Event[].class);
            allEvents.addAll(Arrays.asList(page));

            if (page.length < pageSize) {
                break;
            }
            offset += pageSize;
        }

        return allEvents;
    }

    // pulls "unit" out of "Will Unit launch a token before Jan 1, 2027?"
    static String tokenLaunchEntity(String title) {
        String lower = title.toLowerCase();
        int start = lower.indexOf("will ");
        int end = lower.indexOf(" launch a token");
        if (start < 0 || end < 0 || end <= start + 5) return null;
        return lower.substring(start + 5, end).trim();
    }

    // true if the two entity phrases share a word or one contains the other
    // (so "felix" ~ "felix protocol", "fomo" ~ "fomo.family")
    static boolean entitiesOverlap(String a, String b) {
        if (a == null || b == null) return false;
        if (a.contains(b) || b.contains(a)) return true;
        for (String wa : a.split("[^a-z0-9]+")) {
            if (wa.length() < 3) continue;
            for (String wb : b.split("[^a-z0-9]+")) {
                if (wa.equals(wb)) return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
    List<com.freelunch.kalshi.Series> cryptoSeries = fetchCryptoSeries();
    int rawSeriesCount = cryptoSeries.size();
    cryptoSeries.removeIf(s -> s.ticker.endsWith("15M")); // drop rolling 15-min series (KX...15M) -- unmatchable vs Polymarket's fixed-slot windows
    System.out.println("Crypto series: " + cryptoSeries.size() + " (excluded " + (rawSeriesCount - cryptoSeries.size()) + " rolling 15-min)");

    long start = System.currentTimeMillis();
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    Future<List<Event>> polyFuture = executor.submit(() -> fetchPolymarketEvents());

    int batchSize = 10;
    long pauseBetweenBatchesMs = 1500;

    List<com.freelunch.kalshi.Market> allCryptoMarkets = new ArrayList<>();

    for (int i = 0; i < cryptoSeries.size(); i += batchSize) {
        int end = Math.min(i + batchSize, cryptoSeries.size());
        List<com.freelunch.kalshi.Series> batch = cryptoSeries.subList(i, end);

        List<Future<com.freelunch.kalshi.MarketsResponse>> futures = new ArrayList<>();
        for (com.freelunch.kalshi.Series series : batch) {
            futures.add(executor.submit(() -> fetchMarketsForSeries(series.ticker)));
        }

        for (Future<com.freelunch.kalshi.MarketsResponse> future : futures) {
            try {
                com.freelunch.kalshi.MarketsResponse result = future.get();
                if (result.markets != null) {
                    allCryptoMarkets.addAll(result.markets);
                }
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.println("A series fetch failed, skipping: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            }
        }

        if (end < cryptoSeries.size()) {
            Thread.sleep(pauseBetweenBatchesMs);
        }
    }

    List<Event> polyEvents = polyFuture.get();
    int rawPolyCount = polyEvents.size();
    polyEvents.removeIf(e -> e.title != null && e.title.toLowerCase().contains("up or down")); // drop 5-min "Up or Down" markets (~81% of Polymarket crypto)

    List<com.freelunch.polymarket.Market> polyCryptoMarkets = new ArrayList<>();
    for (Event e : polyEvents) {
        if (e.markets != null) {
            polyCryptoMarkets.addAll(e.markets);
        }
    }

    Set<String> seenTitles = new HashSet<>();
    for (com.freelunch.kalshi.Market m : allCryptoMarkets) {
        seenTitles.add(m.title);
    }

    executor.shutdown();

    long elapsed = System.currentTimeMillis() - start;
    System.out.println("Kalshi crypto markets: " + allCryptoMarkets.size() + " (" + seenTitles.size() + " distinct titles)");
    System.out.println("Polymarket crypto events: " + polyEvents.size() + " (excluded " + (rawPolyCount - polyEvents.size()) + " 'Up or Down')");
    System.out.println("Polymarket crypto markets: " + polyCryptoMarkets.size());
    System.out.println("Fetch took: " + elapsed + "ms");
    
    List<String> kalshiTokenTitles = new ArrayList<>();
    for (String t : seenTitles) {
        if (t != null && t.toLowerCase().contains("launch a token")) {
                kalshiTokenTitles.add(t);
        }
    }
    List<String> polyTokenTitles = new ArrayList<>();
    for (Event e : polyEvents) {
        if (e.title != null && e.title.toLowerCase().contains("launch a token")) {
                polyTokenTitles.add(e.title);
                }
    }
    System.out.println("\n'launch a token' cluster: " + kalshiTokenTitles.size() + " Kalshi x " + polyTokenTitles.size() + " Polymarket pairs");
    
    ContractMatcher matcher = new ContractMatcher();
    for (String kTitle : kalshiTokenTitles) {
        String kEnt = tokenLaunchEntity(kTitle);
        for (String pTitle : polyTokenTitles) {
            if (!entitiesOverlap(kEnt, tokenLaunchEntity(pTitle))) continue; // blocking gate -- only LLM same-entity pairs
            MatchResult r = matcher.compare(kTitle, pTitle);
            System.out.println((r.match ? "MATCH(" + r.direction + ")" : "no match") + ": [" + kTitle + "]  <->  [" + pTitle + "]");
        }
    }
    }
}