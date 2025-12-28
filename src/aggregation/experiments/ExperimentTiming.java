package aggregation.experiments;

import aggregation.algorithms.RankSumAggregator;
import aggregation.io.JsonProfileReader;
import aggregation.io.ProfileDataset;
import aggregation.kemeny.*;
import aggregation.model.PreferenceProfile;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Эксперимент: замер времени выполнения на датасетах разного размера.
 * 
 * Использование:
 *   java ExperimentTiming data/timing
 *   
 * Результат сохраняется в results/timing.json
 */
public final class ExperimentTiming {
    
    private static final int WARMUP_RUNS = 2;
    private static final int MEASUREMENT_RUNS = 3;
    
    private ExperimentTiming() {}

    public static void main(String[] args) {
        Path inputDir = args.length > 0 ? Path.of(args[0]) : Path.of("data", "timing");
        
        if (!Files.isDirectory(inputDir)) {
            System.err.println("Directory not found: " + inputDir);
            return;
        }
        
        System.out.println("Processing datasets from: " + inputDir);
        System.out.println("Warmup runs: " + WARMUP_RUNS);
        System.out.println("Measurement runs: " + MEASUREMENT_RUNS);
        System.out.println();
        
        List<Map<String, Object>> allResults = new ArrayList<>();
        
        try {
            List<Path> files = Files.list(inputDir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
            
            for (Path file : files) {
                System.out.println("Processing: " + file.getFileName());
                Map<String, Object> result = processDataset(file);
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
        output.put("experiment", "timing");
        output.put("warmup_runs", WARMUP_RUNS);
        output.put("measurement_runs", MEASUREMENT_RUNS);
        output.put("datasets_count", allResults.size());
        output.put("results", allResults);
        
        Path outputPath = Path.of("results", "timing.json");
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
    
    private static Map<String, Object> processDataset(Path datasetPath) {
        ProfileDataset dataset;
        try {
            dataset = new JsonProfileReader().read(datasetPath);
        } catch (Exception e) {
            System.err.println("  Failed to load: " + e.getMessage());
            return null;
        }
        
        PreferenceProfile profile = dataset.preferenceProfile();
        int numExperts = dataset.numExperts().orElse(profile.entries().stream()
                .mapToInt(e -> e.voters())
                .sum());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file", datasetPath.getFileName().toString());
        result.put("alternatives", profile.alternatives().size());
        result.put("experts", numExperts);
        result.put("unique_rankings", profile.entries().size());
        
        Map<String, Object> timings = new LinkedHashMap<>();
        
        // Borda
        timings.put("Borda", measureMethod(() -> {
            new RankSumAggregator().aggregate(profile);
        }));
        
        // Classic Kemeny
        timings.put("Classic", measureMethod(() -> {
            new KemenyMedianSolver().solve(profile);
        }));
        
        // Hyperbolic
        timings.put("Hyperbolic", measureMethod(() -> {
            new PositionWeightedKemenySolver(PositionWeightFunction.hyperbolic()).solve(profile);
        }));
        
        // Conflict Focus
        timings.put("Conflict", measureMethod(() -> {
            new AdaptiveKemenySolver(AdaptiveWeightMode.CONFLICT_FOCUS).solve(profile);
        }));
        
        // Consensus Focus
        timings.put("Consensus", measureMethod(() -> {
            new AdaptiveKemenySolver(AdaptiveWeightMode.CONSENSUS_FOCUS).solve(profile);
        }));
        
        result.put("timing_ms", timings);
        
        return result;
    }
    
    private static double measureMethod(Runnable method) {
        // Warmup
        for (int i = 0; i < WARMUP_RUNS; i++) {
            method.run();
        }
        
        // Measurement
        long totalNanos = 0;
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            long start = System.nanoTime();
            method.run();
            long end = System.nanoTime();
            totalNanos += (end - start);
        }
        
        double avgMs = (totalNanos / MEASUREMENT_RUNS) / 1_000_000.0;
        return Math.round(avgMs * 100) / 100.0; // Round to 2 decimals
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
    
    private static void printSummary(List<Map<String, Object>> results) {
        System.out.println("\n=== TIMING EXPERIMENT SUMMARY ===");
        System.out.printf("%-25s %-10s %-10s %-10s %-10s %-10s %-10s%n", 
                "File", "Experts", "Borda", "Classic", "Hyperb", "Conflict", "Consens");
        System.out.println("-".repeat(95));
        
        for (Map<String, Object> result : results) {
            @SuppressWarnings("unchecked")
            Map<String, Object> timing = (Map<String, Object>) result.get("timing_ms");
            System.out.printf("%-25s %-10d %-10.2f %-10.2f %-10.2f %-10.2f %-10.2f%n",
                    result.get("file"),
                    result.get("experts"),
                    timing.get("Borda"),
                    timing.get("Classic"),
                    timing.get("Hyperbolic"),
                    timing.get("Conflict"),
                    timing.get("Consensus"));
        }
        System.out.println("\nTimes in milliseconds (average of " + MEASUREMENT_RUNS + " runs)");
    }
}

