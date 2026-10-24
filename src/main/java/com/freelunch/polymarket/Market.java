package com.freelunch.polymarket;

import com.freelunch.model.Contract;
import com.freelunch.model.Venue;

public class Market {
    public String id;
    public String question;
    public String outcomes;
    public String outcomePrices;
    public boolean active;
    public boolean closed;

    // Parsed from outcomePrices for O(1) arb checks
    public double yesPrice;
    public double noPrice;

    public void parsePrices() {
        if (outcomePrices == null) return;
        String cleaned = outcomePrices.replace("[", "").replace("]", "").replace("\"", "");
        String[] parts = cleaned.split(",");
        if (parts.length >= 2) {
            yesPrice = Double.parseDouble(parts[0].trim());
            noPrice = Double.parseDouble(parts[1].trim());
        }
    }

    public Contract toContract(String eventTitle) {
        parsePrices();
        return new Contract(Venue.POLYMARKET, id, question, eventTitle, yesPrice, noPrice);
    }
}