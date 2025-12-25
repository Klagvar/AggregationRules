package aggregation;

import aggregation.algorithms.RankSumAggregator;
import aggregation.algorithms.UtilityAggregator;
import aggregation.algorithms.WeightedRankSumAggregator;
import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.UtilityProfile;
import aggregation.model.WeightMatrix;

import java.nio.file.Path;
import java.util.Map;

/**
 * Тесты для проверки корректности агрегаторов.
 * 
 * Каждый тест содержит ручной расчёт в комментариях и сравнивает
 * результат программы с ожидаемым значением.
 * 
 * Данные: data/profile_example.json (учебник Петровского, примеры 21.1 / 22.2)
 */
public final class AggregatorTests {

    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("=" .repeat(70));
        System.out.println("ТЕСТЫ АГРЕГАТОРОВ");
        System.out.println("Данные: data/profile_example.json");
        System.out.println("=" .repeat(70));

        ProfileDataset dataset;
        try {
            dataset = new JsonProfileReader().read(Path.of("data", "profile_example.json"));
        } catch (Exception e) {
            System.err.println("Ошибка загрузки данных: " + e.getMessage());
            return;
        }

        testRankSumAggregator(dataset.preferenceProfile());
        testWeightedRankSumAggregator(dataset);
        testUtilityAggregator(dataset);

