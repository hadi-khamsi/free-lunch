package com.freelunch.matching;

/**
 * The kind of bet a question is. Two questions about the same coin and day can still pay out
 * differently (e.g. "above $X at 5pm" vs "ever hits $X"), so only same-shape questions are paired.
 */
public enum QuestionShape {
    /** Will a project launch a token by a deadline. "Will Rabby launch a token before Jan 1, 2027?" */
    TOKEN_LAUNCH,

    /** Fully diluted valuation after a token launch. "Arc FDV above ___ one day after launch?" */
    FDV,

    /**
     * Does the price ever touch a level before a deadline. Resolves YES the moment it's reached, even if
     * it falls back after. "Will Ethereum reach below $1000 by Jan 1, 2027?", "When will Solana hit $150?"
     */
    PRICE_TOUCH,

    /**
     * The price at one specific moment; only that instant matters.
     * "SOL price on Oct 25, 2026?", "Will BTC trimmed mean be above $90000 by 11:59 PM ET on Oct 31?",
     * "Solana above ___ on October 31?"
     */
    PRICE_AT_TIME,

    /** Anything that fits no shape above (e.g. "Will Satoshi move any Bitcoin by 2027?"). Never paired. */
    OTHER;

    /**
     * Classifies a question by the kind of bet it is, from its wording.
     * Order matters: a token-launch question can also say "price", so the specific shapes are checked first.
     */
    public static QuestionShape of(String question) {
        if (question == null) {
            return OTHER;
        }
        String t = question.toLowerCase().replaceAll("\\s+", " ");
        if (t.contains("launch a token")) {
            return TOKEN_LAUNCH;
        }
        if (t.contains("fdv")) {
            return FDV;
        }
        if (containsAny(t, "hit ", "reach", "all time high", "trade above", "trade below")) {
            return PRICE_TOUCH;
        }
        if (containsAny(t, "price on", "price at", "price range", "price above", "above ___",
                "trimmed mean", " be above", " be below")) {
            return PRICE_AT_TIME;
        }
        return OTHER;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
