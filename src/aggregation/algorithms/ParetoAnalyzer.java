package aggregation.algorithms;

import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.RankingEntry;

import java.util.*;

/**
 * Анализатор Парето-оптимальности для ранжировок.
 * 
 * Проверяет есть ли альтернатива, которая доминирует все остальные
 * (т.е. не хуже по всем экспертам и лучше хотя бы по одному).
 * 
 * Если такой нет — задача требует метода агрегации.
 */
public final class ParetoAnalyzer {

    /**
     * Результат Парето-анализа.
     */
    public record ParetoResult(
            Set<Alternative> paretoOptimal,
            boolean hasDominatingAlternative,
            Alternative dominatingAlternative,
            String summary
    ) {
        public void print() {
            System.out.println(summary);
        }
    }

    /**
     * Анализирует профиль предпочтений на Парето-оптимальность.
     */
    public static ParetoResult analyze(PreferenceProfile profile) {
        List<Alternative> alternatives = profile.alternatives();
        List<RankingEntry> entries = profile.entries();
        
        // Строим матрицу рангов: ranks[alt_idx][ranking_idx] = rank
        int numAlts = alternatives.size();
        int numRankings = entries.size();
        int[][] ranks = new int[numAlts][numRankings];
        int[] voterCounts = new int[numRankings];
        
        for (int r = 0; r < numRankings; r++) {
            RankingEntry entry = entries.get(r);
            voterCounts[r] = entry.voters();
            for (int a = 0; a < numAlts; a++) {
                Alternative alt = alternatives.get(a);
                ranks[a][r] = entry.ranking().getRank(alt);
            }
        }
        
        // Проверяем доминирование: A доминирует B если
        // - A не хуже B по всем ранжировкам (rank_A <= rank_B)
        // - A строго лучше B хотя бы по одной (rank_A < rank_B)
        Set<Alternative> paretoOptimal = new LinkedHashSet<>(alternatives);
        Alternative dominating = null;
        
        for (int a = 0; a < numAlts; a++) {
            for (int b = 0; b < numAlts; b++) {
                if (a == b) continue;
                
                if (dominates(ranks[a], ranks[b], voterCounts)) {
                    // A доминирует B -> B не Парето-оптимальна
                    paretoOptimal.remove(alternatives.get(b));
                }
            }
        }
        
        // Проверяем есть ли альтернатива, доминирующая ВСЕ остальные
        for (int a = 0; a < numAlts; a++) {
            boolean dominatesAll = true;
            for (int b = 0; b < numAlts; b++) {
                if (a == b) continue;
                if (!dominates(ranks[a], ranks[b], voterCounts)) {
                    dominatesAll = false;
                    break;
                }
            }
            if (dominatesAll) {
                dominating = alternatives.get(a);
                break;
            }
        }
        
        // Формируем summary
        StringBuilder sb = new StringBuilder();
        sb.append("=== Pareto Analysis ===\n");
        sb.append(String.format("Alternatives: %d, Rankings: %d, Total voters: %d%n",
                numAlts, numRankings, Arrays.stream(voterCounts).sum()));
        sb.append(String.format("Pareto-optimal alternatives: %d%n", paretoOptimal.size()));
        
        if (paretoOptimal.size() <= 10) {
            sb.append("  ");
            paretoOptimal.forEach(alt -> sb.append(alt.name()).append(" "));
            sb.append("\n");
        }
        
        if (dominating != null) {
            sb.append(String.format("%nDominating alternative found: %s%n", dominating.name()));
            sb.append("This alternative is better than all others by all voters.\n");
            sb.append("Aggregation is trivial - just pick the dominating one.\n");
        } else {
            sb.append(String.format("%nNo dominating alternative found.%n"));
            sb.append("Voters disagree - aggregation method is required.\n");
        }
        
        return new ParetoResult(paretoOptimal, dominating != null, dominating, sb.toString());
    }
    
    /**
     * Проверяет доминирует ли A альтернативу B.
     * 
     * A доминирует B если:
     * - A не хуже B по всем ранжировкам (rank_A <= rank_B для всех)
     * - A строго лучше B хотя бы по одной ранжировке (rank_A < rank_B)
     */
    private static boolean dominates(int[] ranksA, int[] ranksB, int[] voterCounts) {
        boolean strictlyBetterSomewhere = false;
        
        for (int r = 0; r < ranksA.length; r++) {
            // Учитываем что у каждой ранжировки может быть много голосов
            if (ranksA[r] > ranksB[r]) {
                // A хуже B хотя бы в одной ранжировке -> не доминирует
                return false;
            }
            if (ranksA[r] < ranksB[r]) {
                strictlyBetterSomewhere = true;
            }
        }
        
        return strictlyBetterSomewhere;
    }
}

