package com.freelunch.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityBlocker {

    private static final Map<String, String> COIN_ALIASES = Map.of(
            "btc", "bitcoin",
            "eth", "ethereum",
            "sol", "solana",
            "doge", "dogecoin",
            "zec", "zcash",
            "hype", "hyperliquid",
            "shib", "shiba",
            "strk", "starknet",
            "ada", "cardano",
            "ripple", "xrp"
    );
    
    private static final Set<String> STOP_WORDS = Set.of(
            "will", "the", "what", "when", "which", "how", "and", "for", "with",
            "price", "above", "below", "hit", "reach", "range", "between",
            "launch", "token", "fdv", "one", "day", "days", "after", "before", "within",
            "week", "month", "year", "end", "daily", "weekly", "monthly", "yearly",
            "max", "min", "high", "low", "top", "best", "performing", "directional",
            "coin", "crypto", "market", "listed",
            "jan", "january", "feb", "february", "mar", "march", "apr", "april",
            "may", "jun", "june", "jul", "july", "aug", "august",
            "sep", "sept", "september", "oct", "october",
            "nov", "november", "dec", "december"
    );

    private static final Pattern MONTH_DAY = Pattern.compile(
            "\\b(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august"
                    + "|sep|sept|september|oct|october|nov|november|dec|december)\\.?\\s+(\\d{1,2})\\b");

    public List<CandidatePair> candidatePairs(Set<String> kalshiQuestions, Set<String> polyQuestions) {
        Map<String, Set<String>> polyWords = new HashMap<>();
        Map<String, String> polyDates = new HashMap<>();
        Map<String, QuestionShape> polyShapes = new HashMap<>();
        for (String p : polyQuestions) {
            polyWords.put(p, normalizedWords(p));
            polyDates.put(p, dateKey(p));
            polyShapes.put(p, QuestionShape.of(p));
        }

        List<CandidatePair> pairs = new ArrayList<>();
        for (String k : kalshiQuestions) {
            Set<String> kWords = normalizedWords(k);
            QuestionShape kShape = QuestionShape.of(k);
            if (kWords.isEmpty() || kShape == QuestionShape.OTHER) {
                continue;
            }
            String kDate = dateKey(k);
            for (String p : polyQuestions) {
                if (Collections.disjoint(kWords, polyWords.get(p))) {
                    continue;
                }
                if (!datesCompatible(kDate, polyDates.get(p))) {
                    continue;
                }
                if (kShape != polyShapes.get(p)) {
                    continue;
                }
                pairs.add(new CandidatePair(k, p));
            }
        }
        return pairs;
    }

    private Set<String> normalizedWords(String question) {
        Set<String> words = new HashSet<>();
        if (question == null) {
            return words;
        }
        for (String w : question.toLowerCase().split("[^a-z0-9]+")) {
            if (w.length() < 3 || w.matches("[0-9]+") || STOP_WORDS.contains(w)) {
                continue;
            }
            words.add(COIN_ALIASES.getOrDefault(w, w));
        }
        return words;
    }

    /**
     * Returns the first month-day in the question as e.g. "oct-25", so "Oct 25, 2026" and
     * "October 25" give the same key. Returns null when the question names no specific day.
     */
    private String dateKey(String question) {
        if (question == null) {
            return null;
        }
        Matcher m = MONTH_DAY.matcher(question.toLowerCase());
        if (!m.find()) {
            return null;
        }
        String month = m.group(1).substring(0, 3);
        int day = Integer.parseInt(m.group(2));
        return month + "-" + day;
    }

    /** Two questions can only be the same bet if they don't name different days. */
    private boolean datesCompatible(String a, String b) {
        return a == null || b == null || a.equals(b);
    }
}
