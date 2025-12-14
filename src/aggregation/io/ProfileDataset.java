package aggregation.io;

import aggregation.model.PreferenceProfile;
import aggregation.model.UtilityProfile;
import aggregation.model.WeightMatrix;

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

    /**
     * Создаёт контейнер с прочитанными данными.
     */
    public ProfileDataset(PreferenceProfile preferenceProfile,
                          WeightMatrix weightMatrix,
                          UtilityProfile utilityProfile,
                          String title,
                          String source) {
        this.preferenceProfile = preferenceProfile;
        this.weightMatrix = weightMatrix;
        this.utilityProfile = utilityProfile;
        this.title = title;
        this.source = source;
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
}

