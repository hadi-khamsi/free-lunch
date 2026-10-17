package com.freelunch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.freelunch.kalshi.KalshiClient;
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
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        KalshiClient kalshi = new KalshiClient(executor);
        long start = System.currentTimeMillis();

        Future<List<Event>> polyFuture = executor.submit(() -> fetchPolymarketEvents());
        List<com.freelunch.kalshi.Market> allCryptoMarkets = kalshi.fetchCryptoMarkets();

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
