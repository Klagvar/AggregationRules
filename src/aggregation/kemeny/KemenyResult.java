package aggregation.kemeny;

import aggregation.model.AggregatedRanking;

/**
 * Результат расчёта медианы Кемени: ранжировка, минимальное расстояние и матрица дистанций.
 */
public record KemenyResult(AggregatedRanking ranking,
                           double totalDistance,
                           DistanceMatrix distanceMatrix) {
}

