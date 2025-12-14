package aggregation.kemeny;

import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;

import java.util.Arrays;
import java.util.List;

/**
 * Матрица d_{ik} (расстояние между альтернативой и предполагаемым рангом) для медианы Кемени.
 */
public final class DistanceMatrix {
    private final List<Alternative> alternatives;
    private final double[][] distances;

    /**
     * Приватный конструктор: принимает список альтернатив и готовую матрицу.
     */
    private DistanceMatrix(List<Alternative> alternatives, double[][] distances) {
        this.alternatives = List.copyOf(alternatives);
        this.distances = distances;
    }

    /**
     * Строит матрицу по профилю предпочтений, используя взвешенную метрику Хэмминга.
     */
    public static DistanceMatrix fromProfile(PreferenceProfile profile) {
        List<Alternative> alternatives = profile.alternatives();
        int m = alternatives.size();
        double[][] matrix = new double[m][m];

        for (int i = 0; i < m; i++) {
            Alternative alternative = alternatives.get(i);
            for (int k = 0; k < m; k++) {
                double sum = 0.0;
                int rankTarget = k + 1;
                for (RankingEntry entry : profile.entries()) {
                    // Суммируем абсолютное отклонение ранга для каждой ранжировки.
                    int rank = entry.ranking().getRank(alternative);
                    sum += entry.voters() * Math.abs(rankTarget - rank);
                }
                matrix[i][k] = sum;
            }
        }

        return new DistanceMatrix(alternatives, matrix);
    }

    /**
     * Возвращает список альтернатив, соответствующий строкам матрицы.
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
     * Возвращает расстояние для выбранной строки и столбца.
     */
    public double value(int alternativeIndex, int rankIndex) {
        return distances[alternativeIndex][rankIndex];
    }
}

