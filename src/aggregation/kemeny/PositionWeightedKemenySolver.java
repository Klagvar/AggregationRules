package aggregation.kemeny;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Решает задачу поиска позиционно-взвешенной медианы Кемени.
 * 
 * Отличие от классического метода: матрица стоимостей строится с учётом
 * весовой функции φ(k), которая определяет важность каждой позиции.
 * 
 * При φ(k) = 1 для всех k совпадает с классическим методом Кемени.
 */
public final class PositionWeightedKemenySolver {

    private final PositionWeightFunction weightFunction;

    /**
     * Создаёт солвер с заданной весовой функцией.
     */
    public PositionWeightedKemenySolver(PositionWeightFunction weightFunction) {
        this.weightFunction = weightFunction;
    }

    /**
     * Создаёт солвер с равномерными весами (классический Кемени).
     */
    public PositionWeightedKemenySolver() {
        this(PositionWeightFunction.uniform());
    }

    /**
     * Возвращает оптимальное ранжирование и минимальное расстояние.
     */
    public PositionWeightedKemenyResult solve(PreferenceProfile profile) {
        WeightedDistanceMatrix matrix = WeightedDistanceMatrix.fromProfile(profile, weightFunction);
        double[][] costMatrix = matrix.asArray();
        int[] assignment = HungarianSolver.solve(costMatrix);

        List<Alternative> alternatives = matrix.alternatives();
        Map<Alternative, Double> ranks = new LinkedHashMap<>();
        double totalDistance = 0.0;

        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alternative = alternatives.get(i);
            int position = assignment[i];
            totalDistance += matrix.value(i, position);
            ranks.put(alternative, (double) (position + 1));
        }

        AggregatedRanking ranking = new AggregatedRanking(ranks, AggregatedRanking.Order.ASCENDING);
        return new PositionWeightedKemenyResult(ranking, totalDistance, matrix, weightFunction);
    }

    /**
     * Возвращает используемую весовую функцию.
     */
    public PositionWeightFunction weightFunction() {
        return weightFunction;
    }
}

