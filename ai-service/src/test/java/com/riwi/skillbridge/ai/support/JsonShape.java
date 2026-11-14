package com.riwi.skillbridge.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * Compara la <em>forma</em> de dos JSON: mismas claves en cada objeto y mismo tipo JSON en cada valor
 * (string, number, boolean, object, array; null se acepta frente a cualquier tipo). Los arrays se comparan por su
 * primer elemento.
 */
public final class JsonShape {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonShape() {
    }

    public static JsonNode fixture(String classpath) throws IOException {
        try (InputStream in = JsonShape.class.getResourceAsStream(classpath)) {
            if (in == null) throw new IllegalArgumentException("Fixture not found: " + classpath);
            JsonNode node = MAPPER.readTree(in);
            if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                object.remove("_source"); // nota de procedencia del fixture, no forma parte del contrato
            }
            return node;
        }
    }

    public static List<String> differences(JsonNode expected, JsonNode actual) {
        List<String> diffs = new ArrayList<>();
        compare("$", expected, actual, diffs);
        return diffs;
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> diffs) {
        if (expected.isNull() || actual.isNull()) {
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            diffs.add(path + ": expected " + expected.getNodeType() + " but was " + actual.getNodeType());
            return;
        }
        if (expected.isObject()) {
            TreeSet<String> expectedKeys = names(expected);
            TreeSet<String> actualKeys = names(actual);
            if (!expectedKeys.equals(actualKeys)) {
                diffs.add(path + ": keys " + actualKeys + " differ from contract " + expectedKeys);
            }
            for (String key : expectedKeys) {
                if (actual.has(key)) {
                    compare(path + "." + key, expected.get(key), actual.get(key), diffs);
                }
            }
        } else if (expected.isArray() && !expected.isEmpty() && !actual.isEmpty()) {
            compare(path + "[0]", expected.get(0), actual.get(0), diffs);
        }
    }

    private static TreeSet<String> names(JsonNode node) {
        TreeSet<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }
}
