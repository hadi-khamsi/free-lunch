package com.freelunch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.freelunch.kalshi.MarketsResponse;
import com.freelunch.polymarket.Event;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    static MarketsResponse fetchKalshiMarkets() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/markets?limit=100&status=open"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(resp.body(), MarketsResponse.class);
    }

    static Event[] fetchPolymarketEvents() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://gamma-api.polymarket.com/events?limit=100&active=true&closed=false"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req,HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(resp.body(), Event[].class);
    }

    public static void main(String[] args) throws Exception {
    long start = System.currentTimeMillis();

    // one virtual thread per task, no pool size to tune like in Java 17: Executors.newFixedThreadPool(2);
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); 

    Future<MarketsResponse> kalshiFuture = executor.submit(() -> fetchKalshiMarkets());
    Future<Event[]> polyFuture = executor.submit(() -> fetchPolymarketEvents());

    // Both tasks are already running concurrently on pool threads by now;
    // .get() just blocks until each one's result is ready.
    MarketsResponse kalshiData = kalshiFuture.get();
    Event[] polyEvents = polyFuture.get();

    executor.shutdown(); // pool threads are non-daemon -- JVM won't exit after main() without

    long elapsed = System.currentTimeMillis() - start;

    System.out.println("Kalshi markets: " + kalshiData.markets.size());
    System.out.println("Polymarket events: " + polyEvents.length);
    System.out.println("Concurrent fetch took: " + elapsed + "ms");
    }
}