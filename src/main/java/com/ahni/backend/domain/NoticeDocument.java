package com.ahni.backend.domain;

import com.ahni.backend.exception.NoticeException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** The shared mobile/web document subset. Storage URLs and arbitrary HTML are never persisted. */
public record NoticeDocument(String json, String text, Set<UUID> imageIds) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static NoticeDocument parse(JsonNode input) {
        if (input == null || input.isNull() || input.toString().length() > 100_000) {
            throw NoticeException.invalidContent();
        }
        var state = new Parser();
        ObjectNode doc = state.node(input, "root", 0);
        String text = state.text.toString().strip();
        if (state.images.size() > 10 || text.length() > 10000 || (text.isBlank() && state.images.isEmpty())) {
            throw NoticeException.invalidContent();
        }
        return new NoticeDocument(doc.toString(), text.isBlank() ? "[사진]" : text, Set.copyOf(state.images));
    }

    private static class Parser {
        private final StringBuilder text = new StringBuilder();
        private final Set<UUID> images = new LinkedHashSet<>();
        private int count;

        private ObjectNode node(JsonNode input, String parent, int depth) {
            if (depth > 16 || ++count > 2000 || !input.isObject()) throw NoticeException.invalidContent();
            String type = input.path("type").asText();
            boolean allowed = switch (parent) {
                case "root" -> type.equals("doc");
                case "doc" -> List.of("paragraph", "heading", "bulletList", "orderedList", "image").contains(type);
                case "bulletList", "orderedList" -> type.equals("listItem");
                case "listItem" -> List.of("paragraph", "bulletList", "orderedList").contains(type);
                case "paragraph", "heading" -> List.of("text", "hardBreak").contains(type);
                default -> false;
            };
            if (!allowed) throw NoticeException.invalidContent();
            ObjectNode result = MAPPER.createObjectNode().put("type", type);
            if (type.equals("text")) {
                if (!input.path("text").isString()) throw NoticeException.invalidContent();
                String value = input.path("text").asText();
                if (value.isEmpty()) throw NoticeException.invalidContent();
                text.append(value);
                result.put("text", value);
                if (input.has("marks")) {
                    if (!input.path("marks").isArray() || input.path("marks").size() > 2) throw NoticeException.invalidContent();
                    var marks = result.putArray("marks");
                    for (var mark : input.path("marks")) {
                        String markType = mark.path("type").asText();
                        if (!List.of("bold", "italic").contains(markType)) throw NoticeException.invalidContent();
                        marks.addObject().put("type", markType);
                    }
                }
            } else if (type.equals("image")) {
                UUID imageId;
                try {
                    imageId = UUID.fromString(input.path("attrs").path("imageId").asText());
                } catch (IllegalArgumentException exception) {
                    throw NoticeException.invalidContent();
                }
                images.add(imageId);
                String alt = input.path("attrs").path("alt").asText("");
                if (alt.length() > 200) throw NoticeException.invalidContent();
                result.putObject("attrs").put("imageId", imageId.toString()).put("alt", alt);
                text.append('\n');
            } else if (type.equals("hardBreak")) {
                text.append('\n');
            } else {
                if (type.equals("heading")) {
                    int level = input.path("attrs").path("level").asInt();
                    if (level != 2 && level != 3) throw NoticeException.invalidContent();
                    result.putObject("attrs").put("level", level);
                }
                if (type.equals("orderedList")) {
                    int start = input.path("attrs").path("start").asInt(1);
                    if (start < 1 || start > 9999) throw NoticeException.invalidContent();
                    result.putObject("attrs").put("start", start);
                }
                var children = input.path("content");
                if (!children.isMissingNode() && !children.isArray()) throw NoticeException.invalidContent();
                var content = result.putArray("content");
                for (var child : children) content.add(node(child, type, depth + 1));
                if ((type.equals("bulletList") || type.equals("orderedList") || type.equals("listItem")) && content.isEmpty()) {
                    throw NoticeException.invalidContent();
                }
                if (type.equals("listItem") && !content.get(0).path("type").asText().equals("paragraph")) {
                    throw NoticeException.invalidContent();
                }
                if (type.equals("paragraph") || type.equals("heading")) text.append('\n');
            }
            if (text.length() > 10001) throw NoticeException.invalidContent();
            return result;
        }
    }
}
