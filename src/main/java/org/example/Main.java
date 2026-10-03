package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elections.kalshi.com/trade-api/v2/markets"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MarketsResponse data = mapper.readValue(response.body(), MarketsResponse.class);

        // Print each market
        for (Market market : data.markets) {
            System.out.println(market.ticker + " | bid: " + market.yes_bid_dollars + " | ask: " + market.yes_ask_dollars);
        }
    }
}