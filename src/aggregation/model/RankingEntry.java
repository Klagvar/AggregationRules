package aggregation.model;

/**
 * Описание одной индивидуальной ранжировки и числа участников, выбравших её.
 */
public record RankingEntry(Ranking ranking, int voters) {
    /**
     * Проверяет наличие ранжировки и положительного количества голосов.
     */
    public RankingEntry {
        if (ranking == null) {
            throw new IllegalArgumentException("Ranking must be provided");
        }
        if (voters <= 0) {
            throw new IllegalArgumentException("Number of voters must be positive");
        }
    }
}

