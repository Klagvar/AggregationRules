package aggregation.kemeny;

import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;

import java.util.Arrays;
import java.util.List;

/**
 * Позиционно-взвешенная матрица стоимостей d_{ik} для медианы Кемени.
 * Учитывает функцию весов φ(k) для каждой позиции.
 * 
 * Формула: d_{ik} = Σ g_l · φ(k) · |k - r_{il}|
 */
public final class WeightedDistanceMatrix {
    private final List<Alternative> alternatives;
    private final double[][] distances;
    private final double[] positionWeights;

    private WeightedDistanceMatrix(List<Alternative> alternatives, double[][] distances, double[] positionWeights) {
        this.alternatives = List.copyOf(alternatives);
        this.distances = distances;
        this.positionWeights = positionWeights;
    }

    /**
     * Строит матрицу по профилю предпочтений с заданной весовой функцией.
     *
     * @param profile профиль предпочтений
     * @param weightFunction функция весов позиций φ(k)
     */
    public static WeightedDistanceMatrix fromProfile(PreferenceProfile profile, 
                                                      PositionWeightFunction weightFunction) {
        List<Alternative> alternatives = profile.alternatives();
        int m = alternatives.size();
        double[][] matrix = new double[m][m];
        double[] weights = new double[m];

        // Предвычисляем веса для каждой позиции
        for (int k = 0; k < m; k++) {
            weights[k] = weightFunction.weight(k + 1, m);
        }

        // Строим матрицу стоимостей
        for (int i = 0; i < m; i++) {
            Alternative alternative = alternatives.get(i);
            for (int k = 0; k < m; k++) {
                double sum = 0.0;
                int rankTarget = k + 1;
                double posWeight = weights[k];
                
                for (RankingEntry entry : profile.entries()) {
                    int rank = entry.ranking().getRank(alternative);
                    // Взвешенное расстояние: φ(k) · |k - r_il|
                    sum += entry.voters() * posWeight * Math.abs(rankTarget - rank);
                }
                matrix[i][k] = sum;
            }
        }

        return new WeightedDistanceMatrix(alternatives, matrix, weights);
    }

    /**
     * Строит классическую матрицу (без весов, φ(k) = 1).
     */
    public static WeightedDistanceMatrix fromProfile(PreferenceProfile profile) {
        return fromProfile(profile, PositionWeightFunction.uniform());
    }

    /**
     * Возвращает список альтернатив.
     */
    public List<Alternative> alternatives() {
        return alternatives;
    }

    /**
     * Возвращает копию матрицы расстояний.
     */
    public double[][] asArray() {
        double[][] copy = new double[distances.length][distances.length];
        for (int i = 0; i < distances.length; i++) {
            copy[i] = Arrays.copyOf(distances[i], distances[i].length);
        }
        return copy;
    }

    /**
     * Возвращает расстояние для альтернативы i на позиции k.
     */
    public double value(int alternativeIndex, int rankIndex) {
        return distances[alternativeIndex][rankIndex];
    }

    /**
     * Возвращает веса позиций (для отладки/вывода).
     */
    public double[] positionWeights() {
        return Arrays.copyOf(positionWeights, positionWeights.length);
    }
}

