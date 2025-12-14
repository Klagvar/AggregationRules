package aggregation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Профиль предпочтений группы: набор ранжировок и числа голосов за каждую из них.
 */
public final class PreferenceProfile {
    private final List<RankingEntry> entries;
    private final List<Alternative> alternatives;

    /**
     * Создаёт профиль и проверяет, что в нём есть хотя бы одна ранжировка.
     */
    public PreferenceProfile(List<RankingEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Preference profile must have at least one ranking entry");
        }
        this.entries = List.copyOf(entries);
        this.alternatives = validateAndExtractAlternatives(entries);
    }

    /**
     * Убеждается, что все ранжировки описывают один и тот же набор альтернатив.
     */
    private List<Alternative> validateAndExtractAlternatives(List<RankingEntry> entries) {
        Set<Alternative> reference = new LinkedHashSet<>(entries.get(0).ranking().alternatives());
        for (RankingEntry entry : entries) {
            Set<Alternative> current = entry.ranking().alternatives();
            if (!current.equals(reference)) {
                throw new IllegalArgumentException("All rankings must contain the same set of alternatives");
            }
        }
        return List.copyOf(reference);
    }

    /**
     * Возвращает список записей профиля.
     */
    public List<RankingEntry> entries() {
        return entries;
    }

    /**
     * Возвращает список альтернатив.
     */
    public List<Alternative> alternatives() {
        return alternatives;
    }

    /**
     * Подсчитывает общее количество голосов в профиле.
     */
    public int totalVoters() {
        return entries.stream().mapToInt(RankingEntry::voters).sum();
    }

    /**
     * Возвращает новый профиль, дополненный ещё одной записью.
     */
    public PreferenceProfile withEntry(RankingEntry entry) {
        Objects.requireNonNull(entry, "entry");
        List<RankingEntry> updated = new ArrayList<>(entries);
        updated.add(entry);
        return new PreferenceProfile(updated);
    }

    /**
     * Проверяет, содержится ли альтернатива в профиле.
     */
    public boolean containsAlternative(Alternative alternative) {
        return alternatives.contains(alternative);
    }

    /**
     * Возвращает неизменяемый список записей.
     */
    public List<RankingEntry> asUnmodifiableList() {
        return Collections.unmodifiableList(entries);
    }
}

