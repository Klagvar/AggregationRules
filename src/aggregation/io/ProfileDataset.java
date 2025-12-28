package aggregation.io;

import aggregation.model.PreferenceProfile;
import aggregation.model.UtilityProfile;
import aggregation.model.WeightMatrix;

import java.util.List;
import java.util.Optional;

/**
 * Результат загрузки набора данных из JSON: профиль предпочтений + необязательные блоки.
 */
public final class ProfileDataset {
    private final PreferenceProfile preferenceProfile;
    private final WeightMatrix weightMatrix;
    private final UtilityProfile utilityProfile;
    private final String title;
    private final String source;
    private final List<String> groundTruth;
    private final Double consensusLevel;
    private final Integer numExperts;

    /**
     * Создаёт контейнер с прочитанными данными.
     */
    public ProfileDataset(PreferenceProfile preferenceProfile,
                          WeightMatrix weightMatrix,
                          UtilityProfile utilityProfile,
                          String title,
                          String source,
                          List<String> groundTruth,
                          Double consensusLevel,
                          Integer numExperts) {
        this.preferenceProfile = preferenceProfile;
        this.weightMatrix = weightMatrix;
        this.utilityProfile = utilityProfile;
        this.title = title;
        this.source = source;
        this.groundTruth = groundTruth;
        this.consensusLevel = consensusLevel;
        this.numExperts = numExperts;
    }
    
    /**
     * Конструктор для обратной совместимости.
     */
    public ProfileDataset(PreferenceProfile preferenceProfile,
                          WeightMatrix weightMatrix,
                          UtilityProfile utilityProfile,
                          String title,
                          String source) {
        this(preferenceProfile, weightMatrix, utilityProfile, title, source, null, null, null);
    }

    /**
     * Возвращает обязательный профиль предпочтений.
     */
    public PreferenceProfile preferenceProfile() {
        return preferenceProfile;
    }

    /**
     * Возвращает матрицу весов, если она была в файле.
     */
    public Optional<WeightMatrix> weightMatrix() {
        return Optional.ofNullable(weightMatrix);
    }

    /**
     * Возвращает профиль полезностей, если он задан.
     */
    public Optional<UtilityProfile> utilityProfile() {
        return Optional.ofNullable(utilityProfile);
    }

    /**
     * Опциональное название набора данных.
     */
    public Optional<String> title() {
        return Optional.ofNullable(title).filter(s -> !s.isBlank());
    }

    /**
     * Опциональное поле источника (для ссылок на литературу).
     */
    public Optional<String> source() {
        return Optional.ofNullable(source).filter(s -> !s.isBlank());
    }
    
    /**
     * Эталонное ранжирование (для синтетических данных).
     */
    public Optional<List<String>> groundTruth() {
        return Optional.ofNullable(groundTruth);
    }
    
    /**
     * Уровень консенсуса (для синтетических данных, 0-1).
     */
    public Optional<Double> consensusLevel() {
        return Optional.ofNullable(consensusLevel);
    }
    
    /**
     * Количество экспертов (из метаданных).
     */
    public Optional<Integer> numExperts() {
        return Optional.ofNullable(numExperts);
    }
}
