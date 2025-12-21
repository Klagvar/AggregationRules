package aggregation;

import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.kemeny.*;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.nio.file.Path;
import java.util.Map;

/**
 * Демонстрация адаптивного метода Кемени.
 * 
 * Сравнивает результаты классического Кемени, позиционно-взвешенного
 * и адаптивного (с вычислением весов из энтропии).
 */
public final class AdaptiveKemenyDemo {
    
    private AdaptiveKemenyDemo() {}

    public static void main(String[] args) {
        Path datasetPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("data", "profile_example.json");
        
        ProfileDataset dataset;
        try {
            dataset = new JsonProfileReader().read(datasetPath);
        } catch (Exception e) {
            System.err.println("Failed to load dataset: " + e.getMessage());
            return;
        }

        PreferenceProfile profile = dataset.preferenceProfile();
        dataset.title().ifPresent(title -> System.out.println("Dataset: " + title));
        System.out.println();

        // Шаг 1: Анализ энтропии
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              ENTROPY ANALYSIS                                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        PositionEntropyAnalyzer entropyAnalyzer = PositionEntropyAnalyzer.analyze(profile);
        entropyAnalyzer.printReport();
        System.out.println();

        // Шаг 2: Классический Кемени
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              CLASSIC KEMENY                                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        printRanking("Classic Kemeny (φ(k) = 1)", classicResult.ranking().sortedMap(), classicResult.totalDistance());

        // Шаг 3: Позиционно-взвешенный (гиперболический)
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              POSITION-WEIGHTED KEMENY (Hyperbolic)           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        PositionWeightedKemenySolver hyperbolicSolver = new PositionWeightedKemenySolver(PositionWeightFunction.hyperbolic());
        PositionWeightedKemenyResult hyperbolicResult = hyperbolicSolver.solve(profile);
        printRanking("Hyperbolic (φ(k) = 1/k)", hyperbolicResult.ranking().sortedMap(), hyperbolicResult.totalDistance());

        // Шаг 4: Адаптивный — фокус на конфликтах
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              ADAPTIVE KEMENY (Conflict Focus)                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        AdaptiveKemenySolver conflictSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONFLICT_FOCUS);
        AdaptiveKemenyResult conflictResult = conflictSolver.solve(profile);
        printRanking("Conflict Focus (φ(k) = H(k)/H_max)", conflictResult.ranking().sortedMap(), conflictResult.totalWeightedDistance());
        printAdaptiveWeights(entropyAnalyzer, AdaptiveWeightMode.CONFLICT_FOCUS);

        // Шаг 5: Адаптивный — фокус на консенсусе
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              ADAPTIVE KEMENY (Consensus Focus)               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        AdaptiveKemenySolver consensusSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONSENSUS_FOCUS);
        AdaptiveKemenyResult consensusResult = consensusSolver.solve(profile);
        printRanking("Consensus Focus (φ(k) = 1 - H(k)/H_max)", consensusResult.ranking().sortedMap(), consensusResult.totalWeightedDistance());
        printAdaptiveWeights(entropyAnalyzer, AdaptiveWeightMode.CONSENSUS_FOCUS);

        // Шаг 6: Сводная таблица
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              SUMMARY COMPARISON                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        
        printSummary(
                Map.entry("Classic", classicResult.ranking().sortedMap()),
                Map.entry("Hyperbolic", hyperbolicResult.ranking().sortedMap()),
                Map.entry("Conflict", conflictResult.ranking().sortedMap()),
                Map.entry("Consensus", consensusResult.ranking().sortedMap())
        );
    }

    private static void printRanking(String title, Map<Alternative, Double> ranking, double distance) {
        System.out.println("\n" + title);
        System.out.printf("Total weighted distance: %.4f%n", distance);
        System.out.println("Ranking:");
        ranking.forEach((alt, rank) -> System.out.printf("  %2.0f. %s%n", rank, alt.name()));
        System.out.println();
    }

    private static void printAdaptiveWeights(PositionEntropyAnalyzer analyzer, AdaptiveWeightMode mode) {
        PositionWeightFunction wf = analyzer.toWeightFunction(mode);
        int m = analyzer.getEntropies().length;
        
        System.out.print("Computed weights: ");
        for (int k = 1; k <= m; k++) {
            System.out.printf("φ(%d)=%.3f ", k, wf.weight(k, m));
        }
        System.out.println("\n");
    }

    @SafeVarargs
    private static void printSummary(Map.Entry<String, Map<Alternative, Double>>... results) {
        if (results.length == 0) return;

        var alternatives = results[0].getValue().keySet().stream().toList();

        // Header
        System.out.printf("%-12s", "Method");
        for (Alternative alt : alternatives) {
            System.out.printf("%-8s", alt.name().length() > 7 ? alt.name().substring(0, 7) : alt.name());
        }
        System.out.println();
        System.out.println("-".repeat(12 + alternatives.size() * 8));

        // Rows
        for (var entry : results) {
            System.out.printf("%-12s", entry.getKey());
            for (Alternative alt : alternatives) {
                System.out.printf("%-8.0f", entry.getValue().get(alt));
            }
            System.out.println();
        }
        System.out.println();
    }
}

