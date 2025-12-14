package aggregation.kemeny;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Решает задачу поиска медианы Кемени через сведение к задаче о назначениях.
 */
public final class KemenyMedianSolver {

    /**
     * Возвращает оптимальное ранжирование и минимальное расстояние \(d^*\).
     */
    public KemenyResult solve(PreferenceProfile profile) {
        DistanceMatrix matrix = DistanceMatrix.fromProfile(profile);
        double[][] costMatrix = matrix.asArray();
        int[] assignment = HungarianSolver.solve(costMatrix);

        List<Alternative> alternatives = matrix.alternatives();
        Map<Alternative, Double> ranks = new LinkedHashMap<>();
        double totalDistance = 0.0;
        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alternative = alternatives.get(i);
            int position = assignment[i];
            // Накапливаем суммарное расстояние и записываем позицию альтернативы.
            totalDistance += matrix.value(i, position);
            ranks.put(alternative, (double) (position + 1));
        }

        AggregatedRanking ranking = new AggregatedRanking(ranks, AggregatedRanking.Order.ASCENDING);
        return new KemenyResult(ranking, totalDistance, matrix);
    }
}

