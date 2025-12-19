# AggregationRules

Программная реализация методов агрегирования индивидуальных предпочтений на языке Java. Проект основан на учебнике:

> **Петровский А.Б. Теория принятия решений.** — М.: Издательский центр «Академия», 2009. — Глава 21, 22.

## Реализованные методы

### Базовые методы (из учебника)

| Метод | Описание |
|-------|----------|
| **Сумма рангов** (Гудман—Марковиц) | $r_{agg}(A_i) = \sum_{s} g_s \cdot r_i^{(s)}$ |
| **Взвешенная сумма рангов** | $r_{agg}(A_i) = \sum_{s} g_s \cdot w_{r_i}^{(s)}$ |
| **Аддитивная полезность Харшаньи** | $U(A_i) = \sum_{j} \alpha_j \cdot u_{ij}$ |
| **Медиана Кемени** | Минимизация $\sum d_{ik}$ через задачу о назначениях |

### Новый метод: Позиционно-взвешенная медиана Кемени

**Модификация классического метода Кемени** с учётом неравнозначности позиций в ранжировке.

**Идея:** В реальных задачах ошибка на первом месте критичнее, чем на последнем. Например, в выборах важнее правильно определить лидера, чем аутсайдера.

**Формула:**
$$d_{ik} = \sum_{l=1}^{n} g_l \cdot \varphi(k) \cdot |k - r_{il}|$$

где $\varphi(k)$ — весовая функция позиции $k$.

**Реализованные весовые функции:**

| Функция | Формула | Применение |
|---------|---------|------------|
| Uniform | $\varphi(k) = 1$ | Классический Кемени |
| Hyperbolic | $\varphi(k) = 1/k$ | Выборы, рейтинги |
| Linear | $\varphi(k) = (m-k+1)/m$ | Сбалансированный подход |
| Exponential | $\varphi(k) = e^{-0.5(k-1)}$ | Сильный фокус на топе |
| Logarithmic | $\varphi(k) = 1/\log_2(k+1)$ | Плавное убывание |
| Top-K | $\varphi(k) = 1$ если $k \leq K$, иначе $0$ | Только топ-K позиций |

### Планируется: Адаптивная медиана Кемени

**Идея:** Веса позиций вычисляются автоматически из данных, а не задаются вручную. Используется **энтропия согласованности экспертов** на каждой позиции.

**Мотивация:** Если на позиции k=1 все эксперты согласны (все поставили одну альтернативу), то эта позиция "решена" и не требует большого веса. Если на позиции k=3 мнения расходятся — там конфликт, который нужно разрешить.

**Формула энтропии для позиции k:**
$$H(k) = -\sum_{A_i} p_i \log_2(p_i)$$

где $p_i$ — доля экспертов, поставивших альтернативу $A_i$ на позицию $k$.

**Два режима работы:**

| Режим | Формула веса | Логика |
|-------|--------------|--------|
| CONFLICT_FOCUS | $\varphi(k) = H(k) / H_{max}$ | Больший вес спорным позициям |
| CONSENSUS_FOCUS | $\varphi(k) = 1 - H(k) / H_{max}$ | Больший вес согласованным позициям |

**Преимущество:** Метод самонастраивающийся — не требует экспертного задания весовой функции, адаптируется к структуре данных.

## Структура проекта

```
src/aggregation/
  Main.java                           # точка входа
  PositionWeightedDemo.java           # демо нового метода
  model/                              # модели данных
    Alternative.java
    Ranking.java
    RankingEntry.java
    PreferenceProfile.java
    WeightMatrix.java
    UtilityVector.java
    UtilityProfile.java
    AggregatedRanking.java
  algorithms/                         # алгоритмы агрегирования
    RankSumAggregator.java
    WeightedRankSumAggregator.java
    UtilityAggregator.java
  kemeny/                             # медиана Кемени
    DistanceMatrix.java
    HungarianSolver.java
    KemenyMedianSolver.java
    KemenyResult.java
    PositionWeightFunction.java       # [NEW] весовые функции
    WeightedDistanceMatrix.java       # [NEW] взвешенная матрица
    PositionWeightedKemenySolver.java # [NEW] решатель
    PositionWeightedKemenyResult.java # [NEW] результат
  io/                                 # ввод-вывод
    JsonProfileReader.java
    ProfileDataset.java
    SimpleJsonParser.java
```

