package aggregation.kemeny;

import aggregation.model.AggregatedRanking;

/**
 * Результат работы адаптивного метода Кемени.
 */
public record AdaptiveKemenyResult(
        AggregatedRanking ranking,
        double totalWeightedDistance,
        WeightedDistanceMatrix distanceMatrix,
        PositionEntropyAnalyzer entropyAnalyzer,
        AdaptiveWeightMode mode
) {
}

