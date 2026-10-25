package com.freelunch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EntityBlocker {
    public List<CandidatePair> candidatePairs(Set<String> kalshiQuestions, Set<String> polyQuestions) {
        List<CandidatePair> pairs = new ArrayList<>();
        for (String k : kalshiQuestions) {
            String kEntity = tokenLaunchEntity(k);
            if (kEntity == null) {
                continue;
            }
            for (String p : polyQuestions) {
                if (entitiesOverlap(kEntity, tokenLaunchEntity(p))) {
                    pairs.add(new CandidatePair(k, p));
                }
            }
        }
        return pairs;
    }

    private boolean entitiesOverlap(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        for (String wa : a.split("[^a-z0-9]+")) {
            if (wa.length() < 3) {
                continue;
            }
            for (String wb : b.split("[^a-z0-9]+")) {
                if (wa.equals(wb)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String tokenLaunchEntity(String title) {
        if (title == null) {
            return null;
        }
        String lower = title.toLowerCase();
        int start = lower.indexOf("will ");
        int end = lower.indexOf(" launch a token");
        if (start < 0 || end < 0 || end <= start + 5) {
            return null;
        }
        return lower.substring(start + 5, end).trim();
    }
}
