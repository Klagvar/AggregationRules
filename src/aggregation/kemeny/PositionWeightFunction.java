package aggregation.kemeny;

/**
 * Функция весов позиций φ(k) для позиционно-взвешенной медианы Кемени.
 * Определяет важность каждой позиции в ранжировке.
 */
@FunctionalInterface
public interface PositionWeightFunction {

    /**
     * Возвращает вес для позиции k.
     *
     * @param k позиция (1-based: 1 = лучшее место)
     * @param m общее количество альтернатив
     * @return вес позиции (положительное число)
     */
    double weight(int k, int m);

    // ========== Стандартные реализации ==========

    /**
     * Равномерная функция: φ(k) = 1.
     * Эквивалентна классическому методу Кемени.
     */
    static PositionWeightFunction uniform() {
        return (k, m) -> 1.0;
    }

    /**
     * Гиперболическая функция: φ(k) = 1/k.
     * Топ-1 в k раз важнее топ-k.
     * Применение: выборы, рейтинги.
     */
    static PositionWeightFunction hyperbolic() {
        return (k, m) -> 1.0 / k;
    }

    /**
     * Линейная функция: φ(k) = (m - k + 1) / m.
     * Плавное убывание от 1 до 1/m.
     * Применение: универсальная.
     */
    static PositionWeightFunction linear() {
        return (k, m) -> (double) (m - k + 1) / m;
    }

    /**
     * Экспоненциальная функция: φ(k) = e^(-α(k-1)).
     * Резкий акцент на топовых позициях.
     *
     * @param alpha параметр затухания (больше = быстрее затухание)
     */
    static PositionWeightFunction exponential(double alpha) {
        return (k, m) -> Math.exp(-alpha * (k - 1));
    }

    /**
     * Top-K функция: φ(k) = 1 если k ≤ K, иначе 0.
     * Учитывает только первые K позиций.
     * Применение: отбор с отсечкой.
     *
     * @param topK количество учитываемых позиций
     */
    static PositionWeightFunction topK(int topK) {
        return (k, m) -> k <= topK ? 1.0 : 0.0;
    }

    /**
     * Логарифмическая функция: φ(k) = 1 / log₂(k + 1).
     * Мягкое затухание.
     * Применение: рекомендательные системы.
     */
    static PositionWeightFunction logarithmic() {
        return (k, m) -> 1.0 / (Math.log(k + 1) / Math.log(2));
    }
}

