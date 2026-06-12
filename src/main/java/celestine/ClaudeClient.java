package celestine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 1024;

    private static final String SYSTEM_PROMPT = """
You are a FrameNet semantic annotator. Given an English sentence, identify the single most dominant frame it evokes from the list below and annotate it.

FRAMES:

1. Conditional_scenario
   A situation where two mutually exclusive possibilities are presented, each with a consequence.
   Core FEs: Profiled_possibility, Opposite_possibility, Consequence, Anti_consequence

2. Request
   A Speaker asks an Addressee or Recipient for something or to carry out some action.
   Core FEs: Speaker, Addressee, Recipient, Message, Message_argument, Medium
   Non-core FEs: Manner, Means, Time, Beneficiary, Topic

3. Time_period_of_action
   Denotes a period of time in which an Action is possible or required, with optional Duration and Agent.
   Core FEs: Action, Agent, Duration
   Non-core FEs: Whole

4. Time_vector
   An Event occurs at a particular Distance and Direction from a Landmark_event. Direction is often incorporated into the lexical unit (e.g. "ago", "before").
   Core FEs: Direction, Distance, Event, Landmark_event

RULES:
- Identify the TARGET: the word or phrase that lexically evokes the frame.
- Identify each Frame Element (FE) by its exact character offsets in the original sentence (0-indexed).
- Only annotate FEs that are explicitly present in the text. Do not infer absent elements.
- Incorporated FEs (encoded in the target word itself, e.g. Direction in "ago") should be listed with "incorporated": true and no offsets.
- Return ONLY valid JSON, no explanation, no markdown fences.

OUTPUT FORMAT:
{
  "sentence": "<original sentence>",
  "frame": "<frame name>",
  "target": { "text": "<target word>", "start": <int>, "end": <int> },
  "fes": [
    { "name": "<FE name>", "text": "<span text>", "start": <int>, "end": <int> },
    { "name": "<FE name>", "incorporated": true }
  ]
}
""";

    private final HttpClient http;
    private final String apiKey;

    public ClaudeClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newHttpClient();
    }

    public String annotate(String sentence) throws Exception {
        String userMessage = "Annotate this sentence: " + sentence;

        String body = """
                {
                  "model": "%s",
                  "max_tokens": %d,
                  "system": %s,
                  "messages": [
                    { "role": "user", "content": %s }
                  ]
                }
                """.formatted(
                MODEL,
                MAX_TOKENS,
                toJsonString(SYSTEM_PROMPT),
                toJsonString(userMessage)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error " + response.statusCode() + ": " + response.body());
        }

        return extractText(response.body());
    }

    // Extracts the text content from Claude's response envelope
    private String extractText(String responseJson) {
        // Minimal extraction: find "text" field in content array
        int idx = responseJson.indexOf("\"text\"");
        if (idx == -1) throw new RuntimeException("Unexpected Claude response: " + responseJson);
        int start = responseJson.indexOf("\"", idx + 7) + 1;
        int end = responseJson.lastIndexOf("\"");
        String raw = responseJson.substring(start, end);
        // Unescape JSON string
        return raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // Escapes a Java string for embedding as a JSON string value
    private String toJsonString(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
