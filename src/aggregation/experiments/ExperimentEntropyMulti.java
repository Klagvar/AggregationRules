package aggregation.experiments;

import aggregation.algorithms.RankSumAggregator;
import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.kemeny.*;
import aggregation.model.AggregatedRanking;
import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Эксперимент: запуск на серии датасетов с множеством seed'ов.
 * Усредняет точность восстановления топ-3 по всем seed'ам для каждого уровня консенсуса.
 * 
 * Структура входных данных:
 *   data/entropy_multi/c0.00/seed_000.json ... seed_099.json
 *   data/entropy_multi/c0.10/seed_000.json ... seed_099.json
 *   ...
 *   
 * Использование:
 *   java ExperimentEntropyMulti data/entropy_multi
 *   
 * Результат сохраняется в results/entropy_multi.json
 */
public final class ExperimentEntropyMulti {
    
    private static final String[] METHODS = {"Borda", "Classic", "Hyperbolic", "Conflict", "Consensus"};
    
    // Метрики: top1, top3, weighted (top1=3, top2=2, top3=1, max=6)
    private record Metrics(int top1, int top3, int weighted) {}
    
    private ExperimentEntropyMulti() {}

    public static void main(String[] args) {
        Path inputDir = args.length > 0 ? Path.of(args[0]) : Path.of("data", "entropy_multi");
        
        if (!Files.isDirectory(inputDir)) {
            System.err.println("Directory not found: " + inputDir);
            return;
        }
        
        System.out.println("Processing datasets from: " + inputDir);
        System.out.println();
        
        List<Map<String, Object>> allResults = new ArrayList<>();
        
        try {
            // Получаем список поддиректорий (c0.00, c0.10, ...)
            List<Path> consensusDirs = Files.list(inputDir)
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
            
            for (Path consensusDir : consensusDirs) {
                String dirName = consensusDir.getFileName().toString();
                double noiseLevel = parseNoiseLevel(dirName);
                
                System.out.printf("Processing %s (noise=%.2f)...%n", dirName, noiseLevel);
                
                Map<String, Object> result = processConsensusLevel(consensusDir, noiseLevel);
                if (result != null) {
                    allResults.add(result);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to list directory: " + e.getMessage());
            return;
        }
        
        // Сохраняем результаты
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("experiment", "entropy_multi_seed_accuracy");
        output.put("description", "Усреднённая точность по множеству seed'ов");
        output.put("consensus_levels", allResults.size());
        output.put("results", allResults);
        
        Path outputPath = Path.of("results", "entropy_multi.json");
        try {
            Files.createDirectories(outputPath.getParent());
            saveJson(outputPath, output);
            System.out.println("\nResults saved to: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to save results: " + e.getMessage());
        }
        
        // Печатаем сводку
        printSummary(allResults);
    }
    
    private static double parseNoiseLevel(String dirName) {
        // Формат: "n0.50" -> noise_level = 0.50
        if (dirName.startsWith("n") || dirName.startsWith("c") || dirName.startsWith("r")) {
            try {
                return Double.parseDouble(dirName.substring(1));
            } catch (NumberFormatException e) {
                return -1.0;
            }
        }
        return -1.0;
    }
    
    private static Map<String, Object> processConsensusLevel(Path consensusDir, double noiseLevel) {
        List<Path> seedFiles;
        try {
            seedFiles = Files.list(consensusDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            System.err.println("  Failed to list seeds: " + e.getMessage());
            return null;
        }
        
        if (seedFiles.isEmpty()) {
            System.err.println("  No seed files found");
            return null;
        }
        
        // Аккумуляторы для суммирования метрик
        Map<String, int[]> totals = new LinkedHashMap<>(); // [top1, top3, weighted]
        for (String method : METHODS) {
            totals.put(method, new int[]{0, 0, 0});
        }
        
        int processedCount = 0;
        
        for (Path seedFile : seedFiles) {
            Map<String, Metrics> metrics = processSingleDataset(seedFile);
            if (metrics != null) {
                for (String method : METHODS) {
                    Metrics m = metrics.get(method);
                    int[] acc = totals.get(method);
                    acc[0] += m.top1;
                    acc[1] += m.top3;
                    acc[2] += m.weighted;
                }
                processedCount++;
            }
        }
        
        if (processedCount == 0) {
            return null;
        }
        
        // Вычисляем средние значения
        Map<String, Object> avgTop1 = new LinkedHashMap<>();
        Map<String, Object> avgTop3 = new LinkedHashMap<>();
        Map<String, Object> avgWeighted = new LinkedHashMap<>();
        
        for (String method : METHODS) {
            int[] acc = totals.get(method);
            avgTop1.put(method, Math.round((double) acc[0] / processedCount * 100.0) / 100.0);
            avgTop3.put(method, Math.round((double) acc[1] / processedCount * 100.0) / 100.0);
            avgWeighted.put(method, Math.round((double) acc[2] / processedCount * 100.0) / 100.0);
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("noise_level", noiseLevel);
        result.put("consensus_level", Math.round((1.0 - noiseLevel) * 100.0) / 100.0);
        result.put("seeds_count", processedCount);
        result.put("avg_top1_accuracy", avgTop1);  // Точность определения победителя
        result.put("avg_top3_accuracy", avgTop3);  // Сколько из топ-3 совпало
        result.put("avg_weighted_score", avgWeighted);  // Взвешенный балл (top1=3, top2=2, top3=1)
        
        return result;
    }
    
    private static Map<String, Metrics> processSingleDataset(Path datasetPath) {
        ProfileDataset dataset;
        try {
            dataset = new JsonProfileReader().read(datasetPath);
        } catch (Exception e) {
            return null;
        }
        
        PreferenceProfile profile = dataset.preferenceProfile();
        
        List<String> groundTruth = dataset.groundTruth().orElse(null);
        if (groundTruth == null) {
            return null;
        }
        
        List<String> top3Truth = groundTruth.subList(0, Math.min(3, groundTruth.size()));
        String top1Truth = groundTruth.get(0);
        
        Map<String, Metrics> results = new LinkedHashMap<>();
        
        // Borda
        RankSumAggregator bordaAggregator = new RankSumAggregator();
        AggregatedRanking bordaResult = bordaAggregator.aggregate(profile);
        results.put("Borda", calculateMetrics(bordaResult.sortedMap(), top1Truth, top3Truth));
        
        // Classic
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        results.put("Classic", calculateMetrics(classicResult.ranking().sortedMap(), top1Truth, top3Truth));
        
        // Hyperbolic
        PositionWeightedKemenySolver hyperbolicSolver = new PositionWeightedKemenySolver(PositionWeightFunction.hyperbolic());
        PositionWeightedKemenyResult hyperbolicResult = hyperbolicSolver.solve(profile);
        results.put("Hyperbolic", calculateMetrics(hyperbolicResult.ranking().sortedMap(), top1Truth, top3Truth));
        
        // Conflict Focus
        AdaptiveKemenySolver conflictSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONFLICT_FOCUS);
        AdaptiveKemenyResult conflictResult = conflictSolver.solve(profile);
        results.put("Conflict", calculateMetrics(conflictResult.ranking().sortedMap(), top1Truth, top3Truth));
        
        // Consensus Focus
        AdaptiveKemenySolver consensusSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONSENSUS_FOCUS);
        AdaptiveKemenyResult consensusResult = consensusSolver.solve(profile);
        results.put("Consensus", calculateMetrics(consensusResult.ranking().sortedMap(), top1Truth, top3Truth));
        
        return results;
    }
    
    private static Metrics calculateMetrics(Map<Alternative, Double> ranking, String top1Truth, List<String> top3Truth) {
        List<String> resultTop3 = ranking.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(e -> e.getKey().name())
                .toList();
        
        // Top-1 accuracy (0 or 1)
        int top1 = resultTop3.get(0).equals(top1Truth) ? 1 : 0;
        
        // Top-3 accuracy (0-3)
        int top3 = 0;
        for (String alt : resultTop3) {
            if (top3Truth.contains(alt)) {
                top3++;
            }
        }
        
        // Weighted score: top1=3, top2=2, top3=1 (max=6)
        int weighted = 0;
        for (int i = 0; i < 3; i++) {
            if (resultTop3.get(i).equals(top3Truth.get(i))) {
                weighted += (3 - i);  // 3 for pos 0, 2 for pos 1, 1 for pos 2
            }
        }
        
        return new Metrics(top1, top3, weighted);
    }
    
    private static void saveJson(Path path, Map<String, Object> data) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(toJson(data, 0));
        }
    }
    
