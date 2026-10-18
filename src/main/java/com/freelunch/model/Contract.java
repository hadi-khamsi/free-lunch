package com.freelunch.model;

public class Contract {
    private final Venue venue;
    private final String venueId;
    private final String title;
    private final double yesAsk;
    private final double noAsk;
    
    public Contract(Venue venue, String venueId, String title, double yesAsk, double noAsk) {
        this.venue = venue;
        this.venueId = venueId;
        this.title = title;
        this.yesAsk = yesAsk;
        this.noAsk = noAsk;
    }
    public Venue getVenue() {
        return venue;
    }
    public String getVenueId() {
        return venueId;
    }

    public String getTitle() {
        return title;
    }

    public double getYesAsk() {
        return yesAsk;
    }

    public double getNoAsk() {
        return noAsk;
    }
    public boolean hasPrices() {
        return yesAsk > 0 && noAsk > 0;
    }
    
    @Override
    public String toString() {
        return venue + " " + venueId + " [" + title + "] YES " + yesAsk + " / NO " + noAsk;
    }
}