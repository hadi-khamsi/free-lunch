package com.freelunch.kalshi;

public class Market {
    public String ticker;
    public String event_ticker;
    public String title;
    public String status;
    public String yes_bid_dollars;
    public String yes_ask_dollars;
    public String no_bid_dollars;
    public String no_ask_dollars;
    public String volume_24h_fp;
    public String expiration_time;
    public String yes_sub_title;

    // Parsed for O(1) arb checks
    public double yesBid;
    public double yesAsk;
    public double noBid;
    public double noAsk;

    public void parsePrices() {
        yesBid = parseOrZero(yes_bid_dollars);
        yesAsk = parseOrZero(yes_ask_dollars);
        noBid = parseOrZero(no_bid_dollars);
        noAsk = parseOrZero(no_ask_dollars);
    }

    private double parseOrZero(String val) {
        if (val == null || val.isEmpty()) return 0.0;
        return Double.parseDouble(val);
    }
}