## Датасет

Проект использует реальные данные из **PrefLib** (Preference Library):

**Skate Dataset — Figure Skating Judges**  
 https://www.preflib.org/dataset/00006

- Оценки судей на чемпионатах по фигурному катанию
- 9 судей ранжируют 14 фигуристов
- Полные ранжировки (SOC — Strict Orders Complete)

Для конвертации данных PrefLib в JSON используется скрипт:

```bash
python convert_preflib.py preflib/00006_skate/00006-00000003.soc -o data/skate_euros_pairs.json
```

## Результаты на датасете Skate

**European Championships, Pairs Short Program** (14 альтернатив, 9 судей)

| Позиция | Фигурист | Classic | Hyperbolic | Top-2 |
|---------|----------|---------|------------|-------|
| 1 | Berezhnaya & Sikharulidze | 1 | 1 | 1 |
| 2 | Abitbol & Bernadis | 2 | 2 | 2 |
| 3 | Zagorska & Siudek | 4 | 4 | **3** |
| 4 | Kazakova & Dmitriev | 3 | 3 | **4** |
| ... | ... | ... | ... | ... |

**Вывод:** Данный датасет высоко согласован (судьи единодушны по лидерам), поэтому большинство методов дают схожие результаты. Для демонстрации различий между весовыми функциями необходимо протестировать на датасетах с большим расхождением между экспертами (например, политические выборы, опросы с противоречивыми мнениями).

## Формат входных данных

Входные данные задаются в формате JSON:

```json
{
  "metadata": {
    "title": "Example",
    "source": "Textbook"
  },
  "alternatives": ["A", "B", "C"],
  "rankings": [
    {
      "order": ["A", "B", "C"],
      "voters": 23,
      "weights": {"A": 2, "B": 4, "C": 5}
    },
    {
      "order": ["B", "C", "A"],
      "voters": 17,
      "weights": {"A": 6, "B": 1, "C": 3}
    }
  ],
  "utilityProfiles": [
    {
      "id": "Expert-1",
      "weight": 0.5,
      "utilities": {"A": 0.90, "B": 0.70, "C": 0.40}
    }
  ]
}
```

### Описание полей

- `alternatives` — массив имён альтернатив.
- `rankings` — массив ранжировок:
  - `order` — упорядочение альтернатив (первый элемент — лучший);
  - `voters` — число голосов за данную ранжировку;
  - `weights` — (опционально) веса мест для взвешенной суммы.
- `utilityProfiles` — (опционально) массив экспертов для метода Харшаньи:
  - `id` — идентификатор эксперта;
  - `weight` — вес эксперта;
  - `utilities` — значения полезности для каждой альтернативы (от 0 до 1).

Полное описание формата: [`docs/json_format.md`](docs/json_format.md).

## Требования

- JDK 17 или выше
- Python 3.8+ (для конвертера PrefLib)

Проект не использует внешних зависимостей.

## Сборка и запуск

**Сборка:**
```powershell
$files = (Get-ChildItem -Recurse src -Filter *.java).FullName
javac -d out -sourcepath src $files
```

**Запуск базовых методов:**
```bash
java -cp out aggregation.Main data/profile_example.json
```

**Запуск позиционно-взвешенного Кемени:**
```bash
java -cp out aggregation.PositionWeightedDemo data/skate_euros_pairs.json
```

## TODO

- [ ] Реализовать адаптивную медиану Кемени (веса из энтропии)
- [ ] Найти датасет с высокой степенью расхождения между экспертами
- [ ] Провести сравнительный анализ на разных датасетах

