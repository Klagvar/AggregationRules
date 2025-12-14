package aggregation.algorithms;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реализует правило суммы мест (Гудман — Марковиц): чем меньше сумма рангов, тем лучше альтернатива.
 */
public final class RankSumAggregator {

    /**
     * Складывает ранги всех ранжировок с учётом численности голосов.
     */
    public AggregatedRanking aggregate(PreferenceProfile profile) {
        Map<Alternative, Double> scores = new LinkedHashMap<>();
        for (Alternative alternative : profile.alternatives()) {
            scores.put(alternative, 0.0);
        }

        for (RankingEntry entry : profile.entries()) {
            for (Alternative alternative : profile.alternatives()) {
                // Каждое место влияет пропорционально числу голосов за данное упорядочение.
                double current = scores.getOrDefault(alternative, 0.0);
                double contribution = entry.voters() * entry.ranking().getRank(alternative);
                scores.put(alternative, current + contribution);
            }
        }

        return new AggregatedRanking(scores, AggregatedRanking.Order.ASCENDING);
    }
}

