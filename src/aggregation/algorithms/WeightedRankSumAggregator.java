package aggregation.algorithms;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;
import aggregation.model.WeightMatrix;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реализует взвешенную сумму мест: вес задаёт важность позиции альтернативы в конкретной ранжировке.
 */
public final class WeightedRankSumAggregator {

    /**
     * Вычисляет сумму весов с учётом числа голосов для каждой ранжировки.
     */
    public AggregatedRanking aggregate(PreferenceProfile profile, WeightMatrix weights) {
        if (weights.rows() != profile.entries().size()) {
            throw new IllegalArgumentException("Weight matrix must have the same number of rows as ranking entries");
        }

        Map<Alternative, Double> scores = new LinkedHashMap<>();
        for (Alternative alternative : profile.alternatives()) {
            scores.put(alternative, 0.0);
        }

        for (int idx = 0; idx < profile.entries().size(); idx++) {
            RankingEntry entry = profile.entries().get(idx);
            Map<Alternative, Double> weightRow = weights.row(idx);
            for (Alternative alternative : profile.alternatives()) {
                double weight = weightRow.getOrDefault(alternative, 1.0);
                double current = scores.get(alternative);
                // Вклад строки = значение веса (важность места) * число голосов за ранжировку.
                double contribution = weight * entry.voters();
                scores.put(alternative, current + contribution);
            }
        }

        return new AggregatedRanking(scores, AggregatedRanking.Order.ASCENDING);
    }
}

