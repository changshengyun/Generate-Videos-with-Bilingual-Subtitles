package com.example.lyriccaptioner.processing.enhancement

internal class JsonParseException(message: String) : IllegalArgumentException(message)

internal class StrictJsonParser(private val source: String) {
    private var index = 0

    fun parseObjectDocument(): JsonObject = parseDocument().asObject()

    fun parseArrayDocument(): JsonArray = parseDocument().asArray()

    private fun parseDocument(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (index != source.length) throw JsonParseException("Unexpected trailing JSON content")
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= source.length) throw JsonParseException("Unexpected end of JSON")
        return when (source[index]) {
            '{' -> parseMap()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            't' -> consume("true", JsonValue.BooleanValue(true))
            'f' -> consume("false", JsonValue.BooleanValue(false))
            'n' -> consume("null", JsonValue.NullValue)
            else -> parseNumber()
        }
    }

    private fun parseMap(): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        val values = linkedMapOf<String, JsonValue>()
        if (peek('}')) {
            index++
            return JsonValue.ObjectValue(values)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            if (values.containsKey(key)) throw JsonParseException("Duplicate JSON object key")
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            if (peek('}')) {
                index++
                return JsonValue.ObjectValue(values)
            }
            expect(',')
        }
    }

    private fun parseArray(): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (peek(']')) {
            index++
            return JsonValue.ArrayValue(values)
        }
        while (true) {
            values += parseValue()
            skipWhitespace()
            if (peek(']')) {
                index++
                return JsonValue.ArrayValue(values)
            }
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"')
        val output = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '"' -> return output.toString()
                '\\' -> {
                    if (index >= source.length) throw JsonParseException("Invalid JSON escape")
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> output.append(escaped)
                        'b' -> output.append('\b')
                        'f' -> output.append('\u000C')
                        'n' -> output.append('\n')
                        'r' -> output.append('\r')
                        't' -> output.append('\t')
                        'u' -> {
                            if (index + 4 > source.length) throw JsonParseException("Invalid Unicode escape")
                            val codePoint = source.substring(index, index + 4).toIntOrNull(16)
                                ?: throw JsonParseException("Invalid Unicode escape")
                            output.append(codePoint.toChar())
                            index += 4
                        }
                        else -> throw JsonParseException("Invalid JSON escape")
                    }
                }
                in '\u0000'..'\u001F' -> throw JsonParseException("Unescaped JSON control character")
                else -> output.append(character)
            }
        }
        throw JsonParseException("Unterminated JSON string")
    }

    private fun parseNumber(): JsonValue.NumberValue {
        val start = index
        if (peek('-')) index++
        if (index >= source.length) throw JsonParseException("Invalid JSON number")
        if (peek('0')) {
            index++
        } else {
            if (source[index] !in '1'..'9') throw JsonParseException("Invalid JSON number")
            while (index < source.length && source[index].isDigit()) index++
        }
        if (peek('.')) {
            index++
            val fractionStart = index
            while (index < source.length && source[index].isDigit()) index++
            if (fractionStart == index) throw JsonParseException("Invalid JSON number")
        }
        if (index < source.length && source[index] in setOf('e', 'E')) {
            index++
            if (index < source.length && source[index] in setOf('+', '-')) index++
            val exponentStart = index
            while (index < source.length && source[index].isDigit()) index++
            if (exponentStart == index) throw JsonParseException("Invalid JSON number")
        }
        return JsonValue.NumberValue(source.substring(start, index))
    }

    private fun <T : JsonValue> consume(token: String, value: T): T {
        if (!source.startsWith(token, index)) throw JsonParseException("Invalid JSON token")
        index += token.length
        return value
    }

    private fun expect(character: Char) {
        skipWhitespace()
        if (index >= source.length || source[index++] != character) {
            throw JsonParseException("Unexpected JSON token")
        }
    }

    private fun peek(character: Char): Boolean = index < source.length && source[index] == character

    private fun skipWhitespace() {
        while (index < source.length && source[index] in setOf(' ', '\t', '\r', '\n')) index++
    }
}

internal sealed class JsonValue {
    class ObjectValue(val values: Map<String, JsonValue>) : JsonValue()
    class ArrayValue(val values: List<JsonValue>) : JsonValue()
    class StringValue(val value: String) : JsonValue()
    class NumberValue(val value: String) : JsonValue()
    class BooleanValue(val value: Boolean) : JsonValue()
    data object NullValue : JsonValue()

    fun asObject(): ObjectValue = this as? ObjectValue ?: throw JsonParseException("Expected JSON object")
    fun asArray(): ArrayValue = this as? ArrayValue ?: throw JsonParseException("Expected JSON array")
}

internal typealias JsonObject = JsonValue.ObjectValue
internal typealias JsonArray = JsonValue.ArrayValue

internal fun JsonObject.requiredString(key: String): String =
    (values[key] as? JsonValue.StringValue)?.value ?: throw JsonParseException("Expected JSON string")

internal fun JsonObject.optionalString(key: String): String? = when (val value = values[key]) {
    null, JsonValue.NullValue -> null
    is JsonValue.StringValue -> value.value
    else -> throw JsonParseException("Expected optional JSON string")
}

internal fun JsonObject.requiredLong(key: String): Long =
    (values[key] as? JsonValue.NumberValue)?.value?.toLongOrNull()
        ?: throw JsonParseException("Expected JSON integer")

internal fun JsonObject.optionalDouble(key: String): Double? = when (val value = values[key]) {
    null, JsonValue.NullValue -> null
    is JsonValue.NumberValue -> value.value.toDoubleOrNull()
        ?: throw JsonParseException("Expected optional JSON number")
    else -> throw JsonParseException("Expected optional JSON number")
}

internal fun JsonObject.optionalBoolean(key: String): Boolean? = when (val value = values[key]) {
    null, JsonValue.NullValue -> null
    is JsonValue.BooleanValue -> value.value
    else -> throw JsonParseException("Expected optional JSON boolean")
}

internal fun JsonObject.requiredObject(key: String): JsonObject =
    (values[key] as? JsonValue.ObjectValue) ?: throw JsonParseException("Expected JSON object")

internal fun JsonObject.optionalObject(key: String): JsonObject? = when (val value = values[key]) {
    null, JsonValue.NullValue -> null
    is JsonValue.ObjectValue -> value
    else -> throw JsonParseException("Expected optional JSON object")
}

internal fun JsonObject.requiredArray(key: String): JsonArray =
    (values[key] as? JsonValue.ArrayValue) ?: throw JsonParseException("Expected JSON array")

internal fun JsonArray.firstOrThrow(): JsonValue =
    values.firstOrNull() ?: throw JsonParseException("Expected JSON array item")

internal fun encodeJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
        append('"')
    }
    is Boolean, is Number -> value.toString()
    is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
        encodeJson(key.toString()) + ":" + encodeJson(item)
    }
    is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", transform = ::encodeJson)
    else -> throw IllegalArgumentException("Unsupported JSON value")
}
