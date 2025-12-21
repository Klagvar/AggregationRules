package aggregation.kemeny;

import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Адаптивный решатель медианы Кемени.
 * 
 * Веса позиций вычисляются автоматически из данных на основе энтропии согласованности
 * экспертов на каждой позиции.
 */
public final class AdaptiveKemenySolver {

    private final AdaptiveWeightMode mode;

    public AdaptiveKemenySolver(AdaptiveWeightMode mode) {
        this.mode = mode;
    }

    /**
     * Решает задачу медианы Кемени с адаптивными весами.
     */
    public AdaptiveKemenyResult solve(PreferenceProfile profile) {
        // Шаг 1: Анализ энтропии
        PositionEntropyAnalyzer entropyAnalyzer = PositionEntropyAnalyzer.analyze(profile);
        
        // Шаг 2: Преобразование энтропии в веса
        PositionWeightFunction weightFunction = entropyAnalyzer.toWeightFunction(mode);
        
        // Шаг 3: Построение взвешенной матрицы и решение
        WeightedDistanceMatrix matrix = WeightedDistanceMatrix.fromProfile(profile, weightFunction);
        double[][] costMatrix = matrix.asArray();
        int[] assignment = HungarianSolver.solve(costMatrix);
        
        // Шаг 4: Формирование результата
        List<Alternative> alternatives = matrix.alternatives();
        Map<Alternative, Double> ranks = new LinkedHashMap<>();
        double totalWeightedDistance = 0.0;
        
        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alternative = alternatives.get(i);
            int position = assignment[i];
            totalWeightedDistance += matrix.value(i, position);
            ranks.put(alternative, (double) (position + 1));
        }
        
        AggregatedRanking ranking = new AggregatedRanking(ranks, AggregatedRanking.Order.ASCENDING);
        
        return new AdaptiveKemenyResult(
                ranking,
                totalWeightedDistance,
                matrix,
                entropyAnalyzer,
                mode
        );
    }

    /**
     * Возвращает режим работы.
     */
    public AdaptiveWeightMode getMode() {
        return mode;
    }
}

