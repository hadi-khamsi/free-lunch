package com.freelunch;

import com.freelunch.kalshi.KalshiClient;
import com.freelunch.polymarket.PolymarketClient;
import com.freelunch.matching.ContractMatcher;
import com.freelunch.matching.MatchResult;
import com.freelunch.matching.CandidatePair;
import com.freelunch.matching.EntityBlocker;
import com.freelunch.model.Contract;
import com.freelunch.model.Venue;
import com.freelunch.venue.VenueClient;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    static Set<String> distinctEventTitles(List<Contract> contracts) {
        Set<String> titles = new HashSet<>();
        for (Contract c : contracts) {
            titles.add(c.getEventTitle());
        }
        return titles;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<VenueClient> clients = List.of(new KalshiClient(executor), new PolymarketClient());
        long start = System.currentTimeMillis();

        List<Future<List<Contract>>> futures = new ArrayList<>();
        for (VenueClient client : clients) {
            futures.add(executor.submit(() -> client.fetchCryptoContracts()));
        }

        List<Contract> allContracts = new ArrayList<>();
        for (Future<List<Contract>> f : futures) {
            allContracts.addAll(f.get());
        }
        executor.shutdown();

        List<Contract> kalshiContracts = new ArrayList<>();
        List<Contract> polyContracts = new ArrayList<>();
        for (Contract c : allContracts) {
            if (c.getVenue() == Venue.KALSHI) {
                kalshiContracts.add(c);
            } else {
                polyContracts.add(c);
            }
        }

        Set<String> kalshiQuestions = distinctEventTitles(kalshiContracts);
        Set<String> polyQuestions = distinctEventTitles(polyContracts);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Kalshi crypto contracts: " + kalshiContracts.size() + " (" + kalshiQuestions.size() + " distinct questions)");
        System.out.println("Polymarket crypto contracts: " + polyContracts.size() + " (" + polyQuestions.size() + " distinct questions)");
        System.out.println("Fetch took: " + elapsed + "ms");

        EntityBlocker blocker = new EntityBlocker();
        List<CandidatePair> pairs = blocker.candidatePairs(kalshiQuestions, polyQuestions);
        System.out.println("\nCandidate pairs for the LLM: " + pairs.size());

        ContractMatcher matcher = new ContractMatcher();
        for (CandidatePair pair : pairs) {
            MatchResult r = matcher.compare(pair.getKalshiQuestion(), pair.getPolyQuestion());
            System.out.println((r.match ? "MATCH(" + r.direction + ")" : "no match") + ": " + pair);
        }
    }
}
