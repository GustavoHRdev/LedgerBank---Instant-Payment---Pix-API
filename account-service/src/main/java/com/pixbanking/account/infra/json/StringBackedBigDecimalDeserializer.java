package com.pixbanking.account.infra.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.math.BigDecimal;

public class StringBackedBigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw InvalidFormatException.from(
                    parser,
                    "Amount must be sent as a JSON string",
                    parser.getValueAsString(),
                    BigDecimal.class
            );
        }

        String rawValue = parser.getValueAsString();
        try {
            return new BigDecimal(rawValue);
        } catch (NumberFormatException ex) {
            throw InvalidFormatException.from(parser, "Amount must be a valid decimal string", rawValue, BigDecimal.class);
        }
    }
}
