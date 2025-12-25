package aggregation;

import aggregation.algorithms.ParetoAnalyzer;
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
import java.util.Map;

/**
 * Демонстрация позиционно-взвешенной медианы Кемени.
 * Сравнивает результаты классического метода с различными весовыми функциями.
 */
public final class PositionWeightedDemo {

    private PositionWeightedDemo() {}

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

        // Шаг 0: Парето-анализ
        printHeader("PARETO ANALYSIS");
        ParetoAnalyzer.ParetoResult paretoResult = ParetoAnalyzer.analyze(profile);
        paretoResult.print();
        System.out.println();

        // Шаг 1: Классический Кемени
        printHeader("CLASSIC KEMENY (phi(k) = 1)");
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        printRanking("Classic Kemeny (phi(k) = 1)", classicResult.ranking().sortedMap(), classicResult.totalDistance());
        printMatrix(classicResult.distanceMatrix().asArray(), classicResult.distanceMatrix().alternatives());

        // Шаг 2: Гиперболическая
        printHeader("HYPERBOLIC (phi(k) = 1/k)");
        PositionWeightedKemenyResult hyperbolicResult = runWeighted(profile, PositionWeightFunction.hyperbolic(), "Hyperbolic (phi(k) = 1/k)");

        // Шаг 3: Линейная
        printHeader("LINEAR (phi(k) = (m-k+1)/m)");
        PositionWeightedKemenyResult linearResult = runWeighted(profile, PositionWeightFunction.linear(), "Linear (phi(k) = (m-k+1)/m)");

        // Шаг 4: Экспоненциальная
        printHeader("EXPONENTIAL (phi(k) = e^(-0.5(k-1)))");
        PositionWeightedKemenyResult expResult = runWeighted(profile, PositionWeightFunction.exponential(0.5), "Exponential (phi(k) = e^(-0.5(k-1)))");

        // Шаг 5: Логарифмическая
        printHeader("LOGARITHMIC (phi(k) = 1/log2(k+1))");
        PositionWeightedKemenyResult logResult = runWeighted(profile, PositionWeightFunction.logarithmic(), "Logarithmic (phi(k) = 1/log2(k+1))");

        // Шаг 6: Top-2
        printHeader("TOP-2 (phi(k) = 1 if k<=2, else 0)");
        PositionWeightedKemenyResult topResult = runWeighted(profile, PositionWeightFunction.topK(2), "Top-2 (phi(k) = 1 if k<=2, else 0)");

        // Шаг 7: Сводная таблица
        printHeader("SUMMARY COMPARISON");
        printSummary(profile, classicResult, hyperbolicResult, linearResult, expResult, logResult, topResult);
    }

    private static void printHeader(String title) {
        System.out.println("+" + "=".repeat(62) + "+");
        System.out.printf("|  %-60s|%n", title);
        System.out.println("+" + "=".repeat(62) + "+");
    }

    private static PositionWeightedKemenyResult runWeighted(PreferenceProfile profile, PositionWeightFunction fn, String name) {
        PositionWeightedKemenySolver solver = new PositionWeightedKemenySolver(fn);
        PositionWeightedKemenyResult result = solver.solve(profile);
        printRanking(name, result.ranking().sortedMap(), result.totalDistance());
        printWeights(result.distanceMatrix().positionWeights());
        printMatrix(result.distanceMatrix().asArray(), result.distanceMatrix().alternatives());
        return result;
    }

    private static void printRanking(String title, Map<Alternative, Double> ranking, double distance) {
        System.out.println("\n" + title);
        System.out.printf("Total weighted distance: %.2f%n", distance);
        System.out.println("Ranking:");
        ranking.forEach((alt, rank) -> System.out.printf("  %2.0f. %s%n", rank, alt.name()));
        System.out.println();
    }

    private static void printWeights(double[] weights) {
        System.out.print("Position weights: ");
        for (int i = 0; i < weights.length; i++) {
            System.out.printf("phi(%d)=%.3f ", i + 1, weights[i]);
        }
        System.out.println();
    }

    private static void printMatrix(double[][] matrix, List<Alternative> alts) {
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
        System.out.println();
    }

    private static void printSummary(PreferenceProfile profile, 
                                     KemenyResult classicResult,
                                     PositionWeightedKemenyResult... results) {
        String[] names = {"Uniform", "Hyperbolic", "Linear", "Exponential", "Logarithmic", "Top-2"};

        System.out.printf("%-15s", "Method");
        for (Alternative alt : profile.alternatives()) {
            String name = alt.name();
            System.out.printf("%-8s", name.length() > 7 ? name.substring(0, 7) : name);
        }
        System.out.println("Distance");
        System.out.println("-".repeat(15 + profile.alternatives().size() * 8 + 10));

        // Classic
        System.out.printf("%-15s", "Uniform");
        for (Alternative alt : profile.alternatives()) {
            double rank = classicResult.ranking().scores().get(alt);
            System.out.printf("%-8.0f", rank);
        }
        System.out.printf("%.2f%n", classicResult.totalDistance());

        // Weighted
        for (int i = 0; i < results.length; i++) {
            System.out.printf("%-15s", names[i + 1]);
            for (Alternative alt : profile.alternatives()) {
                double rank = results[i].ranking().scores().get(alt);
                System.out.printf("%-8.0f", rank);
            }
            System.out.printf("%.2f%n", results[i].totalDistance());
        }
        System.out.println();
    }
}
