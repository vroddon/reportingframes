package celestine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class ClaudeClient {

    // Set CELESTINE_DEBUG=1 to log full request/response payloads to stderr.
    private static final boolean DEBUG =
            "1".equals(System.getenv("CELESTINE_DEBUG"))
            || "true".equalsIgnoreCase(System.getenv("CELESTINE_DEBUG"));

    private static final Gson GSON = new Gson();

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 1024;

    private static final String SYSTEM_PROMPT = """
You are a FrameNet semantic annotator. Given an English sentence, identify up to 3 frames it evokes from the list below, ordered by relevance to reporting duties in legal documents. Prioritise frames in this order: Request, Receiving, Conditional_scenario, Time_period_of_action, Time_vector, Frequency, Calendric_unit.

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

5. Receiving
   A Recipient comes into possession of a Theme transferred from a Donor (e.g. an authority receives a report submitted by an entity).
   Core FEs: Donor, Recipient, Theme
   Non-core FEs: Means, Manner, Place, Time, Purpose_of_theme

6. Calendric_unit
   Names a conventional Unit of time within a calendric system (e.g. day, week, month, quarter, year), optionally positioned by Relative_time or within a larger Whole.
   Core FEs: Unit, Name, Relative_time
   Non-core FEs: Whole, Salient_event

7. Frequency
   An Event recurs on a regular basis, characterized by how often it occurs over an Interval (e.g. "quarterly", "annually", "every month").
   Core FEs: Event, Interval
   Non-core FEs: Attribute, Degree, Rate, Salient_entity

RULES:
- Identify the TARGET: the content word or phrase (main verb, noun, adjective, adverb) that lexically evokes the frame. Modal auxiliaries (shall, must, may, should, will) are never targets — look for the main verb they modify.
- Identify each Frame Element (FE) by copying the exact substring as it appears in the sentence. Do not paraphrase.
- Only annotate FEs that are explicitly present in the text. Do not infer absent elements.
- Incorporated FEs (encoded in the target word itself, e.g. Direction in "ago") should be listed with "incorporated": true and no text field.
- If the sentence does not clearly evoke any of the listed frames, return "frame": null and empty arrays.
- Return ONLY valid JSON, no explanation, no markdown fences.

OUTPUT FORMAT:
{
  "sentence": "<original sentence>",
  "frames": [
    {
      "frame": "<frame name>",
      "target": "<target word or phrase>",
      "fes": [
        { "name": "<FE name>", "text": "<exact substring from sentence>" },
        { "name": "<FE name>", "incorporated": true }
      ]
    }
  ]
}

If no frame matches:
{ "sentence": "<original sentence>", "frames": [] }
""";

    private final HttpClient http;
    private final String apiKey;

    public ClaudeClient(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newHttpClient();
    }

    public String annotate(String sentence) throws Exception {
        String userMessage = "Annotate this sentence: " + sentence;

        // Build the request with Gson so all string escaping is handled correctly.
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", userMessage);
        JsonArray messages = new JsonArray();
        messages.add(message);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.addProperty("max_tokens", MAX_TOKENS);
        payload.addProperty("system", SYSTEM_PROMPT);
        payload.add("messages", messages);

        String body = GSON.toJson(payload);
        if (DEBUG) System.err.println("[ClaudeClient] request body:\n" + body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (DEBUG) {
            System.err.println("[ClaudeClient] HTTP " + response.statusCode());
            System.err.println("[ClaudeClient] response body:\n" + response.body());
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API error " + response.statusCode() + ": " + response.body());
        }

        return extractText(response.body());
    }

    // Extracts and concatenates the text blocks from Claude's response envelope.
    private String extractText(String responseJson) {
        JsonObject root;
        try {
            root = JsonParser.parseString(responseJson).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new RuntimeException("Could not parse Claude response as JSON: " + responseJson, e);
        }

        // Surface an API-level error object if present.
        if (root.has("error")) {
            throw new RuntimeException("Claude API error: " + root.get("error"));
        }

        JsonArray content = root.getAsJsonArray("content");
        if (content == null) {
            throw new RuntimeException("Claude response has no 'content' array: " + responseJson);
        }

        StringBuilder text = new StringBuilder();
        for (JsonElement element : content) {
            JsonObject block = element.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString())) {
                text.append(block.get("text").getAsString());
            }
        }

        return stripCodeFences(text.toString().trim());
    }

    // Defensive: if the model wraps its JSON in a ```json ... ``` fence, unwrap it.
    private String stripCodeFences(String s) {
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return s.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return s;
    }
}
