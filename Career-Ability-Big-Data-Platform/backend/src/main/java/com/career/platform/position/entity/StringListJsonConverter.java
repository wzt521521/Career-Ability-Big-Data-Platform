package com.career.platform.position.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<List<String>>() { };

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化 JSON 数组", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(value, TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法解析 JSON 数组", exception);
        }
    }
}
