package celestine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Takes the text-only JSON returned by Claude and resolves each "text" field
 * to {start, end} character offsets by searching the original text.
 *
 * FEs are searched within the sentence that contains the target, not from
 * position 0, so multi-sentence input does not confuse the search.
 */
public class OffsetResolver {

    public static String resolve(String claudeJson) {
        JsonObject root = JsonParser.parseString(claudeJson).getAsJsonObject();

        String fullText = root.has("sentence") && !root.get("sentence").isJsonNull()
                ? root.get("sentence").getAsString() : "";

        if (root.has("frames") && root.get("frames").isJsonArray()) {
            JsonArray frames = root.getAsJsonArray("frames");
            for (JsonElement el : frames) {
                resolveFrame(el.getAsJsonObject(), fullText);
            }
            root.add("frames", prioritise(frames));
        }

        return root.toString();
    }

    // Frame ordering by relevance to reporting duties. Telling (the core
    // reporting act) is top priority; any frame not listed sorts after these.
    private static final String[] PRIORITY = {
        "Telling", "Request", "Receiving", "Conditional_scenario",
        "Time_period_of_action", "Time_vector", "Frequency", "Calendric_unit"
    };

    /**
     * Reorders frames by the priority list above (Telling first). The sort is
     * stable, so frames sharing a rank — and any unlisted frames — keep their
     * original relative order.
     */
    private static JsonArray prioritise(JsonArray frames) {
        java.util.List<JsonElement> list = new java.util.ArrayList<>();
        frames.forEach(list::add);
        list.sort(java.util.Comparator.comparingInt(OffsetResolver::rank));
        JsonArray reordered = new JsonArray();
        list.forEach(reordered::add);
        return reordered;
    }

    private static int rank(JsonElement el) {
        if (!el.isJsonObject()) return PRIORITY.length;
        JsonObject obj = el.getAsJsonObject();
        if (!obj.has("frame") || obj.get("frame").isJsonNull()) return PRIORITY.length;
        String name = obj.get("frame").getAsString();
        for (int i = 0; i < PRIORITY.length; i++) {
            if (PRIORITY[i].equals(name)) return i;
        }
        return PRIORITY.length;
    }

    private static void resolveFrame(JsonObject frameObj, String fullText) {
        // Resolve target first — we need its position to constrain FE search
        int sentenceStart = 0;
        int sentenceEnd   = fullText.length();

        if (frameObj.has("target") && !frameObj.get("target").isJsonNull()) {
            String targetText = frameObj.get("target").getAsString();
            int[] offsets = findOffsets(fullText, targetText, 0);

            if (offsets[0] >= 0) {
                // Find the sentence boundaries around the target
                sentenceStart = sentenceStartBefore(fullText, offsets[0]);
                sentenceEnd   = sentenceEndAfter(fullText, offsets[1]);
            }

            JsonObject targetObj = new JsonObject();
            targetObj.addProperty("text", targetText);
            targetObj.addProperty("start", offsets[0]);
            targetObj.addProperty("end", offsets[1]);
            frameObj.add("target", targetObj);
        }

        // Resolve FEs — search only within the sentence that contains the target
        if (frameObj.has("fes") && frameObj.get("fes").isJsonArray()) {
            JsonArray fes = frameObj.getAsJsonArray("fes");
            for (JsonElement el : fes) {
                JsonObject fe = el.getAsJsonObject();
                if (fe.has("incorporated") && fe.get("incorporated").getAsBoolean()) continue;
                if (!fe.has("text") || fe.get("text").isJsonNull()) continue;
                String feText = fe.get("text").getAsString();
                int[] offsets = findOffsets(fullText, feText, sentenceStart);
                // Accept only if found within the same sentence
                if (offsets[0] >= sentenceStart && offsets[1] <= sentenceEnd) {
                    fe.addProperty("start", offsets[0]);
                    fe.addProperty("end", offsets[1]);
                } else {
                    fe.addProperty("start", -1);
                    fe.addProperty("end", -1);
                }
            }
        }
    }

    /**
     * Returns [start, end) of needle in haystack, searching from fromIndex.
     * Falls back to case-insensitive if exact match fails.
     * Returns [-1, -1] if not found.
     */
    private static int[] findOffsets(String haystack, String needle, int fromIndex) {
        if (needle == null || needle.isEmpty()) return new int[]{-1, -1};

        int idx = haystack.indexOf(needle, fromIndex);
        if (idx >= 0) return new int[]{idx, idx + needle.length()};

        idx = haystack.toLowerCase().indexOf(needle.toLowerCase(), fromIndex);
        if (idx >= 0) return new int[]{idx, idx + needle.length()};

        return new int[]{-1, -1};
    }

    /**
     * Finds the start of the sentence containing the character at pos.
     * Sentence boundaries are periods, exclamation marks, or question marks
     * followed by whitespace (or start of string).
     */
    private static int sentenceStartBefore(String text, int pos) {
        for (int i = pos - 1; i >= 1; i--) {
            char c = text.charAt(i - 1);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i))) {
                return i + 1 <= pos ? i + 1 : i;
            }
        }
        return 0;
    }

    /**
     * Finds the end of the sentence containing the character at pos.
     */
    private static int sentenceEndAfter(String text, int pos) {
        for (int i = pos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return text.length();
    }
}
