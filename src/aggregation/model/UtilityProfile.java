package aggregation.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Набор индивидуальных векторов полезности с весами b^{(s)}, используемый в формуле Харшаньи.
 */
public final class UtilityProfile {
    private final List<UtilityVector> utilities;
    private final List<Double> weights;
    private final List<Alternative> alternatives;

    /**
     * Создаёт профиль из уже подготовленных векторов и весов.
     */
    public UtilityProfile(List<UtilityVector> utilities, List<Double> weights) {
        Objects.requireNonNull(utilities, "utilities");
        Objects.requireNonNull(weights, "weights");
        if (utilities.isEmpty()) {
            throw new IllegalArgumentException("Utility profile must have at least one vector");
        }
        if (utilities.size() != weights.size()) {
            throw new IllegalArgumentException("Utilities and weights must have the same size");
        }
        if (weights.stream().anyMatch(weight -> weight == null || weight <= 0.0)) {
            throw new IllegalArgumentException("Weights must be positive");
        }
        this.utilities = List.copyOf(utilities);
        this.weights = List.copyOf(weights);
        this.alternatives = validateAlternatives(utilities);
    }

    /**
     * Проверяет, что все векторы содержат одинаковые альтернативы.
     */
    private List<Alternative> validateAlternatives(List<UtilityVector> utilities) {
        Set<Alternative> baseline = new LinkedHashSet<>(utilities.get(0).asMap().keySet());
        for (UtilityVector vector : utilities) {
            if (!vector.asMap().keySet().equals(baseline)) {
                throw new IllegalArgumentException("All utility vectors must contain the same alternatives");
            }
        }
        return List.copyOf(baseline);
    }

    /**
     * Возвращает список векторов полезностей.
     */
    public List<UtilityVector> utilities() {
        return utilities;
    }

    /**
     * Возвращает веса экспертов.
     */
    public List<Double> weights() {
        return weights;
    }

    /**
     * Возвращает список альтернатив.
     */
    public List<Alternative> alternatives() {
        return alternatives;
    }

    /**
     * Строит профиль по «сырым» таблицам значений (альтернатива -> список полезностей).
     */
    public static UtilityProfile fromRawValues(List<Alternative> alternatives,
                                               Map<Alternative, List<Double>> utilityValues,
                                               List<Double> weights) {
        Objects.requireNonNull(alternatives, "alternatives");
        Objects.requireNonNull(utilityValues, "utilityValues");
        Objects.requireNonNull(weights, "weights");
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Alternatives list must not be empty");
        }
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Weights list must not be empty");
        }

        List<UtilityVector> vectors = new ArrayList<>();
        for (int idx = 0; idx < weights.size(); idx++) {
            Map<Alternative, Double> utilities = new java.util.LinkedHashMap<>();
            for (Alternative alternative : alternatives) {
                List<Double> values = utilityValues.get(alternative);
                if (values == null || idx >= values.size()) {
                    throw new IllegalArgumentException("Missing utility value for alternative "
                            + alternative + " and decision maker #" + idx);
                }
                Double value = values.get(idx);
                if (value == null) {
                    throw new IllegalArgumentException("Utility value must not be null");
                }
                utilities.put(alternative, value);
            }
            vectors.add(new UtilityVector(utilities));
        }
        return new UtilityProfile(vectors, weights);
    }
}

