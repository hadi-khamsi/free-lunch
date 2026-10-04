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
                Compare these two prediction market contracts. Respond with JSON only.
                
                Contract A: %s
                Contract B: %s
                
                Respond in this exact format:
                {"match": true/false, "direction": "same"/"inverse"/"none", "confidence": 0.0-1.0}
                
                - "same" = both YES outcomes mean the same thing
                - "inverse" = A's YES is B's NO
                - "none" = unrelated contracts
                """, c1, c2);
    }

    private String callGroq(String prompt) throws Exception {
        String body = """
                {
                    "model": "openai/gpt-oss-20b",
                    "messages": [{"role": "user", "content": "%s"}],
                    "temperature": 0
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
