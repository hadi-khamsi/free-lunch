package com.freelunch.matching;

public class MatchResult {
    public final boolean match;
    public final String direction;

    public MatchResult(boolean match, String direction) {
        this.match = match;
        this.direction = direction;
    }
}