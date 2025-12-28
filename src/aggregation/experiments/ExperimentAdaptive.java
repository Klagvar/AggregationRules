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
 * Эксперимент: сравнение Borda + Classic + адаптивных методов.
 * Также выводит энтропию и веса для каждой позиции.
 * 
 * Использование:
 *   java ExperimentAdaptive data/skate_conflict.json
 *   
 * Результат сохраняется в results/adaptive/{dataset_name}.json
 */
public final class ExperimentAdaptive {
    
    private ExperimentAdaptive() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java ExperimentAdaptive <dataset.json>");
            System.err.println("Example: java ExperimentAdaptive data/skate_conflict.json");
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
        
        // Анализ энтропии
        System.out.println("Analyzing entropy...");
        PositionEntropyAnalyzer entropyAnalyzer = PositionEntropyAnalyzer.analyze(profile);
        
        // Собираем результаты
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("dataset", datasetName);
        results.put("title", dataset.title().orElse(datasetName));
        results.put("alternatives", profile.alternatives().size());
        results.put("rankings", profile.entries().size());
        
        // Энтропия по позициям
        List<Map<String, Object>> entropyList = new ArrayList<>();
        double[] entropies = entropyAnalyzer.getEntropies();
        double maxEntropy = entropyAnalyzer.getMaxEntropy();
        
        PositionWeightFunction conflictWf = entropyAnalyzer.toWeightFunction(AdaptiveWeightMode.CONFLICT_FOCUS);
        PositionWeightFunction consensusWf = entropyAnalyzer.toWeightFunction(AdaptiveWeightMode.CONSENSUS_FOCUS);
        
        for (int k = 0; k < entropies.length; k++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("position", k + 1);
            entry.put("entropy", round(entropies[k], 4));
            entry.put("normalized", round(entropies[k] / maxEntropy, 4));
            entry.put("conflict_weight", round(conflictWf.weight(k + 1, entropies.length), 4));
            entry.put("consensus_weight", round(consensusWf.weight(k + 1, entropies.length), 4));
            entropyList.add(entry);
        }
        results.put("entropy_analysis", entropyList);
        results.put("max_entropy", round(maxEntropy, 4));
        
        List<Map<String, Object>> methods = new ArrayList<>();
        
        // 1. Borda
        System.out.println("Running Borda...");
        RankSumAggregator bordaAggregator = new RankSumAggregator();
        AggregatedRanking bordaResult = bordaAggregator.aggregate(profile);
        methods.add(createMethodResult("Borda", bordaResult.sortedMap(), 0));
        
        // 2. Classic Kemeny
        System.out.println("Running Classic Kemeny...");
        KemenyMedianSolver classicSolver = new KemenyMedianSolver();
        KemenyResult classicResult = classicSolver.solve(profile);
        methods.add(createMethodResult("Classic", classicResult.ranking().sortedMap(), classicResult.totalDistance()));
        
        // 3. Hyperbolic
        System.out.println("Running Hyperbolic...");
        PositionWeightedKemenySolver hyperbolicSolver = new PositionWeightedKemenySolver(PositionWeightFunction.hyperbolic());
        PositionWeightedKemenyResult hyperbolicResult = hyperbolicSolver.solve(profile);
        methods.add(createMethodResult("Hyperbolic", hyperbolicResult.ranking().sortedMap(), hyperbolicResult.totalDistance()));
        
        // 4. Conflict Focus
        System.out.println("Running Conflict Focus...");
        AdaptiveKemenySolver conflictSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONFLICT_FOCUS);
        AdaptiveKemenyResult conflictResult = conflictSolver.solve(profile);
        methods.add(createMethodResult("Conflict", conflictResult.ranking().sortedMap(), conflictResult.totalWeightedDistance()));
        
        // 5. Consensus Focus
        System.out.println("Running Consensus Focus...");
        AdaptiveKemenySolver consensusSolver = new AdaptiveKemenySolver(AdaptiveWeightMode.CONSENSUS_FOCUS);
        AdaptiveKemenyResult consensusResult = consensusSolver.solve(profile);
        methods.add(createMethodResult("Consensus", consensusResult.ranking().sortedMap(), consensusResult.totalWeightedDistance()));
        
        results.put("methods", methods);
        
        // Сохраняем JSON
        Path outputDir = Path.of("results", "adaptive");
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
        printEntropyTable(entropyList);
    }
    
    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
    
    private static Map<String, Object> createMethodResult(String name, Map<Alternative, Double> ranking, double distance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("distance", round(distance, 4));
        
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
        System.out.println("\n=== RANKING SUMMARY ===");
        System.out.printf("%-12s", "Method");
        for (int i = 1; i <= Math.min(7, alternatives.size()); i++) {
            System.out.printf("%-12s", "Pos " + i);
        }
        System.out.println();
        System.out.println("-".repeat(96));
        
        for (Map<String, Object> method : methods) {
            System.out.printf("%-12s", method.get("name"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ranking = (List<Map<String, Object>>) method.get("ranking");
            for (int i = 0; i < Math.min(7, ranking.size()); i++) {
                String name = (String) ranking.get(i).get("alternative");
                if (name.length() > 10) name = name.substring(0, 10);
                System.out.printf("%-12s", name);
            }
            System.out.println();
        }
    }
    
    private static void printEntropyTable(List<Map<String, Object>> entropyList) {
        System.out.println("\n=== ENTROPY & WEIGHTS ===");
        System.out.printf("%-8s %-10s %-12s %-15s %-15s%n", "Position", "Entropy", "Normalized", "Conflict Wt", "Consensus Wt");
        System.out.println("-".repeat(60));
        
        for (Map<String, Object> entry : entropyList) {
            System.out.printf("%-8d %-10.4f %-12.4f %-15.4f %-15.4f%n",
                    entry.get("position"),
                    entry.get("entropy"),
                    entry.get("normalized"),
                    entry.get("conflict_weight"),
                    entry.get("consensus_weight"));
        }
    }
}

