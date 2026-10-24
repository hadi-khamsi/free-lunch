package com.freelunch.polymarket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelunch.model.Contract;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fetches active crypto events (and their nested markets) from Polymarket's Gamma API.
 *
 * How Polymarket differs from Kalshi:
 * - Only 2 levels: Event -> Market, and markets come nested inside each event, so one call returns both.
 * - Filtering is server-side (tag_slug=crypto), so we page through /events with limit/offset instead of fanning out.
 * - Responses are bare JSON arrays (no wrapper class); past the last page it returns an object, not [].
 * - Gamma's outcomePrices are indicative only; executable prices come from the separate CLOB API.
 */
public class PolymarketClient {
    private static final String BASE_URL = "https://gamma-api.polymarket.com";
    private static final int PAGE_SIZE = 100;

    private final HttpClient http;
    private final ObjectMapper mapper;

    public PolymarketClient() {
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Returns every market in every active Polymarket crypto event as a Contract,
     * excluding the 5-minute "Up or Down" events. Each Contract's eventTitle is its parent event's title.
     */
    public List<Contract> fetchCryptoContracts() throws IOException, InterruptedException {
        List<Event> allEvents = new ArrayList<>();
        int offset = 0;

        while (true) {
            String url = BASE_URL + "/events?tag_slug=crypto&limit=" + PAGE_SIZE + "&offset=" + offset + "&active=true&closed=false";
            HttpResponse<String> resp = get(url);
            JsonNode root = mapper.readTree(resp.body());
            // Gamma signals "past the end" with a non-array object, not an empty list
            if (!root.isArray() || root.isEmpty()) {
                break;
            }
            Event[] page = mapper.treeToValue(root, Event[].class);
            allEvents.addAll(Arrays.asList(page));

            if (page.length < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        int rawCount = allEvents.size();
        // 5-min "Up or Down" slots can't be time-aligned with Kalshi's rolling windows yet
        allEvents.removeIf(e -> e.title != null && e.title.toLowerCase().contains("up or down"));
        System.out.println("Polymarket crypto events: " + allEvents.size() + " (excluded " + (rawCount - allEvents.size()) + " 'Up or Down')");

        List<Contract> contracts = new ArrayList<>();
        for (Event e : allEvents) {
            if (e.markets == null) {
                continue;
            }
            for (Market m : e.markets) {
                contracts.add(m.toContract(e.title));
            }
        }
        return contracts;
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
