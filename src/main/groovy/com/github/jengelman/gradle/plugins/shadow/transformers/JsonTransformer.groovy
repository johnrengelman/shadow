package com.github.jengelman.gradle.plugins.shadow.transformers

import org.gradle.api.file.FileTreeElement
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonParser
import com.google.gson.Gson

import org.apache.tools.zip.ZipOutputStream
import org.apache.tools.zip.ZipEntry

/**
 * Merge multiple occurrence of JSON files.
 *
 * @author Logic Fan, extended to process an array of files by Jan-Hendrik Diederich
 */
@CacheableTransformer
class JsonTransformer implements Transformer {
    private static final GSON = new Gson()
    private static final LOGGER = Logging.getLogger(JsonTransformer.class)

    @Optional
    @Input
    List<String> paths

    private Map<String, JsonElement> matchedPath = [:]

    @Override
    boolean canTransformResource(FileTreeElement element) {
        String path = element.relativePath.pathString
        for (p in paths) {
            if (path.equalsIgnoreCase(p)) {
                matchedPath[path] = null
                return true
            }
        }
        return false
    }

    @Override
    void transform(TransformerContext context) {
        String path = context.getPath()
        final JsonElement j
        try {
            j = JsonParser.parseReader(new InputStreamReader(context.is, "UTF-8"))
        } catch (Exception e) {
            throw new RuntimeException("error on processing json", e)
        }

        matchedPath[path] = (matchedPath[path] == null) ? j : mergeJson(matchedPath[path], j)
    }

    @Override
    boolean hasTransformedResource() {
        return !matchedPath.isEmpty()
    }

    @Override
    void modifyOutputStream(ZipOutputStream os, boolean preserveFileTimestamps) {
        if (paths == null) {
            throw new IllegalArgumentException("\"paths\" is null and not set")
        }
        for (Map.Entry<String, JsonElement> entrySet in matchedPath) {
            ZipEntry entry = new ZipEntry(entrySet.key)
            entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
            os.putNextEntry(entry)
            os.write(GSON.toJson(entrySet.value).getBytes())
        }
        matchedPath = [:]
    }

    /**
     * <table>
     *     <tr>
     *         <td>{@code lhs}</td> <td>{@code rhs}</td> <td>{@code return}</td>
     *     </tr>
     *     <tr>
     *         <td>Any</td> <td>{@code JsonNull}</td> <td>{@code lhs}</td>
     *     </tr>
     *     <tr>
     *         <td>{@code JsonNull}</td> <td>Any</td> <td>{@code rhs}</td>
     *     </tr>
     *     <tr>
     *         <td>{@code JsonArray}</td> <td>{@code JsonArray}</td> <td>concatenation</td>
     *     </tr>
     *     <tr>
     *         <td>{@code JsonObject}</td> <td>{@code JsonObject}</td> <td>merge for each key</td>
     *     </tr>
     *     <tr>
     *         <td>{@code JsonPrimitive}</td> <td>{@code JsonPrimitive}</td>
     *         <td>return lhs if {@code lhs.equals(rhs)}, error otherwise</td>
     *     </tr>
     *     <tr>
     *         <td colspan="2">Other</td> <td>error</td>
     *     </tr>
     * </table>
     * @param lhs a {@code JsonElement}
     * @param rhs a {@code JsonElement}
     * @param id used for logging purpose only
     * @return the merged {@code JsonElement}
     */
    private static JsonElement mergeJson(JsonElement lhs, JsonElement rhs, String id = "") {
        if (rhs == null || rhs instanceof JsonNull) {
            return lhs
        } else if (lhs == null || lhs instanceof JsonNull) {
            return rhs
        } else if (lhs instanceof JsonArray && rhs instanceof JsonArray) {
            return mergeJsonArray(lhs as JsonArray, rhs as JsonArray)
        } else if (lhs instanceof JsonObject && rhs instanceof JsonObject) {
            return mergeJsonObject(lhs as JsonObject, rhs as JsonObject, id)
        } else if (lhs instanceof JsonPrimitive && rhs instanceof JsonPrimitive) {
            return mergeJsonPrimitive(lhs as JsonPrimitive, rhs as JsonPrimitive, id)
        } else {
            LOGGER.warn("conflicts for property {} detected, {} & {}",
                    id, lhs.toString(), rhs.toString())
            return lhs
        }
    }

    private static JsonPrimitive mergeJsonPrimitive(JsonPrimitive lhs, JsonPrimitive rhs, String id) {
        // In Groovy, {@code a == b} is equivalent to {@code a.equals(b)}
        if (lhs != rhs) {
            LOGGER.warn("conflicts for property {} detected, {} & {}",
                    id, lhs.toString(), rhs.toString())
        }
        return lhs
    }

    private static JsonObject mergeJsonObject(JsonObject lhs, JsonObject rhs, String id) {
        JsonObject object = new JsonObject()

        Set<String> properties = new HashSet<>()
        properties.addAll(lhs.keySet())
        properties.addAll(rhs.keySet())
        for (String property : properties) {
            object.add(property,
                    mergeJson(lhs.get(property), rhs.get(property), id + ":" + property))
        }

        return object
    }

    private static JsonArray mergeJsonArray(JsonArray lhs, JsonArray rhs) {
        JsonArray array = new JsonArray()

        array.addAll(lhs)
        array.addAll(rhs)

        return array
    }
}