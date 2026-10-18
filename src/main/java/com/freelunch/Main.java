package com.freelunch;

import com.freelunch.kalshi.KalshiClient;
import com.freelunch.polymarket.Event;
import com.freelunch.polymarket.PolymarketClient;
import com.freelunch.matching.ContractMatcher;
import com.freelunch.matching.MatchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

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
        PolymarketClient poly = new PolymarketClient();
        long start = System.currentTimeMillis();

        Future<List<Event>> polyFuture = executor.submit(() -> poly.fetchCryptoEvents());
        List<com.freelunch.kalshi.Market> allCryptoMarkets = kalshi.fetchCryptoMarkets();

        List<Event> polyEvents = polyFuture.get();

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
