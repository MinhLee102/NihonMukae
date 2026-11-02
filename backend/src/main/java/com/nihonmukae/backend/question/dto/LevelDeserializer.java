package com.nihonmukae.backend.question.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Level viết là số (3) hoặc nhãn quen thuộc ("N3", "n3"). Giá trị có hợp lệ (1 đến 5) hay không do validator kiểm tra. */
public class LevelDeserializer extends JsonDeserializer<Integer> {

    private static final Pattern LABEL = Pattern.compile("^\\s*[Nn]?\\s*(\\d)\\s*$");

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            return p.getIntValue();
        }
        String text = p.getValueAsString();
        Matcher m = text == null ? null : LABEL.matcher(text);
        if (m != null && m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return (Integer) ctxt.handleWeirdStringValue(Integer.class, text, "level phải là số từ 1 đến 5 hoặc dạng N1 đến N5");
    }
}
