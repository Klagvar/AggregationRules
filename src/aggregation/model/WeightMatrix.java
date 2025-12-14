package aggregation.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Матрица весов w_{si}, задающая значимость позиции альтернативы в конкретной ранжировке.
 */
public final class WeightMatrix {
    private final List<Map<Alternative, Double>> rows;

    /**
     * Внутренний конструктор: принимает уже провалидированные строки.
     */
    private WeightMatrix(List<Map<Alternative, Double>> rows) {
        this.rows = List.copyOf(rows);
    }

    /**
     * Создаёт матрицу с одинаковыми весами для всех позиций.
     */
    public static WeightMatrix uniform(PreferenceProfile profile, double weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        List<Map<Alternative, Double>> rows = new ArrayList<>();
        for (int i = 0; i < profile.entries().size(); i++) {
            Map<Alternative, Double> row = new LinkedHashMap<>();
            for (Alternative alternative : profile.alternatives()) {
                row.put(alternative, weight);
            }
            rows.add(row);
        }
        return new WeightMatrix(rows);
    }

    /**
     * Возвращает билдер для построчного заполнения весов.
     */
    public static Builder builder(PreferenceProfile profile) {
        return new Builder(profile);
    }

    /**
     * Возвращает строку весов для указанной ранжировки.
     */
    public Map<Alternative, Double> row(int index) {
        return rows.get(index);
    }

    /**
     * Возвращает количество строк (равно числу ранжировок в профиле).
     */
    public int rows() {
        return rows.size();
    }

    public static final class Builder {
        private final PreferenceProfile profile;
        private final List<Map<Alternative, Double>> rows = new ArrayList<>();

        /**
         * Создаёт билдер, привязанный к конкретному профилю (для контроля числа строк).
         */
        private Builder(PreferenceProfile profile) {
            this.profile = Objects.requireNonNull(profile, "profile");
        }

        /**
          * Добавляет строку весов, проверяя наличие всех альтернатив и положительность значений.
          */
        public Builder addRow(Map<Alternative, Double> weights) {
            Objects.requireNonNull(weights, "weights");
            if (!weights.keySet().equals(Set.copyOf(profile.alternatives()))) {
                throw new IllegalArgumentException("Weight row must contain all alternatives from profile");
            }
            if (weights.values().stream().anyMatch(value -> value == null || value <= 0)) {
                throw new IllegalArgumentException("Weights must be positive numbers");
            }
            rows.add(Map.copyOf(weights));
            return this;
        }

        /**
         * Финализирует матрицу, убеждаясь, что строк столько же, сколько ранжировок.
         */
        public WeightMatrix build() {
            if (rows.size() != profile.entries().size()) {
                throw new IllegalStateException("Weight matrix must match number of ranking entries");
            }
            return new WeightMatrix(rows);
        }
    }
}

