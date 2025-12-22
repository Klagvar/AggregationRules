package aggregation;

import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.kemeny.KemenyMedianSolver;
import aggregation.kemeny.KemenyResult;
import aggregation.kemeny.PositionWeightFunction;
import aggregation.kemeny.PositionWeightedKemenyResult;
import aggregation.kemeny.PositionWeightedKemenySolver;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.nio.file.Path;
import java.util.List;

/**
 * Демонстрация позиционно-взвешенной медианы Кемени.
 * Сравнивает результаты классического метода с различными весовыми функциями.
 */
public final class PositionWeightedDemo {

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

        // 1. Классический Кемени
        System.out.println("=== CLASSIC KEMENY (phi(k) = 1) ===");
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        printResult(classicResult.ranking().sortedEntries(), classicResult.totalDistance());
        printMatrix("Classic", classicResult.distanceMatrix().asArray(), classicResult.distanceMatrix().alternatives());

        // 2. Гиперболическая: phi(k) = 1/k
        System.out.println("\n=== HYPERBOLIC (phi(k) = 1/k) ===");
        runWeighted(profile, PositionWeightFunction.hyperbolic(), "Hyperbolic");

        // 3. Линейная: phi(k) = (m-k+1)/m
        System.out.println("\n=== LINEAR (phi(k) = (m-k+1)/m) ===");
        runWeighted(profile, PositionWeightFunction.linear(), "Linear");

        // 4. Экспоненциальная: phi(k) = e^(-0.5(k-1))
        System.out.println("\n=== EXPONENTIAL (phi(k) = e^(-0.5(k-1))) ===");
        runWeighted(profile, PositionWeightFunction.exponential(0.5), "Exponential");

        // 5. Логарифмическая: phi(k) = 1/log₂(k+1)
        System.out.println("\n=== LOGARITHMIC (phi(k) = 1/log₂(k+1)) ===");
        runWeighted(profile, PositionWeightFunction.logarithmic(), "Logarithmic");

        // 6. Top-2: только первые 2 позиции
        System.out.println("\n=== TOP-2 (phi(k) = 1 if k≤2, else 0) ===");
        runWeighted(profile, PositionWeightFunction.topK(2), "Top-2");

        // Сводная таблица
        printSummary(profile);
    }

    private static void runWeighted(PreferenceProfile profile, PositionWeightFunction fn, String name) {
        PositionWeightedKemenySolver solver = new PositionWeightedKemenySolver(fn);
        PositionWeightedKemenyResult result = solver.solve(profile);
        printResult(result.ranking().sortedEntries(), result.totalDistance());
        printWeights(result.distanceMatrix().positionWeights());
        printMatrix(name, result.distanceMatrix().asArray(), result.distanceMatrix().alternatives());
    }

    private static void printResult(List<java.util.Map.Entry<Alternative, Double>> entries, double distance) {
        System.out.println("Ranking:");
        entries.forEach(e -> 
            System.out.printf("  %s -> rank %.0f%n", e.getKey().name(), e.getValue())
        );
        System.out.printf("Total weighted distance: %.2f%n", distance);
    }

    private static void printWeights(double[] weights) {
        System.out.print("Position weights: ");
        for (int i = 0; i < weights.length; i++) {
            System.out.printf("phi(%d)=%.3f ", i + 1, weights[i]);
        }
        System.out.println();
    }

    private static void printMatrix(String name, double[][] matrix, List<Alternative> alts) {
        System.out.println("Distance matrix:");
        System.out.print("      ");
        for (int k = 1; k <= alts.size(); k++) {
            System.out.printf("k=%-6d", k);
        }
        System.out.println();
        for (int i = 0; i < alts.size(); i++) {
            System.out.printf("%-5s ", alts.get(i).name());
            for (int k = 0; k < alts.size(); k++) {
                System.out.printf("%-8.2f", matrix[i][k]);
            }
            System.out.println();
        }
    }

    private static void printSummary(PreferenceProfile profile) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("SUMMARY: Comparison of Results");
        System.out.println("=".repeat(60));
        
        PositionWeightFunction[] functions = {
            PositionWeightFunction.uniform(),
            PositionWeightFunction.hyperbolic(),
            PositionWeightFunction.linear(),
            PositionWeightFunction.exponential(0.5),
            PositionWeightFunction.logarithmic(),
            PositionWeightFunction.topK(2)
        };
        String[] names = {"Uniform", "Hyperbolic", "Linear", "Exponential", "Logarithmic", "Top-2"};

        System.out.printf("%-15s", "Method");
        for (Alternative alt : profile.alternatives()) {
            System.out.printf("%-8s", alt.name());
        }
        System.out.println("Distance");
        System.out.println("-".repeat(60));

        for (int i = 0; i < functions.length; i++) {
            PositionWeightedKemenySolver solver = new PositionWeightedKemenySolver(functions[i]);
            PositionWeightedKemenyResult result = solver.solve(profile);
            
            System.out.printf("%-15s", names[i]);
            for (Alternative alt : profile.alternatives()) {
                double rank = result.ranking().scores().get(alt);
                System.out.printf("%-8.0f", rank);
            }
            System.out.printf("%.2f%n", result.totalDistance());
        }
    }
}

