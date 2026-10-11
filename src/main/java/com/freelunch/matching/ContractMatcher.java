package com.freelunch.matching;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ContractMatcher {
    private final HttpClient client;
    private final String apiKey;

    public ContractMatcher() {
        this.client = HttpClient.newHttpClient();
        this.apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null) {
            throw new RuntimeException("GROQ_API_KEY environment variable not set");
        }
    }

    public MatchResult compare(String contract1, String contract2) throws Exception {
        String prompt = buildPrompt(contract1, contract2);
        String response = callGroq(prompt);
        return parseResponse(response);
    }

    private String buildPrompt(String c1, String c2) {
        return String.format("""
                You are matching prediction-market contracts across two venues to find the SAME underlying event.
                Respond with JSON only.

                Contract A: %s
                Contract B: %s

                Judge whether a YES resolution on each refers to the same real-world outcome.
                Ignore superficial wording, and ignore differences in date phrasing or an unspecified placeholder date like "by ___" -- those alone do NOT make them different events.
                - "same" = YES on A means the same outcome as YES on B
                - "inverse" = YES on A means NO on B
                - "none" = unrelated underlying events

                Respond in this exact format:
                {"match": true/false, "direction": "same"/"inverse"/"none", "confidence": 0.0-1.0}
                """, c1, c2);
    }

    private String callGroq(String prompt) throws Exception {
        String body = """
                {
                    "model": "openai/gpt-oss-20b",
                    "messages": [{"role": "user", "content": "%s"}],
                    "temperature": 0,
                    "reasoning_effort": "low"
                }
                """.formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private final ObjectMapper mapper = new ObjectMapper();

    private MatchResult parseResponse(String response) {
        try {
            JsonNode root = mapper.readTree(response);
            String content = root.get("choices").get(0).get("message").get("content").asText();
            JsonNode parsed = mapper.readTree(content);

            boolean match = parsed.get("match").asBoolean();
            String direction = parsed.get("direction").asText();

            return new MatchResult(match, direction);
        } catch (Exception e) {
            return new MatchResult(false, "none");
        }
    }
}