    private static String toJson(Object obj, int indent) {
        String indentStr = "  ".repeat(indent);
        String nextIndent = "  ".repeat(indent + 1);
        
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + obj + "\"";
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof List<?> list) {
            if (list.isEmpty()) return "[]";
            boolean simple = list.stream().allMatch(e -> e instanceof String || e instanceof Number);
            if (simple) {
                return "[" + String.join(", ", list.stream().map(e -> toJson(e, 0)).toList()) + "]";
            }
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(nextIndent).append(toJson(list.get(i), indent + 1));
                if (i < list.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(indentStr).append("]");
            return sb.toString();
        } else if (obj instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder sb = new StringBuilder("{\n");
            var entries = new ArrayList<>(map.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                sb.append(nextIndent).append("\"").append(entry.getKey()).append("\": ");
                sb.append(toJson(entry.getValue(), indent + 1));
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(indentStr).append("}");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }
    
    private static void printSummary(List<Map<String, Object>> results) {
        // Top-1 Accuracy (определение победителя)
        System.out.println("\n=== TOP-1 ACCURACY (определение победителя) ===");
        System.out.printf("%-8s %-8s %-8s %-8s %-8s %-8s %-8s%n", 
                "Cons", "Noise", "Borda", "Classic", "Hyperb", "Conflict", "Consens");
        System.out.println("-".repeat(64));
        
        for (Map<String, Object> result : results) {
            @SuppressWarnings("unchecked")
            Map<String, Object> acc = (Map<String, Object>) result.get("avg_top1_accuracy");
            System.out.printf("%-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f%n",
                    result.get("consensus_level"),
                    result.get("noise_level"),
                    ((Number) acc.get("Borda")).doubleValue(),
                    ((Number) acc.get("Classic")).doubleValue(),
                    ((Number) acc.get("Hyperbolic")).doubleValue(),
                    ((Number) acc.get("Conflict")).doubleValue(),
                    ((Number) acc.get("Consensus")).doubleValue());
        }
        
        // Top-3 Accuracy
        System.out.println("\n=== TOP-3 ACCURACY (совпадений из 3) ===");
        System.out.printf("%-8s %-8s %-8s %-8s %-8s %-8s %-8s%n", 
                "Cons", "Noise", "Borda", "Classic", "Hyperb", "Conflict", "Consens");
        System.out.println("-".repeat(64));
        
        for (Map<String, Object> result : results) {
            @SuppressWarnings("unchecked")
            Map<String, Object> acc = (Map<String, Object>) result.get("avg_top3_accuracy");
            System.out.printf("%-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f%n",
                    result.get("consensus_level"),
                    result.get("noise_level"),
                    ((Number) acc.get("Borda")).doubleValue(),
                    ((Number) acc.get("Classic")).doubleValue(),
                    ((Number) acc.get("Hyperbolic")).doubleValue(),
                    ((Number) acc.get("Conflict")).doubleValue(),
                    ((Number) acc.get("Consensus")).doubleValue());
        }
        
        // Weighted Score
        System.out.println("\n=== WEIGHTED SCORE (top1=3, top2=2, top3=1, max=6) ===");
        System.out.printf("%-8s %-8s %-8s %-8s %-8s %-8s %-8s%n", 
                "Cons", "Noise", "Borda", "Classic", "Hyperb", "Conflict", "Consens");
        System.out.println("-".repeat(64));
        
        for (Map<String, Object> result : results) {
            @SuppressWarnings("unchecked")
            Map<String, Object> acc = (Map<String, Object>) result.get("avg_weighted_score");
            System.out.printf("%-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f %-8.2f%n",
                    result.get("consensus_level"),
                    result.get("noise_level"),
                    ((Number) acc.get("Borda")).doubleValue(),
                    ((Number) acc.get("Classic")).doubleValue(),
                    ((Number) acc.get("Hyperbolic")).doubleValue(),
                    ((Number) acc.get("Conflict")).doubleValue(),
                    ((Number) acc.get("Consensus")).doubleValue());
        }
    }
}

