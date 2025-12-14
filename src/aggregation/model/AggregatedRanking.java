package aggregation.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Хранит вычисленные значения (баллы, полезность и т. п.) для каждой альтернативы и порядок сортировки.
 */
public final class AggregatedRanking {
    public enum Order {
        ASCENDING,
        DESCENDING
    }

    private final Map<Alternative, Double> scores;
    private final Order order;

    /**
     * Создаёт агрегированное ранжирование.
     *
     * @param scores карта альтернатив и их итоговых значений
     * @param order  направление сортировки (по возрастанию или по убыванию)
     */
    public AggregatedRanking(Map<Alternative, Double> scores, Order order) {
        if (scores == null || scores.isEmpty()) {
            throw new IllegalArgumentException("Scores map must not be empty");
        }
        this.scores = Map.copyOf(scores);
        this.order = Objects.requireNonNull(order, "order");
    }

    /**
     * Возвращает исходные значения без сортировки.
     */
    public Map<Alternative, Double> scores() {
        return scores;
    }

    /**
     * Возвращает направление сортировки.
     */
    public Order order() {
        return order;
    }

    /**
     * Возвращает список записей, упорядоченный по значениям (при равенстве — по имени альтернативы).
     */
    public List<Map.Entry<Alternative, Double>> sortedEntries() {
        Comparator<Map.Entry<Alternative, Double>> comparator = Map.Entry.comparingByValue();
        if (order == Order.DESCENDING) {
            comparator = comparator.reversed();
        }
        return scores.entrySet()
                .stream()
                .sorted(comparator.thenComparing(e -> e.getKey().name()))
                .collect(Collectors.toList());
    }

    /**
     * Возвращает отсортированную карту (LinkedHashMap) для удобного вывода.
     */
    public Map<Alternative, Double> sortedMap() {
        return sortedEntries()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}

