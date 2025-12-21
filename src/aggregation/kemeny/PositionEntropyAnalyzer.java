package aggregation.kemeny;

import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Анализатор энтропии согласованности экспертов на каждой позиции.
 * 
 * Для каждой позиции k вычисляет энтропию Шеннона H(k), показывающую
 * насколько эксперты согласны в том, какая альтернатива должна быть на этой позиции.
 * 
 * H(k) = 0 — полное согласие (все поставили одну альтернативу)
 * H(k) = log2(m) — полный разброс (все альтернативы равновероятны)
 */
public final class PositionEntropyAnalyzer {

    private final PreferenceProfile profile;
    private final double[] entropies;
    private final double maxEntropy;

    private PositionEntropyAnalyzer(PreferenceProfile profile, double[] entropies, double maxEntropy) {
        this.profile = profile;
        this.entropies = entropies;
        this.maxEntropy = maxEntropy;
    }

    /**
     * Анализирует профиль предпочтений и вычисляет энтропию на каждой позиции.
     */
    public static PositionEntropyAnalyzer analyze(PreferenceProfile profile) {
        List<Alternative> alternatives = profile.alternatives();
        int m = alternatives.size();
        int totalVoters = profile.entries().stream().mapToInt(RankingEntry::voters).sum();
        
        double[] entropies = new double[m];
        double maxEntropy = 0.0;
        
        // Для каждой позиции k (1..m)
        for (int k = 1; k <= m; k++) {
            // Считаем сколько раз каждая альтернатива встречается на позиции k
            Map<Alternative, Integer> counts = new HashMap<>();
            
            for (RankingEntry entry : profile.entries()) {
                // Найти альтернативу на позиции k в этой ранжировке
                for (Alternative alt : alternatives) {
                    int rank = entry.ranking().getRank(alt);
                    if (rank == k) {
                        counts.merge(alt, entry.voters(), Integer::sum);
                        break;
                    }
                }
            }
            
            // Вычисляем энтропию Шеннона
            double entropy = 0.0;
            for (int count : counts.values()) {
                if (count > 0) {
                    double p = (double) count / totalVoters;
                    entropy -= p * log2(p);
                }
            }
            
            entropies[k - 1] = entropy;
            if (entropy > maxEntropy) {
                maxEntropy = entropy;
            }
        }
        
        return new PositionEntropyAnalyzer(profile, entropies, maxEntropy);
    }

    /**
     * Возвращает энтропию на позиции k (1-indexed).
     */
    public double getEntropy(int position) {
        if (position < 1 || position > entropies.length) {
            throw new IllegalArgumentException("Position out of range: " + position);
        }
        return entropies[position - 1];
    }

    /**
     * Возвращает массив энтропий для всех позиций.
     */
    public double[] getEntropies() {
        return entropies.clone();
    }

    /**
     * Возвращает максимальную энтропию среди всех позиций.
     */
    public double getMaxEntropy() {
        return maxEntropy;
    }

    /**
     * Возвращает теоретически максимальную энтропию (log2(m)).
     */
    public double getTheoreticalMaxEntropy() {
        return log2(profile.alternatives().size());
    }

    /**
     * Создаёт весовую функцию на основе энтропии.
     * 
     * @param mode режим: CONFLICT_FOCUS или CONSENSUS_FOCUS
     */
    public PositionWeightFunction toWeightFunction(AdaptiveWeightMode mode) {
        // Копируем значения для замыкания
        final double[] entropyValues = this.entropies.clone();
        final double maxH = this.maxEntropy;
        
        if (maxH == 0) {
            // Если все энтропии нулевые (полное согласие), возвращаем равномерные веса
            return PositionWeightFunction.uniform();
        }
        
        return switch (mode) {
            case CONFLICT_FOCUS -> (rank, totalAlternatives) -> {
                if (rank < 1 || rank > entropyValues.length) return 0.0;
                return entropyValues[rank - 1] / maxH;
            };
            case CONSENSUS_FOCUS -> (rank, totalAlternatives) -> {
                if (rank < 1 || rank > entropyValues.length) return 0.0;
                return 1.0 - (entropyValues[rank - 1] / maxH);
            };
        };
    }

    /**
     * Выводит отчёт об энтропии на каждой позиции.
     */
    public void printReport() {
        System.out.println("=== Position Entropy Analysis ===");
        System.out.printf("Theoretical max entropy: %.4f (log2(%d))%n", 
                getTheoreticalMaxEntropy(), profile.alternatives().size());
        System.out.printf("Actual max entropy: %.4f%n", maxEntropy);
        System.out.println();
        System.out.println("Position | Entropy | Normalized | Interpretation");
        System.out.println("---------+---------+------------+---------------");
        
        for (int k = 1; k <= entropies.length; k++) {
            double h = entropies[k - 1];
            double normalized = maxEntropy > 0 ? h / maxEntropy : 0;
            String interpretation = interpretEntropy(normalized);
            System.out.printf("   %2d    | %7.4f | %10.4f | %s%n", k, h, normalized, interpretation);
        }
    }

    private String interpretEntropy(double normalized) {
        if (normalized < 0.2) return "High consensus";
        if (normalized < 0.5) return "Moderate consensus";
        if (normalized < 0.8) return "Some disagreement";
        return "High disagreement";
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}

