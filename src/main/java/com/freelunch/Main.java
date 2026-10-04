package com.freelunch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.freelunch.kalshi.MarketsResponse;
import com.freelunch.polymarket.Event;
import com.freelunch.matching.ContractMatcher;
import com.freelunch.matching.MatchResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
public class Main {
    public static void main(String[] args) throws Exception {
        ContractMatcher matcher = new ContractMatcher();

        // Test 1: Same contract
        MatchResult r1 = matcher.compare(
                "Will Macron leave office before 2027?",
                "Macron out by 2027?"
        );
        System.out.println("Test 1 - Same event:");
        System.out.println("  Match: " + r1.match + ", Direction: " + r1.direction);

        // Test 2: Inverse contract
        MatchResult r2 = matcher.compare(
                "Will Macron remain president through 2027?",
                "Macron out by 2027?"
        );
        System.out.println("Test 2 - Inverse:");
        System.out.println("  Match: " + r2.match + ", Direction: " + r2.direction);

        // Test 3: Unrelated
        MatchResult r3 = matcher.compare(
                "Will Bitcoin hit $100k?",
                "Macron out by 2027?"
        );
        System.out.println("Test 3 - Unrelated:");
        System.out.println("  Match: " + r3.match + ", Direction: " + r3.direction);
    }
}
//public class Main {
//    public static void main(String[] args) throws Exception {
//        HttpClient client = HttpClient.newHttpClient();
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//
//        // Fetch Kalshi
//        HttpRequest kalshiReq = HttpRequest.newBuilder()
//                .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/markets?limit=100&status=open"))
//                .GET()
//                .build();
//        HttpResponse<String> kalshiResp = client.send(kalshiReq, HttpResponse.BodyHandlers.ofString());
//        MarketsResponse kalshiData = mapper.readValue(kalshiResp.body(), MarketsResponse.class);
//
//        // Fetch Polymarket
//        HttpRequest polyReq = HttpRequest.newBuilder()
//                .uri(URI.create("https://gamma-api.polymarket.com/events?limit=100&active=true&closed=false"))
//                .GET()
//                .build();
//        HttpResponse<String> polyResp = client.send(polyReq, HttpResponse.BodyHandlers.ofString());
//        Event[] polyEvents = mapper.readValue(polyResp.body(), Event[].class);
//
//        System.out.println("Kalshi markets: " + kalshiData.markets.size());
//        System.out.println("Polymarket events: " + polyEvents.length);
//    }
//}
