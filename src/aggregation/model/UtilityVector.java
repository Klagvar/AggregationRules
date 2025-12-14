package aggregation.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Вектор индивидуальных полезностей u^{(s)}(A_i) одного участника.
 */
public final class UtilityVector {
    private final Map<Alternative, Double> utilities;

    /**
     * Создаёт вектор полезностей и валидирует, что значения находятся в диапазоне [0;1].
     */
    public UtilityVector(Map<Alternative, Double> utilities) {
        Objects.requireNonNull(utilities, "utilities");
        if (utilities.isEmpty()) {
            throw new IllegalArgumentException("Utility vector must not be empty");
        }
        if (utilities.values().stream().anyMatch(value -> value == null || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException("Utilities must be within [0, 1]");
        }
        this.utilities = Map.copyOf(utilities);
    }

    /**
     * Возвращает полезность указанной альтернативы.
     */
    public double value(Alternative alternative) {
        Double value = utilities.get(alternative);
        if (value == null) {
            throw new IllegalArgumentException("Alternative " + alternative + " is not present in utility vector");
        }
        return value;
    }

    /**
     * Возвращает копию карты полезностей.
     */
    public Map<Alternative, Double> asMap() {
        return new LinkedHashMap<>(utilities);
    }
}

