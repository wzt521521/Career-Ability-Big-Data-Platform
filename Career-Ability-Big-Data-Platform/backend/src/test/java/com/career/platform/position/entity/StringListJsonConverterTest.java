package com.career.platform.position.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringListJsonConverterTest {
    private final StringListJsonConverter converter = new StringListJsonConverter();

    @Test
    void roundTripsUnicodeAndQuotedValues() {
        List<String> input = List.of("Spring Boot", "C#", "数据分析", "say \"hi\"");
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(input)))
                .containsExactlyElementsOf(input);
    }

    @Test
    void handlesNullAndRejectsMalformedJson() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
