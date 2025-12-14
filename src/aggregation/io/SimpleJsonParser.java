package aggregation.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный JSON-парсер под конкретный формат входных данных (объекты, массивы, числа, строки).
 */
final class SimpleJsonParser {
    private final String input;
    private int index = 0;

    /**
     * Принимает исходную строку JSON.
     */
    SimpleJsonParser(String input) {
        this.input = input;
    }

    /**
     * Точка входа: разбирает строку и возвращает корневой объект.
     */
    Object parse() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (!isEnd()) {
            throw error("Unexpected trailing characters");
        }
        return value;
    }

    /**
     * Определяет тип следующего значения и вызывает соответствующий парсер.
     */
    private Object parseValue() {
        skipWhitespace();
        if (isEnd()) {
            throw error("Unexpected end of input");
        }
        char ch = peek();
        return switch (ch) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    /**
     * Разбирает JSON-объект вида { "key": value, ... }.
     */
    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            index++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            result.put(key, value);
            skipWhitespace();
            char ch = nextChar();
            if (ch == '}') {
                break;
            } else if (ch != ',') {
                throw error("Expected ',' or '}' in object");
            }
        }
        return result;
    }

    /**
     * Разбирает массив JSON.
     */
    private List<Object> parseArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            index++;
            return result;
        }
        while (true) {
            result.add(parseValue());
            skipWhitespace();
            char ch = nextChar();
            if (ch == ']') {
                break;
            } else if (ch != ',') {
                throw error("Expected ',' or ']' in array");
            }
        }
        return result;
    }

    /**
     * Разбирает строковый литерал с поддержкой escape-последовательностей.
     */
    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (!isEnd()) {
            char ch = nextChar();
            if (ch == '"') {
                return builder.toString();
            }
            if (ch == '\\') {
                if (isEnd()) {
                    throw error("Incomplete escape sequence");
                }
                char esc = nextChar();
                builder.append(switch (esc) {
                    case '"', '\\', '/' -> esc;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> {
                        String hex = readChars(4);
                        try {
                            yield (char) Integer.parseInt(hex, 16);
                        } catch (NumberFormatException e) {
                            throw error("Invalid unicode escape: " + hex);
                        }
                    }
                    default -> throw error("Invalid escape character: " + esc);
                });
            } else {
                builder.append(ch);
            }
        }
        throw error("Unterminated string literal");
    }

    /**
     * Разбирает число в формате JSON и возвращает значение типа double.
     */
    private Object parseNumber() {
        int start = index;
        if (peek() == '-') {
            index++;
        }
        consumeDigits();
        if (!isEnd() && peek() == '.') {
            index++;
            consumeDigits();
        }
        if (!isEnd() && (peek() == 'e' || peek() == 'E')) {
            index++;
            if (peek() == '+' || peek() == '-') {
                index++;
            }
            consumeDigits();
        }
        double value;
        try {
            value = Double.parseDouble(input.substring(start, index));
        } catch (NumberFormatException ex) {
            throw error("Invalid number literal");
        }
        return value;
    }

    /**
     * Проверяет наличие заданного литерала (true/false/null) и возвращает соответствующее значение.
     */
    private Object parseLiteral(String literal, Object result) {
        if (!input.startsWith(literal, index)) {
            throw error("Expected literal: " + literal);
        }
        index += literal.length();
        return result;
    }

    /**
     * Считывает последовательность цифр, выбрасывая ошибку при отсутствии цифры.
     */
    private void consumeDigits() {
        if (isEnd() || !Character.isDigit(peek())) {
            throw error("Expected digit");
        }
        while (!isEnd() && Character.isDigit(peek())) {
            index++;
        }
    }

    /**
     * Пропускает пробельные символы.
     */
    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(peek())) {
            index++;
        }
    }

    /**
     * Проверяет, что следующий символ совпадает с ожидаемым.
     */
    private void expect(char expected) {
        if (isEnd() || peek() != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    /**
     * Считывает следующий символ, контролируя выход за пределы строки.
     */
    private char nextChar() {
        if (isEnd()) {
            throw error("Unexpected end of input");
        }
        return input.charAt(index++);
    }

    /**
     * Возвращает текущий символ, не смещая позицию.
     */
    private char peek() {
        if (isEnd()) {
            throw error("Unexpected end of input");
        }
        return input.charAt(index);
    }

    /**
     * Считывает указанное количество символов (используется в unicode escape).
     */
    private String readChars(int count) {
        if (index + count > input.length()) {
            throw error("Unexpected end of input");
        }
        String result = input.substring(index, index + count);
        index += count;
        return result;
    }

    /**
     * Проверяет, достигнут ли конец входной строки.
     */
    private boolean isEnd() {
        return index >= input.length();
    }

    /**
     * Формирует исключение с указанием позиции ошибки.
     */
    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + index);
    }
}

