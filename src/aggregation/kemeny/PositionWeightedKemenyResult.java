package aggregation.kemeny;

import aggregation.model.AggregatedRanking;

/**
 * Результат позиционно-взвешенной медианы Кемени.
 * Содержит ранжировку, расстояние, матрицу стоимостей и использованную весовую функцию.
 */
public record PositionWeightedKemenyResult(
        AggregatedRanking ranking,
        double totalDistance,
        WeightedDistanceMatrix distanceMatrix,
        PositionWeightFunction weightFunction
) {

    /**
     * Возвращает название весовой функции для вывода.
     */
    public String weightFunctionName() {
        // Попытка определить название по типу
        if (isUniform()) {
            return "Uniform (Classic Kemeny)";
        }
        return "Custom";
    }

    /**
     * Проверяет, является ли это классическим Кемени (все веса = 1).
     */
    private boolean isUniform() {
        double[] weights = distanceMatrix.positionWeights();
        for (double w : weights) {
            if (Math.abs(w - 1.0) > 1e-9) {
                return false;
            }
        }
        return true;
    }
}


