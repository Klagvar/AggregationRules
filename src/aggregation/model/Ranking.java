package aggregation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Строгое ранжирование альтернатив (меньший номер ранга соответствует лучшему месту).
 */
public final class Ranking {
    private final Map<Alternative, Integer> ranks;

    /**
     * Создаёт ранжирование на основе заранее подготовленной мапы рангов.
     *
     * @param ranks соответствие альтернатив и их позиций
     */
    public Ranking(Map<Alternative, Integer> ranks) {
        if (ranks == null || ranks.isEmpty()) {
            throw new IllegalArgumentException("Ranking must contain at least one alternative");
        }
        validateRanks(ranks);
        this.ranks = Collections.unmodifiableMap(new LinkedHashMap<>(ranks));
    }

    /**
     * Строит ранжирование по списку альтернатив в порядке убывания предпочтения.
     *
     * @param order список альтернатив от лучших к худшим
     * @return {@link Ranking} с проставленными рангами, начиная с 1
     */
    public static Ranking fromOrder(List<Alternative> order) {
        Objects.requireNonNull(order, "order");
        if (order.isEmpty()) {
            throw new IllegalArgumentException("Order must be non-empty");
        }
        Map<Alternative, Integer> ranks = new LinkedHashMap<>();
        int rank = 1;
        for (Alternative alternative : order) {
            if (ranks.putIfAbsent(alternative, rank) != null) {
                throw new IllegalArgumentException("Duplicate alternative in ranking: " + alternative);
            }
            rank++;
        }
        return new Ranking(ranks);
    }

    /**
     * Проверяет, что все ранги заданы и являются положительными числами.
     */
    private void validateRanks(Map<Alternative, Integer> ranks) {
        if (ranks.values().stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("Rank values must be positive integers");
        }
    }

    /**
     * Возвращает множество альтернатив, присутствующих в ранжировании.
     */
    public Set<Alternative> alternatives() {
        return ranks.keySet();
    }

    /**
     * Возвращает количество альтернатив.
     */
    public int size() {
        return ranks.size();
    }

    /**
     * Возвращает ранг указанной альтернативы.
     *
     * @throws IllegalArgumentException если альтернативы нет в ранжировании
     */
    public int getRank(Alternative alternative) {
        Integer value = ranks.get(alternative);
        if (value == null) {
            throw new IllegalArgumentException("Alternative " + alternative + " is not present in ranking");
        }
        return value;
    }

    /**
     * Возвращает неизменяемое представление рангов.
     */
    public Map<Alternative, Integer> asMap() {
        return ranks;
    }
}

