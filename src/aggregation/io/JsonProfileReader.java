package aggregation.io;

import aggregation.model.Alternative;
import aggregation.model.PreferenceProfile;
import aggregation.model.Ranking;
import aggregation.model.RankingEntry;
import aggregation.model.UtilityProfile;
import aggregation.model.WeightMatrix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Загрузчик профиля предпочтений из JSON-файла, оформленного по договорённому формату.
 */
public final class JsonProfileReader {

    /**
     * Читает файл и преобразует его в {@link ProfileDataset}.
     */
    public ProfileDataset read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Object rootValue = new SimpleJsonParser(content).parse();
        Map<String, Object> root = asObject(rootValue, "root");

        Map<String, Alternative> alternativeMap = readAlternatives(root);
        PreferenceProfile preferenceProfile = readRankings(root, alternativeMap);
        WeightMatrix weightMatrix = readWeightMatrix(root, preferenceProfile, alternativeMap);
        UtilityProfile utilityProfile = readUtilityProfile(root, alternativeMap);
        Map<String, Object> metadata = asObject(root.get("metadata"), "metadata", true);

        String title = metadata != null ? asString(metadata.get("title"), true) : null;
        String source = metadata != null ? asString(metadata.get("source"), true) : null;

        return new ProfileDataset(preferenceProfile, weightMatrix, utilityProfile, title, source);
    }

    /**
     * Читает раздел с альтернативами и создаёт словарь имя -> объект.
     */
    private Map<String, Alternative> readAlternatives(Map<String, Object> root) {
        List<String> names = asStringList(root.get("alternatives"), false);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("alternatives array must not be empty");
        }
        Map<String, Alternative> map = new LinkedHashMap<>();
        for (String name : names) {
            Alternative alternative = new Alternative(name);
            if (map.putIfAbsent(name, alternative) != null) {
                throw new IllegalArgumentException("Duplicate alternative: " + name);
            }
        }
        return map;
    }

    /**
     * Формирует {@link PreferenceProfile} на основе массива ранжировок.
     */
    private PreferenceProfile readRankings(Map<String, Object> root,
                                           Map<String, Alternative> alternatives) {
        List<Map<String, Object>> rankings = asObjectList(root.get("rankings"), false);
        if (rankings.isEmpty()) {
            throw new IllegalArgumentException("rankings array must not be empty");
        }
        List<RankingEntry> entries = new ArrayList<>();
        for (Map<String, Object> rankingNode : rankings) {
            List<String> orderNames = asStringList(rankingNode.get("order"), false);
            if (orderNames.size() != alternatives.size()) {
                throw new IllegalArgumentException("Ranking order must list all alternatives");
            }
            List<Alternative> order = orderNames.stream()
                    .map(name -> requireAlternative(name, alternatives))
                    .toList();
            Ranking ranking = Ranking.fromOrder(order);
            int voters = toPositiveInt(rankingNode.get("voters"), "voters");
            entries.add(new RankingEntry(ranking, voters));
        }
        return new PreferenceProfile(entries);
    }

    /**
     * Собирает матрицу весов, если в JSON присутствует поле weights.
     */
    private WeightMatrix readWeightMatrix(Map<String, Object> root,
                                          PreferenceProfile profile,
                                          Map<String, Alternative> alternatives) {
        List<Map<String, Object>> rankingNodes = asObjectList(root.get("rankings"), false);
        boolean hasWeights = rankingNodes.stream().anyMatch(node -> node.containsKey("weights"));
        if (!hasWeights) {
            return null;
        }
        WeightMatrix.Builder builder = WeightMatrix.builder(profile);
        for (Map<String, Object> rankingNode : rankingNodes) {
            Map<String, Object> weightsNode = asObject(rankingNode.get("weights"), "weights");
            Map<Alternative, Double> row = new LinkedHashMap<>();
            for (Alternative alternative : profile.alternatives()) {
                Object value = weightsNode.get(alternative.name());
                if (value == null) {
                    throw new IllegalArgumentException("Missing weight for alternative " + alternative.name());
                }
                double weight = toPositiveDouble(value, "weight");
                row.put(alternative, weight);
            }
            builder.addRow(row);
        }
        return builder.build();
    }

    /**
     * Строит профиль полезностей (utilityProfiles), если он задан.
     */
    private UtilityProfile readUtilityProfile(Map<String, Object> root,
                                              Map<String, Alternative> alternatives) {
        List<Map<String, Object>> utilitiesNodes = asObjectList(root.get("utilityProfiles"), true);
        if (utilitiesNodes == null || utilitiesNodes.isEmpty()) {
            return null;
        }
        List<Double> weights = new ArrayList<>();
        Map<Alternative, List<Double>> utilitiesByAlternative = new LinkedHashMap<>();
        for (Alternative alternative : alternatives.values()) {
            utilitiesByAlternative.put(alternative, new ArrayList<>());
        }
        for (Map<String, Object> node : utilitiesNodes) {
            double weight = toPositiveDouble(node.get("weight"), "utility weight");
            weights.add(weight);
            Map<String, Object> values = asObject(node.get("utilities"), "utilities");
            for (Alternative alternative : alternatives.values()) {
                Object rawValue = values.get(alternative.name());
                if (rawValue == null) {
                    throw new IllegalArgumentException("Missing utility value for alternative " + alternative.name());
                }
                double utility = toBoundedDouble(rawValue, "utility", 0.0, 1.0);
                utilitiesByAlternative.get(alternative).add(utility);
            }
        }
        return UtilityProfile.fromRawValues(
                new ArrayList<>(alternatives.values()),
                utilitiesByAlternative,
                weights
        );
    }

    @SuppressWarnings("unchecked")
    /**
     * Проверяет, что узел является объектом, и приводит его к Map.
     */
    private Map<String, Object> asObject(Object node, String name) {
        if (node instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected object for " + name);
    }

    @SuppressWarnings("unchecked")
    /**
     * То же, что {@link #asObject(Object, String)}, но допускает отсутствие узла.
     */
    private Map<String, Object> asObject(Object node, String name, boolean optional) {
        if (node == null) {
            if (optional) {
                return null;
            }
            throw new IllegalArgumentException("Missing object: " + name);
        }
        return asObject(node, name);
    }

    @SuppressWarnings("unchecked")
    /**
     * Проверяет, что узел — список объектов.
     */
    private List<Map<String, Object>> asObjectList(Object node, boolean optional) {
        if (node == null) {
            if (optional) {
                return null;
            }
            throw new IllegalArgumentException("Expected array value");
        }
        if (node instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("Expected object inside array");
                }
                result.add((Map<String, Object>) map);
            }
            return result;
        }
        throw new IllegalArgumentException("Expected array");
    }

    @SuppressWarnings("unchecked")
    /**
     * Проверяет, что узел — список строк, и возвращает его.
     */
    private List<String> asStringList(Object node, boolean optional) {
        if (node == null) {
            if (optional) {
                return List.of();
            }
            throw new IllegalArgumentException("Expected array of strings");
        }
        if (node instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(asString(item, false));
            }
            return result;
        }
        throw new IllegalArgumentException("Expected array of strings");
    }

    /**
     * Преобразует узел к строке или выбрасывает исключение.
     */
    private String asString(Object node, boolean optional) {
        if (node == null) {
            if (optional) {
                return null;
            }
            throw new IllegalArgumentException("Expected string value");
        }
        if (node instanceof String str) {
            return str;
        }
        throw new IllegalArgumentException("Expected string value");
    }

    /**
     * Достаёт альтернативу по имени, выбрасывая ошибку при неизвестном идентификаторе.
     */
    private Alternative requireAlternative(String name, Map<String, Alternative> alternatives) {
        Alternative alternative = alternatives.get(name);
        if (alternative == null) {
            throw new IllegalArgumentException("Unknown alternative: " + name);
        }
        return alternative;
    }

    /**
     * Валидирует, что значение — положительное целое.
     */
    private int toPositiveInt(Object value, String label) {
        if (value instanceof Number number) {
            int intValue = number.intValue();
            if (intValue > 0) {
                return intValue;
            }
        }
        throw new IllegalArgumentException(label + " must be a positive integer");
    }

    /**
     * Валидирует, что значение — положительное число.
     */
    private double toPositiveDouble(Object value, String label) {
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (doubleValue > 0) {
                return doubleValue;
            }
        }
        throw new IllegalArgumentException(label + " must be a positive number");
    }

    /**
     * Валидирует, что значение лежит в указанном интервале.
     */
    private double toBoundedDouble(Object value, String label, double min, double max) {
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (doubleValue >= min && doubleValue <= max) {
                return doubleValue;
            }
        }
        throw new IllegalArgumentException(label + " must be in [" + min + ", " + max + "]");
    }
}

