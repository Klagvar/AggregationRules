# AggregationRules

Программная реализация методов агрегирования индивидуальных предпочтений на языке Java. Проект включает четыре классических правила коллективного выбора: сумму рангов (Гудман—Марковиц), взвешенную сумму рангов, аддитивную полезность Харшаньи и медиану Кемени. Последняя решается через сведение к задаче о назначениях с использованием венгерского алгоритма.

## Структура проекта

```
src/aggregation/
  Main.java                  # точка входа
  model/                     # модели данных
    Alternative.java
    Ranking.java
    RankingEntry.java
    PreferenceProfile.java
    WeightMatrix.java
    UtilityVector.java
    UtilityProfile.java
    AggregatedRanking.java
  algorithms/                # алгоритмы агрегирования
    RankSumAggregator.java
    WeightedRankSumAggregator.java
    UtilityAggregator.java
  kemeny/                    # медиана Кемени
    DistanceMatrix.java
    HungarianSolver.java
    KemenyMedianSolver.java
    KemenyResult.java
  io/                        # ввод-вывод
    JsonProfileReader.java
    ProfileDataset.java
    SimpleJsonParser.java
```

### Пакеты

- **model** — классы для представления альтернатив, ранжировок, профилей предпочтений, матриц весов и полезностей.
- **algorithms** — реализации методов суммы рангов, взвешенной суммы и полезности Харшаньи.
- **kemeny** — построение матрицы стоимостей, венгерский алгоритм, поиск медианы Кемени.
- **io** — парсер JSON и загрузчик профиля.

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
- `metadata` — (опционально) описательная информация.

Полное описание формата: [`docs/json_format.md`](docs/json_format.md).

## Требования

- JDK 17 или выше
- Утилиты `javac` и `java` в PATH

Проект не использует внешних зависимостей.

## Сборка

**Windows PowerShell:**
```powershell
$files = (Get-ChildItem -Recurse src -Filter *.java).FullName
javac -d out -sourcepath src $files
```

**Unix/Linux/macOS:**
```bash
find src -name "*.java" | xargs javac -d out
```

## Запуск

**С указанием входного файла:**
```bash
java -cp out aggregation.Main data/profile_example.json
```

**Без аргументов** (используется `data/profile_example.json`):
```bash
java -cp out aggregation.Main
```

## Пример вывода

```
Dataset: Textbook Example 21.1 / 22.2
Source: Petrovsky A.B.
Rank-sum aggregation (lower is better):
 - B : 111
 - A : 122
 - C : 127

Weighted rank-sum aggregation:
 - B : 183
 - C : 214
 - A : 232

Utility aggregation (Harsanyi weighted sum):
 - A : 0.69
 - B : 0.69
 - C : 0.56

Kemeny median ranking:
 - A : rank 1
 - B : rank 2
 - C : rank 3
Total distance d* = 144

Distance matrix d_{ik}:
      k=1   k=2   k=3   
A        62    48    58 
B        51    29    69 
C        67    43    53 
```


