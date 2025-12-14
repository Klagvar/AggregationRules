package aggregation;

import aggregation.algorithms.RankSumAggregator;
import aggregation.algorithms.UtilityAggregator;
import aggregation.algorithms.WeightedRankSumAggregator;
import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.kemeny.KemenyMedianSolver;
import aggregation.kemeny.KemenyResult;
import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.nio.file.Path;
import java.util.List;

/**
 * Точка входа: демонстрирует все алгоритмы на данных из JSON.
 */
public final class Main {
    /**
     * Утилитарный класс не должен инстанциироваться.
     */
    private Main() {
    }

    /**
     * Загружает профиль, запускает агрегаторы и печатает результаты.
     */
    public static void main(String[] args) {
        // Если путь не передан, используем учебный пример из data/profile_example.json.
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
        dataset.source().ifPresent(source -> System.out.println("Source: " + source));
        RankSumAggregator aggregator = new RankSumAggregator();
        AggregatedRanking result = aggregator.aggregate(profile);

        System.out.println("Rank-sum aggregation (lower is better):");
        result.sortedEntries().forEach(entry ->
                System.out.printf(" - %s : %.0f%n", entry.getKey().name(), entry.getValue())
        );

        dataset.weightMatrix().ifPresentOrElse(
                weightMatrix -> {
                    WeightedRankSumAggregator weightedAggregator = new WeightedRankSumAggregator();
                    AggregatedRanking weightedResult = weightedAggregator.aggregate(profile, weightMatrix);
                    System.out.println("\nWeighted rank-sum aggregation:");
                    weightedResult.sortedEntries().forEach(entry ->
                            System.out.printf(" - %s : %.0f%n", entry.getKey().name(), entry.getValue())
                    );
                },
                () -> System.out.println("\nWeighted rank-sum aggregation: skipped (weights not provided)")
        );

        dataset.utilityProfile().ifPresentOrElse(
                utilityProfile -> {
                    UtilityAggregator utilityAggregator = new UtilityAggregator();
                    AggregatedRanking utilityResult = utilityAggregator.aggregate(utilityProfile);
                    System.out.println("\nUtility aggregation (Harsanyi weighted sum):");
                    utilityResult.sortedEntries().forEach(entry ->
                            System.out.printf(" - %s : %.2f%n", entry.getKey().name(), entry.getValue())
                    );
                },
                () -> System.out.println("\nUtility aggregation: skipped (utility profiles not provided)")
        );

        KemenyMedianSolver kemenySolver = new KemenyMedianSolver();
        KemenyResult kemenyResult = kemenySolver.solve(profile);

        System.out.println("\nKemeny median ranking:");
        kemenyResult.ranking().sortedEntries().forEach(entry ->
                System.out.printf(" - %s : rank %.0f%n", entry.getKey().name(), entry.getValue())
        );
        System.out.printf("Total distance d* = %.0f%n", kemenyResult.totalDistance());
        printDistanceMatrix(kemenyResult);
    }

    /**
     * Выводит матрицу расстояний \(d_{ik}\) для дальнейшего анализа.
     */
    private static void printDistanceMatrix(KemenyResult result) {
        System.out.println("\nDistance matrix d_{ik}:");
        double[][] matrix = result.distanceMatrix().asArray();
        List<Alternative> alternatives = result.distanceMatrix().alternatives();
        System.out.print("      ");
        for (int rank = 1; rank <= alternatives.size(); rank++) {
            System.out.printf("k=%d   ", rank);
        }
        System.out.println();
        for (int i = 0; i < alternatives.size(); i++) {
            System.out.printf("%-5s ", alternatives.get(i).name());
            for (int k = 0; k < alternatives.size(); k++) {
                System.out.printf("%5.0f ", matrix[i][k]);
            }
            System.out.println();
        }
    }
}

