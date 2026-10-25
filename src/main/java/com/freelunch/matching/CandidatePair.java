package com.freelunch.matching;

public class CandidatePair {
    private final String kalshiQuestion;
    private final String polyQuestion;

    public CandidatePair(String kalshiQuestion, String polyQuestion) {
        this.kalshiQuestion = kalshiQuestion;
        this.polyQuestion = polyQuestion;
    }

    public String getKalshiQuestion() {
        return kalshiQuestion;
    }

    public String getPolyQuestion() {
        return polyQuestion;
    }

    @Override
    public String toString() {
        return "[" + kalshiQuestion + "]  <->  [" + polyQuestion + "]";
    }
}
