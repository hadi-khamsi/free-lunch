package com.freelunch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/markets?event_ticker=KXNFLCAREERPASSYDS-PMAHOMES&limit=100"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MarketsResponse data = mapper.readValue(response.body(), MarketsResponse.class);

        // Print each market
        for (Market market : data.markets) {
            // Skip combo/cross-category markets
            if (market.event_ticker != null && market.event_ticker.contains("CROSSCATEGORY")) {
                continue;
            }
            System.out.println(market.event_ticker + " | " + market.title + " | bid: " + market.yes_bid_dollars + " | ask: " + market.yes_ask_dollars);
        }

    }

}