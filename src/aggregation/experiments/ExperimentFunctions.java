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

/**
 * Эксперимент: сравнение Borda + Classic Kemeny + 5 весовых функций.
 * 
 * Использование:
 *   java ExperimentFunctions data/skate_conflict.json
 *   
 * Результат сохраняется в results/functions/{dataset_name}.json
 */
public final class ExperimentFunctions {
    
    private ExperimentFunctions() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java ExperimentFunctions <dataset.json>");
            System.err.println("Example: java ExperimentFunctions data/skate_conflict.json");
            return;
        }
        
        Path datasetPath = Path.of(args[0]);
        
        ProfileDataset dataset;
        try {
            dataset = new JsonProfileReader().read(datasetPath);
        } catch (Exception e) {
            System.err.println("Failed to load dataset: " + e.getMessage());
            return;
        }
        
        PreferenceProfile profile = dataset.preferenceProfile();
        String datasetName = datasetPath.getFileName().toString().replace(".json", "");
        
        System.out.println("Dataset: " + dataset.title().orElse(datasetName));
        System.out.println("Alternatives: " + profile.alternatives().size());
        System.out.println("Rankings: " + profile.entries().size());
        System.out.println();
        
        // Собираем результаты
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("dataset", datasetName);
        results.put("title", dataset.title().orElse(datasetName));
        results.put("alternatives", profile.alternatives().size());
        results.put("rankings", profile.entries().size());
        
        List<Map<String, Object>> methods = new ArrayList<>();
        
        // 1. Borda (сумма рангов)
        System.out.println("Running Borda...");
        RankSumAggregator bordaAggregator = new RankSumAggregator();
        AggregatedRanking bordaResult = bordaAggregator.aggregate(profile);
        methods.add(createMethodResult("Borda", bordaResult.sortedMap(), 0));
        
        // 2. Classic Kemeny
        System.out.println("Running Classic Kemeny...");
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        methods.add(createMethodResult("Classic", classicResult.ranking().sortedMap(), classicResult.totalDistance()));
        
        // 3. Linear
        System.out.println("Running Linear...");
        PositionWeightedKemenyResult linearResult = runWeighted(profile, PositionWeightFunction.linear());
        methods.add(createMethodResult("Linear", linearResult.ranking().sortedMap(), linearResult.totalDistance()));
        
        // 4. Logarithmic
        System.out.println("Running Logarithmic...");
        PositionWeightedKemenyResult logResult = runWeighted(profile, PositionWeightFunction.logarithmic());
        methods.add(createMethodResult("Logarithmic", logResult.ranking().sortedMap(), logResult.totalDistance()));
        
        // 5. Hyperbolic
        System.out.println("Running Hyperbolic...");
        PositionWeightedKemenyResult hyperbolicResult = runWeighted(profile, PositionWeightFunction.hyperbolic());
        methods.add(createMethodResult("Hyperbolic", hyperbolicResult.ranking().sortedMap(), hyperbolicResult.totalDistance()));
        
        // 6. Exponential
        System.out.println("Running Exponential...");
        PositionWeightedKemenyResult expResult = runWeighted(profile, PositionWeightFunction.exponential(0.5));
        methods.add(createMethodResult("Exponential", expResult.ranking().sortedMap(), expResult.totalDistance()));
        
        // 7. Top-2
        System.out.println("Running Top-2...");
        PositionWeightedKemenyResult topResult = runWeighted(profile, PositionWeightFunction.topK(2));
        methods.add(createMethodResult("Top2", topResult.ranking().sortedMap(), topResult.totalDistance()));
        
        results.put("methods", methods);
        
        // Сохраняем JSON
        Path outputDir = Path.of("results", "functions");
        Path outputPath = outputDir.resolve(datasetName + ".json");
        
        try {
            Files.createDirectories(outputDir);
            saveJson(outputPath, results);
            System.out.println("\nResults saved to: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to save results: " + e.getMessage());
        }
        
        // Печатаем сводку
        printSummary(methods, profile.alternatives());
    }
    
    private static PositionWeightedKemenyResult runWeighted(PreferenceProfile profile, PositionWeightFunction fn) {
        PositionWeightedKemenySolver solver = new PositionWeightedKemenySolver(fn);
        return solver.solve(profile);
    }
    
    private static Map<String, Object> createMethodResult(String name, Map<Alternative, Double> ranking, double distance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("distance", distance);
        
        // Преобразуем ранжирование в список [{name, rank}, ...]
        List<Map<String, Object>> rankingList = new ArrayList<>();
        ranking.forEach((alt, rank) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("alternative", alt.name());
            entry.put("rank", rank.intValue());
            rankingList.add(entry);
        });
        result.put("ranking", rankingList);
        
        return result;
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
    
    private static void printSummary(List<Map<String, Object>> methods, List<Alternative> alternatives) {
        System.out.println("\n=== SUMMARY ===");
        System.out.printf("%-15s", "Method");
        for (int i = 1; i <= Math.min(5, alternatives.size()); i++) {
            System.out.printf("%-12s", "Pos " + i);
        }
        System.out.println();
        System.out.println("-".repeat(75));
        
        for (Map<String, Object> method : methods) {
            System.out.printf("%-15s", method.get("name"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ranking = (List<Map<String, Object>>) method.get("ranking");
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                String name = (String) ranking.get(i).get("alternative");
                if (name.length() > 10) name = name.substring(0, 10);
                System.out.printf("%-12s", name);
            }
            System.out.println();
        }
    }
}

