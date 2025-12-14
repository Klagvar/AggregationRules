package aggregation.model;

import java.util.Objects;

/**
 * Модель одной альтернативы (кандидата), участвующей в коллективном выборе.
 */
public final class Alternative {
    private final String name;

    /**
     * Создаёт альтернативу и валидирует, что имя не пустое.
     *
     * @param name отображаемое имя альтернативы
     */
    public Alternative(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Alternative name must be non-empty");
        }
        this.name = name.trim();
    }

    /**
     * Возвращает имя альтернативы.
     */
    public String name() {
        return name;
    }

    /**
     * Сравнивает альтернативы по имени без учёта регистра.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Alternative that)) {
            return false;
        }
        return name.equalsIgnoreCase(that.name);
    }

    /**
     * Возвращает хеш по имени без учёта регистра.
     */
    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase());
    }

    /**
     * Возвращает строковое представление (имя) альтернативы.
     */
    @Override
    public String toString() {
        return name;
    }
}