        System.out.println("\n" + "=" .repeat(70));
        System.out.println("ИТОГО: " + passedTests + " тестов пройдено, " + failedTests + " провалено");
        System.out.println("=" .repeat(70));
    }

    /**
     * Тест суммы рангов (правило Гудмана-Марковица).
     * 
     * РУЧНОЙ РАСЧЁТ:
     * ─────────────────────────────────────────────────────────────────────
     * Входные данные (5 ранжировок):
     *   1) A > B > C, 23 голоса  ->  A=1, B=2, C=3
     *   2) B > C > A, 17 голосов ->  B=1, C=2, A=3
     *   3) B > A > C,  2 голоса  ->  B=1, A=2, C=3
     *   4) C > A > B, 10 голосов ->  C=1, A=2, B=3
     *   5) C > B > A,  8 голосов ->  C=1, B=2, A=3
     * 
     * Формула: score(X) = SUM (voters * rank_of_X)
     * 
     * A: 23*1 + 17*3 + 2*2 + 10*2 + 8*3 = 23 + 51 + 4 + 20 + 24 = 122
     * B: 23*2 + 17*1 + 2*1 + 10*3 + 8*2 = 46 + 17 + 2 + 30 + 16 = 111
     * C: 23*3 + 17*2 + 2*3 + 10*1 + 8*1 = 69 + 34 + 6 + 10 + 8  = 127
     * 
     * Результат (меньше = лучше): B(111) < A(122) < C(127)
     * Победитель: B
     * ─────────────────────────────────────────────────────────────────────
     */
    private static void testRankSumAggregator(PreferenceProfile profile) {
        System.out.println("\n" + "─".repeat(70));
        System.out.println("ТЕСТ 1: Сумма рангов (RankSumAggregator)");
        System.out.println("─".repeat(70));

        System.out.println("\nРучной расчёт:");
        System.out.println("  A: 23*1 + 17*3 + 2*2 + 10*2 + 8*3 = 23 + 51 + 4 + 20 + 24 = 122");
        System.out.println("  B: 23*2 + 17*1 + 2*1 + 10*3 + 8*2 = 46 + 17 + 2 + 30 + 16 = 111");
        System.out.println("  C: 23*3 + 17*2 + 2*3 + 10*1 + 8*1 = 69 + 34 + 6 + 10 + 8  = 127");
        System.out.println("  Ожидаемый порядок: B(111) < A(122) < C(127)");

        RankSumAggregator aggregator = new RankSumAggregator();
        AggregatedRanking result = aggregator.aggregate(profile);

        System.out.println("\nРезультат программы:");
        for (var entry : result.sortedEntries()) {
            System.out.printf("  %s: %.0f%n", entry.getKey().name(), entry.getValue());
        }

        // Проверки
        Map<Alternative, Double> scores = result.scores();
        boolean passed = true;

        passed &= checkScore("A", scores, 122.0);
        passed &= checkScore("B", scores, 111.0);
        passed &= checkScore("C", scores, 127.0);

        // Проверка порядка
        var sorted = result.sortedEntries();
        passed &= sorted.get(0).getKey().name().equals("B");
        passed &= sorted.get(1).getKey().name().equals("A");
        passed &= sorted.get(2).getKey().name().equals("C");

        printTestResult(passed);
    }

    /**
     * Тест взвешенной суммы рангов.
     * 
     * РУЧНОЙ РАСЧЁТ:
     * ─────────────────────────────────────────────────────────────────────
     * Входные данные (веса из JSON):
     *   1) A=2, B=4, C=5, 23 голоса
     *   2) A=6, B=1, C=3, 17 голосов
     *   3) A=4, B=2, C=7,  2 голоса
     *   4) A=2, B=3, C=1, 10 голосов
     *   5) A=7, B=5, C=3,  8 голосов
     * 
     * Формула: score(X) = SUM (voters * weight_of_X)
     * 
     * A: 2*23 + 6*17 + 4*2 + 2*10 + 7*8 = 46 + 102 + 8 + 20 + 56 = 232
     * B: 4*23 + 1*17 + 2*2 + 3*10 + 5*8 = 92 + 17 + 4 + 30 + 40  = 183
     * C: 5*23 + 3*17 + 7*2 + 1*10 + 3*8 = 115 + 51 + 14 + 10 + 24 = 214
     * 
     * Результат (меньше = лучше): B(183) < C(214) < A(232)
     * Победитель: B
     * ─────────────────────────────────────────────────────────────────────
     */
    private static void testWeightedRankSumAggregator(ProfileDataset dataset) {
        System.out.println("\n" + "─".repeat(70));
        System.out.println("ТЕСТ 2: Взвешенная сумма рангов (WeightedRankSumAggregator)");
        System.out.println("─".repeat(70));

        if (dataset.weightMatrix().isEmpty()) {
            System.out.println("  ПРОПУЩЕН: веса не указаны в данных");
            return;
        }

        System.out.println("\nРучной расчёт:");
        System.out.println("  A: 2*23 + 6*17 + 4*2 + 2*10 + 7*8 = 46 + 102 + 8 + 20 + 56 = 232");
        System.out.println("  B: 4*23 + 1*17 + 2*2 + 3*10 + 5*8 = 92 + 17 + 4 + 30 + 40  = 183");
        System.out.println("  C: 5*23 + 3*17 + 7*2 + 1*10 + 3*8 = 115 + 51 + 14 + 10 + 24 = 214");
        System.out.println("  Ожидаемый порядок: B(183) < C(214) < A(232)");

        WeightedRankSumAggregator aggregator = new WeightedRankSumAggregator();
        WeightMatrix weights = dataset.weightMatrix().get();
        AggregatedRanking result = aggregator.aggregate(dataset.preferenceProfile(), weights);

        System.out.println("\nРезультат программы:");
        for (var entry : result.sortedEntries()) {
            System.out.printf("  %s: %.0f%n", entry.getKey().name(), entry.getValue());
        }

        // Проверки
        Map<Alternative, Double> scores = result.scores();
        boolean passed = true;

        passed &= checkScore("A", scores, 232.0);
        passed &= checkScore("B", scores, 183.0);
        passed &= checkScore("C", scores, 214.0);

        // Проверка порядка
        var sorted = result.sortedEntries();
        passed &= sorted.get(0).getKey().name().equals("B");
        passed &= sorted.get(1).getKey().name().equals("C");
        passed &= sorted.get(2).getKey().name().equals("A");

        printTestResult(passed);
    }

    /**
     * Тест агрегации по Харшаньи (взвешенная сумма полезностей).
     * 
     * РУЧНОЙ РАСЧЁТ:
     * ─────────────────────────────────────────────────────────────────────
     * Входные данные (эксперты с весами):
     *   Expert-1: вес=0.5, A=0.90, B=0.70, C=0.40
     *   Expert-2: вес=0.3, A=0.40, B=0.80, C=0.60
     *   Expert-3: вес=0.2, A=0.60, B=0.50, C=0.90
     * 
     * Формула: U(X) = SUM (weight_i * utility_i(X))
     * 
     * A: 0.5*0.90 + 0.3*0.40 + 0.2*0.60 = 0.45 + 0.12 + 0.12 = 0.69
     * B: 0.5*0.70 + 0.3*0.80 + 0.2*0.50 = 0.35 + 0.24 + 0.10 = 0.69
     * C: 0.5*0.40 + 0.3*0.60 + 0.2*0.90 = 0.20 + 0.18 + 0.18 = 0.56
     * 
     * Результат (больше = лучше): A(0.69) = B(0.69) > C(0.56)
     * Победители: A и B (ничья!)
     * ─────────────────────────────────────────────────────────────────────
     */
    private static void testUtilityAggregator(ProfileDataset dataset) {
        System.out.println("\n" + "─".repeat(70));
        System.out.println("ТЕСТ 3: Агрегация по Харшаньи (UtilityAggregator)");
        System.out.println("─".repeat(70));

        if (dataset.utilityProfile().isEmpty()) {
            System.out.println("  ПРОПУЩЕН: профили полезности не указаны в данных");
            return;
        }

        System.out.println("\nРучной расчёт:");
        System.out.println("  A: 0.5*0.90 + 0.3*0.40 + 0.2*0.60 = 0.45 + 0.12 + 0.12 = 0.69");
        System.out.println("  B: 0.5*0.70 + 0.3*0.80 + 0.2*0.50 = 0.35 + 0.24 + 0.10 = 0.69");
        System.out.println("  C: 0.5*0.40 + 0.3*0.60 + 0.2*0.90 = 0.20 + 0.18 + 0.18 = 0.56");
        System.out.println("  Ожидаемый порядок: A(0.69) = B(0.69) > C(0.56)");

        UtilityAggregator aggregator = new UtilityAggregator();
        UtilityProfile utilityProfile = dataset.utilityProfile().get();
        AggregatedRanking result = aggregator.aggregate(utilityProfile);

        System.out.println("\nРезультат программы:");
        for (var entry : result.sortedEntries()) {
            System.out.printf("  %s: %.2f%n", entry.getKey().name(), entry.getValue());
        }

        // Проверки
        Map<Alternative, Double> scores = result.scores();
        boolean passed = true;

        passed &= checkScoreApprox("A", scores, 0.69, 0.01);
        passed &= checkScoreApprox("B", scores, 0.69, 0.01);
        passed &= checkScoreApprox("C", scores, 0.56, 0.01);

        // Проверка что C на последнем месте
        var sorted = result.sortedEntries();
        passed &= sorted.get(2).getKey().name().equals("C");

        printTestResult(passed);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────

    private static boolean checkScore(String altName, Map<Alternative, Double> scores, double expected) {
        for (var entry : scores.entrySet()) {
            if (entry.getKey().name().equals(altName)) {
                if (Math.abs(entry.getValue() - expected) < 0.001) {
                    return true;
                } else {
                    System.out.printf("  ОШИБКА: %s = %.2f, ожидалось %.2f%n", 
                            altName, entry.getValue(), expected);
                    return false;
                }
            }
        }
        System.out.printf("  ОШИБКА: альтернатива %s не найдена%n", altName);
        return false;
    }

    private static boolean checkScoreApprox(String altName, Map<Alternative, Double> scores, 
                                            double expected, double tolerance) {
        for (var entry : scores.entrySet()) {
            if (entry.getKey().name().equals(altName)) {
                if (Math.abs(entry.getValue() - expected) < tolerance) {
                    return true;
                } else {
                    System.out.printf("  ОШИБКА: %s = %.4f, ожидалось %.4f (±%.4f)%n", 
                            altName, entry.getValue(), expected, tolerance);
                    return false;
                }
            }
        }
        System.out.printf("  ОШИБКА: альтернатива %s не найдена%n", altName);
        return false;
    }

    private static void printTestResult(boolean passed) {
        if (passed) {
            System.out.println("\nТЕСТ ПРОЙДЕН");
            passedTests++;
        } else {
            System.out.println("\nТЕСТ ПРОВАЛЕН");
            failedTests++;
        }
    }
}

