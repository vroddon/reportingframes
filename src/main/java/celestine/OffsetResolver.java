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
            // Per-target cursor: repeated identical target words resolve to
            // successive occurrences instead of all collapsing onto the first.
            java.util.Map<String, Integer> targetCursor = new java.util.HashMap<>();
            for (JsonElement el : frames) {
                resolveFrame(el.getAsJsonObject(), fullText, targetCursor);
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

    private static void resolveFrame(JsonObject frameObj, String fullText,
                                     java.util.Map<String, Integer> targetCursor) {
        // Resolve target first — we need its position to constrain and anchor FE search
        int sentenceStart = 0;
        int sentenceEnd   = fullText.length();
        int targetStart   = -1;
        int targetEnd     = -1;

        if (frameObj.has("target") && !frameObj.get("target").isJsonNull()) {
            String targetText = frameObj.get("target").getAsString();
            int from = targetCursor.getOrDefault(targetText, 0);
            int[] offsets = findOffsets(fullText, targetText, from);

            if (offsets[0] >= 0) {
                targetStart   = offsets[0];
                targetEnd     = offsets[1];
                // Find the sentence boundaries around the target
                sentenceStart = sentenceStartBefore(fullText, offsets[0]);
                sentenceEnd   = sentenceEndAfter(fullText, offsets[1]);
                // Advance the cursor so the next frame with the same target word
                // resolves to the following occurrence, not this one again.
                targetCursor.put(targetText, offsets[1]);
            }

            JsonObject targetObj = new JsonObject();
            targetObj.addProperty("text", targetText);
            targetObj.addProperty("start", offsets[0]);
            targetObj.addProperty("end", offsets[1]);
            frameObj.add("target", targetObj);
        }

        // Resolve FEs within the target's sentence, choosing the occurrence
        // nearest the target (and preferring whole-word matches). This stops a
        // short filler such as "it" from binding to an earlier, unrelated "it".
        if (frameObj.has("fes") && frameObj.get("fes").isJsonArray()) {
            JsonArray fes = frameObj.getAsJsonArray("fes");
            for (JsonElement el : fes) {
                JsonObject fe = el.getAsJsonObject();
                if (fe.has("incorporated") && fe.get("incorporated").getAsBoolean()) continue;
                if (!fe.has("text") || fe.get("text").isJsonNull()) continue;
                String feText = fe.get("text").getAsString();
                int[] offsets = findNearest(fullText, feText, sentenceStart, sentenceEnd,
                                            targetStart, targetEnd);
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
     * Finds the occurrence of needle within [regionStart, regionEnd) that lies
     * closest to the target span [targetStart, targetEnd), preferring whole-word
     * matches. Tries an exact match first, then a case-insensitive one. Returns
     * [-1, -1] if not found in the region.
     */
    private static int[] findNearest(String text, String needle,
                                     int regionStart, int regionEnd,
                                     int targetStart, int targetEnd) {
        if (needle == null || needle.isEmpty()) return new int[]{-1, -1};
        int[] hit = bestOccurrence(text, needle, false, regionStart, regionEnd, targetStart, targetEnd);
        if (hit[0] >= 0) return hit;
        return bestOccurrence(text, needle, true, regionStart, regionEnd, targetStart, targetEnd);
    }

    private static int[] bestOccurrence(String text, String needle, boolean caseInsensitive,
                                        int regionStart, int regionEnd,
                                        int targetStart, int targetEnd) {
        String hay = caseInsensitive ? text.toLowerCase() : text;
        String ndl = caseInsensitive ? needle.toLowerCase() : needle;
        int len = ndl.length();
        int from = Math.max(0, regionStart);

        int  bestStart    = -1;
        int  bestBoundary = Integer.MAX_VALUE; // 0 = whole-word match (preferred)
        long bestDistance = Long.MAX_VALUE;

        for (int idx = hay.indexOf(ndl, from); idx >= 0 && idx + len <= regionEnd;
             idx = hay.indexOf(ndl, idx + 1)) {
            int  boundary = wholeWord(text, idx, idx + len, needle) ? 0 : 1;
            long distance = spanDistance(idx, idx + len, targetStart, targetEnd, regionStart);
            if (boundary < bestBoundary
                    || (boundary == bestBoundary && distance < bestDistance)) {
                bestBoundary = boundary;
                bestDistance = distance;
                bestStart    = idx;
            }
        }
        return bestStart < 0 ? new int[]{-1, -1} : new int[]{bestStart, bestStart + len};
    }

    // Distance between a candidate span and the target span; 0 if they overlap.
    // With no resolved target, falls back to preferring the earliest occurrence.
    private static long spanDistance(int start, int end, int targetStart, int targetEnd, int regionStart) {
        if (targetStart < 0) return start - regionStart;
        if (end <= targetStart) return targetStart - end;
        if (start >= targetEnd) return start - targetEnd;
        return 0;
    }

    // True if the match sits on word boundaries — only enforced on the sides
    // where the needle itself begins/ends with a word character.
    private static boolean wholeWord(String text, int start, int end, String needle) {
        boolean leftWord  = Character.isLetterOrDigit(needle.charAt(0));
        boolean rightWord = Character.isLetterOrDigit(needle.charAt(needle.length() - 1));
        boolean leftOk  = !leftWord  || start == 0             || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean rightOk = !rightWord || end   == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return leftOk && rightOk;
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
