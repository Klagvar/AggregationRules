package aggregation.algorithms;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.UtilityProfile;
import aggregation.model.UtilityVector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реализует аддитивную схему Харшаньи: итоговая полезность — взвешенная сумма индивидуальных.
 */
public final class UtilityAggregator {

    /**
     * Возвращает агрегированное ранжирование по убыванию коллективной полезности.
     */
    public AggregatedRanking aggregate(UtilityProfile profile) {
        Map<Alternative, Double> scores = new LinkedHashMap<>();
        for (Alternative alternative : profile.alternatives()) {
            scores.put(alternative, 0.0);
        }

        for (int idx = 0; idx < profile.utilities().size(); idx++) {
            UtilityVector vector = profile.utilities().get(idx);
            double weight = profile.weights().get(idx);
            for (Alternative alternative : profile.alternatives()) {
                double current = scores.get(alternative);
                // Каждая полезность масштабируется весом значимости эксперта.
                double contribution = weight * vector.value(alternative);
                scores.put(alternative, current + contribution);
            }
        }

        return new AggregatedRanking(scores, AggregatedRanking.Order.DESCENDING);
    }
}

