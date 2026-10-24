package com.freelunch.kalshi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelunch.model.Contract;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.io.IOException;

/**
 * Fetches open crypto markets from Kalshi's REST API.
 *
 * Kalshi's data model is a 3-level hierarchy:
 *   Series  (a recurring template, e.g. KXBTCD = "Bitcoin price on date X")
 *     -> Event   (one instance of it, e.g. Bitcoin price on Oct 31)
 *       -> Market  (one tradeable YES/NO contract, e.g. "150,000 or above")
 *
 * How that shapes our fetching:
 * - The "Crypto" category lives on /series, not /events, and /markets has no working category filter
 *   (the param is ignored, and the global /markets list is dominated by combo/parlay markets).
 *   So we list crypto series first, then fetch /markets?series_ticker=X once per series.
 * - That fan-out is about 260 requests, and more than ~10-12 at once gets HTTP 429, hence batching + retries.
 * - Markets in one event share an identical title; the real per-contract text is yes_sub_title.
 * - Responses wrap lists in an object ({"series": [...]}, {"markets": [...]}), hence the *Response classes.
 * - Kalshi's yes_bid/yes_ask are real top-of-book prices.
 *
 * See PolymarketClient for how Polymarket's API differs.
 */
public class KalshiClient {
    private static final String BASE_URL = "https://api.elections.kalshi.com/trade-api/v2";
    private static final int BATCH_SIZE = 10;
    private static final long PAUSE_BETWEEN_BATCHES_MS = 1500;
    private static final int MAX_ATTEMPTS = 4;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public KalshiClient(ExecutorService executor) {
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.executor = executor;
    }

    /**
     * Returns every open Kalshi crypto market as a Contract, excluding rolling 15-minute series.
     * Fetches series in batches of BATCH_SIZE with a pause between batches to avoid HTTP 429 rate limits.
     */
    public List<Contract> fetchCryptoContracts() throws IOException, InterruptedException {
        List<Series> series = fetchCryptoSeries();
        int rawCount = series.size();
        // rolling 15-min series can't be time-aligned with Polymarket's fixed slots yet
        series.removeIf(s -> s.ticker.endsWith("15M"));
        System.out.println("Kalshi crypto series: " + series.size() + " (excluded " + (rawCount - series.size()) + " rolling 15-min)");

        List<Market> all = new ArrayList<>();
        for (int i = 0; i < series.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, series.size());

            List<Future<List<Market>>> futures = new ArrayList<>();
            for (Series s : series.subList(i, end)) {
                futures.add(executor.submit(() -> fetchMarketsForSeries(s.ticker)));
            }

            for (Future<List<Market>> f : futures) {
                try {
                    all.addAll(f.get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("Kalshi series fetch failed, skipping: " + cause.getMessage());
                }
            }

            if (end < series.size()) {
                Thread.sleep(PAUSE_BETWEEN_BATCHES_MS);
            }
        }

        List<Contract> contracts = new ArrayList<>();
        for (Market m : all) {
            contracts.add(m.toContract());
        }
        return contracts;
    }

    private List<Series> fetchCryptoSeries() throws IOException, InterruptedException {
    HttpResponse<String> resp = get(BASE_URL + "/series?category=Crypto");
    return mapper.readValue(resp.body(), SeriesResponse.class).series;
    }

    private List<Market> fetchMarketsForSeries(String seriesTicker) throws IOException, InterruptedException {
        // limit=1000: the default (100) would silently truncate big series
        String url = BASE_URL + "/markets?series_ticker=" + seriesTicker + "&status=open&limit=1000";

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpResponse<String> resp = get(url);
            if (resp.statusCode() == 200) {
                MarketsResponse data = mapper.readValue(resp.body(), MarketsResponse.class);
                return data.markets != null ? data.markets : List.of(); // never hand callers a null list
            }
            if (resp.statusCode() != 429 || attempt == MAX_ATTEMPTS) {
                throw new RuntimeException("HTTP " + resp.statusCode() + " for " + seriesTicker + ": " + resp.body());
            }
            Thread.sleep(300L * attempt);
        }
        throw new IllegalStateException("unreachable");
    }

    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}

