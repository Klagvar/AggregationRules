#!/usr/bin/env python3
"""
Генератор ранжировок для тестирования методов агрегации.

Позволяет создавать синтетические датасеты с контролируемыми характеристиками:
- Уровень консенсуса экспертов
- Распределение согласия по позициям (топ/низ/равномерно)
- Кластеры мнений (группы экспертов с похожими предпочтениями)

Использование:
    python ranking_generator.py --alternatives 10 --experts 20 --consensus 0.5
    python ranking_generator.py -a 10 -e 20 -c 0.7 --position top -o data.json
    python ranking_generator.py --preset polarized
"""

import argparse
import json
import random
import numpy as np
from typing import List, Tuple, Optional
from pathlib import Path


class RankingGenerator:
    """Генератор синтетических ранжировок для методов агрегации."""
    
    def __init__(
        self,
        num_alternatives: int = 10,
        num_experts: int = 20,
        consensus_level: float = 0.5,
        consensus_position: str = "uniform",
        num_clusters: int = 1,
        cluster_balance: float = 0.5,
        alternative_names: Optional[List[str]] = None,
        seed: Optional[int] = None,
    ):
        """
        Инициализация генератора.
        
        Args:
            num_alternatives: Количество альтернатив (объектов для ранжирования)
            num_experts: Количество экспертов (голосующих)
            consensus_level: Уровень согласия экспертов (0.0 = хаос, 1.0 = полное согласие)
            consensus_position: Где больше согласия:
                - "uniform" — равномерно по всем позициям
                - "top" — согласие на верхних позициях, разногласия внизу
                - "bottom" — разногласия наверху, согласие внизу
            num_clusters: Количество кластеров мнений (1 = один консенсус, 2+ = группы)
            cluster_balance: Баланс между кластерами (0.5 = равные размеры)
            alternative_names: Имена альтернатив (если None — генерируются автоматически)
            seed: Seed для воспроизводимости результатов
        """
        if not 0 <= consensus_level <= 1:
            raise ValueError("consensus_level должен быть от 0 до 1")
        if consensus_position not in ("uniform", "top", "bottom"):
            raise ValueError("consensus_position должен быть 'uniform', 'top' или 'bottom'")
        if num_clusters < 1:
            raise ValueError("num_clusters должен быть >= 1")
        
        self.num_alternatives = num_alternatives
        self.num_experts = num_experts
        self.consensus_level = consensus_level
        self.consensus_position = consensus_position
        self.num_clusters = num_clusters
        self.cluster_balance = cluster_balance
        self.seed = seed
        
        if seed is not None:
            random.seed(seed)
            np.random.seed(seed)
        
        # Имена альтернатив
        if alternative_names:
            if len(alternative_names) != num_alternatives:
                raise ValueError(f"Нужно {num_alternatives} имён альтернатив")
            self.alternatives = alternative_names
        else:
            self.alternatives = [f"A{i+1}" for i in range(num_alternatives)]
        
        self.rankings: List[dict] = []
        self.cluster_centers: List[List[str]] = []
    
    def _generate_cluster_centers(self) -> List[List[str]]:
        """Генерирует центральные ранжировки для каждого кластера."""
        centers = []
        
        # Первый кластер — случайная перестановка
        base = self.alternatives.copy()
        random.shuffle(base)
        centers.append(base)
        
        # Остальные кластеры — перевёрнутые или сдвинутые версии
        for i in range(1, self.num_clusters):
            if i == 1:
                # Второй кластер — обратный порядок
                centers.append(base[::-1])
            else:
                # Остальные — случайные перестановки с частичным сохранением
                new_center = base.copy()
                # Перемешиваем часть
                swap_count = len(base) // 2
                for _ in range(swap_count):
                    i1, i2 = random.sample(range(len(base)), 2)
                    new_center[i1], new_center[i2] = new_center[i2], new_center[i1]
                centers.append(new_center)
        
        return centers
    
    def _get_position_weight(self, position: int) -> float:
        """
        Возвращает вес позиции для определения вероятности изменения.
        
        Чем выше вес — тем больше вероятность изменения (меньше консенсус).
        """
        n = self.num_alternatives
        normalized_pos = position / (n - 1) if n > 1 else 0  # 0 = топ, 1 = низ
        
        if self.consensus_position == "uniform":
            return 1.0
        elif self.consensus_position == "top":
            # Консенсус на топе: маленький вес вверху, большой внизу
            return 0.2 + 0.8 * normalized_pos
        elif self.consensus_position == "bottom":
            # Консенсус внизу: большой вес вверху, маленький внизу
            return 1.0 - 0.8 * normalized_pos
        return 1.0
    
    def _perturb_ranking(self, base: List[str]) -> List[str]:
        """
        Вносит случайные изменения в ранжировку с учётом уровня консенсуса
        и распределения по позициям.
        """
        result = base.copy()
        n = len(result)
        
        # Количество свопов зависит от уровня консенсуса
        # consensus_level = 1 -> 0 свопов
        # consensus_level = 0 -> много свопов (полный shuffle)
        max_swaps = n * (n - 1) // 2  # Максимум свопов для полной перестановки
        num_swaps = int(max_swaps * (1 - self.consensus_level) * 0.5)
        
        for _ in range(num_swaps):
            # Выбираем позицию с учётом весов
            weights = [self._get_position_weight(i) for i in range(n - 1)]
            total = sum(weights)
            if total == 0:
                break
            probs = [w / total for w in weights]
            
            # Выбираем позицию для свопа
            pos = np.random.choice(range(n - 1), p=probs)
            
            # Меняем с соседом или случайным элементом
            if random.random() < 0.7:
                # Своп с соседом (более реалистично)
                swap_pos = pos + 1
            else:
                # Своп с случайным
                swap_pos = random.randint(0, n - 1)
            
            result[pos], result[swap_pos] = result[swap_pos], result[pos]
        
        return result
    
    def _assign_experts_to_clusters(self) -> List[int]:
        """Распределяет экспертов по кластерам."""
        if self.num_clusters == 1:
            return [0] * self.num_experts
        
        assignments = []
        remaining = self.num_experts
        
        for i in range(self.num_clusters - 1):
            if i == 0:
                # Первый кластер получает долю по cluster_balance
                count = int(self.num_experts * self.cluster_balance)
            else:
                # Остальные делят поровну
                count = remaining // (self.num_clusters - i)
            
            assignments.extend([i] * count)
            remaining -= count
        
        # Последний кластер получает остаток
        assignments.extend([self.num_clusters - 1] * remaining)
        
        random.shuffle(assignments)
        return assignments
    
    def generate(self) -> List[dict]:
        """
        Генерирует ранжировки.
        
        Returns:
            Список ранжировок в формате [{"order": [...], "voters": 1}, ...]
        """
        self.cluster_centers = self._generate_cluster_centers()
        cluster_assignments = self._assign_experts_to_clusters()
        
        rankings_raw = []
        
        for expert_id in range(self.num_experts):
            cluster_id = cluster_assignments[expert_id]
            base = self.cluster_centers[cluster_id]
            ranking = self._perturb_ranking(base)
            rankings_raw.append(tuple(ranking))
        
        # Группируем одинаковые ранжировки
        from collections import Counter
        ranking_counts = Counter(rankings_raw)
        
        self.rankings = [
            {"order": list(ranking), "voters": count}
            for ranking, count in ranking_counts.items()
        ]
        
        return self.rankings
    
    def to_dict(self) -> dict:
        """Преобразует в словарь для JSON."""
        return {
            "metadata": {
                "generator": "RankingGenerator",
                "num_alternatives": self.num_alternatives,
                "num_experts": self.num_experts,
                "consensus_level": self.consensus_level,
                "consensus_position": self.consensus_position,
                "num_clusters": self.num_clusters,
                "cluster_balance": self.cluster_balance,
                "seed": self.seed,
            },
            "alternatives": self.alternatives,
            "rankings": self.rankings,
        }
    
    def export_json(self, filepath: str):
        """Экспортирует в JSON файл."""
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(self.to_dict(), f, indent=2, ensure_ascii=False)
        print(f"Сохранено: {filepath}")
    
    def print_stats(self):
        """Выводит статистику сгенерированных данных."""
        print(f"\n=== Статистика генерации ===")
        print(f"Альтернатив: {self.num_alternatives}")
        print(f"Экспертов: {self.num_experts}")
        print(f"Уровень консенсуса: {self.consensus_level}")
        print(f"Распределение консенсуса: {self.consensus_position}")
        print(f"Кластеров: {self.num_clusters}")
        print(f"Уникальных ранжировок: {len(self.rankings)}")
        
        if self.cluster_centers:
            print(f"\nЦентры кластеров:")
            for i, center in enumerate(self.cluster_centers):
                print(f"  Кластер {i+1}: {' > '.join(center[:3])}...")


# Пресеты для типичных сценариев
PRESETS = {
    "high_consensus": {
        "consensus_level": 0.9,
        "consensus_position": "uniform",
        "num_clusters": 1,
        "description": "Высокий консенсус (судейство с явным лидером)"
    },
    "low_consensus": {
        "consensus_level": 0.2,
        "consensus_position": "uniform",
        "num_clusters": 1,
        "description": "Низкий консенсус (субъективные предпочтения)"
    },
    "top_consensus": {
        "consensus_level": 0.6,
        "consensus_position": "top",
        "num_clusters": 1,
        "description": "Согласие на топе, споры внизу (конкурс с явным победителем)"
    },
    "bottom_consensus": {
        "consensus_level": 0.6,
        "consensus_position": "bottom",
        "num_clusters": 1,
        "description": "Споры на топе, согласие внизу (выборы с сильными кандидатами)"
    },
    "polarized": {
        "consensus_level": 0.7,
        "consensus_position": "uniform",
        "num_clusters": 2,
        "cluster_balance": 0.5,
        "description": "Поляризованные мнения (две группы с противоположными взглядами)"
    },
    "three_factions": {
        "consensus_level": 0.7,
        "consensus_position": "uniform",
        "num_clusters": 3,
        "description": "Три фракции (коалиционная политика)"
    },
}


def main():
    parser = argparse.ArgumentParser(
        description='Генератор ранжировок для тестирования методов агрегации',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Пресеты:
  high_consensus  - Высокий консенсус (судейство)
  low_consensus   - Низкий консенсус (субъективные вкусы)
  top_consensus   - Согласие на топе, споры внизу
  bottom_consensus - Споры на топе, согласие внизу
  polarized       - Две противоположные группы
  three_factions  - Три фракции

Примеры:
  python ranking_generator.py --preset polarized -o polarized.json
  python ranking_generator.py -a 10 -e 30 -c 0.5 --position bottom
        """
    )
    
    parser.add_argument('-a', '--alternatives', type=int, default=10,
                        help='Количество альтернатив (default: 10)')
    parser.add_argument('-e', '--experts', type=int, default=20,
                        help='Количество экспертов (default: 20)')
    parser.add_argument('-c', '--consensus', type=float, default=0.5,
                        help='Уровень консенсуса 0-1 (default: 0.5)')
    parser.add_argument('--position', choices=['uniform', 'top', 'bottom'],
                        default='uniform', help='Распределение консенсуса по позициям')
    parser.add_argument('--clusters', type=int, default=1,
                        help='Количество кластеров мнений (default: 1)')
    parser.add_argument('--balance', type=float, default=0.5,
                        help='Баланс между кластерами (default: 0.5)')
    parser.add_argument('--seed', type=int, default=None,
                        help='Seed для воспроизводимости')
    parser.add_argument('--preset', choices=list(PRESETS.keys()),
                        help='Использовать пресет настроек')
    parser.add_argument('-o', '--output', type=str, default=None,
                        help='Выходной JSON файл')
    parser.add_argument('--names', type=str, default=None,
                        help='Имена альтернатив через запятую')
    
    args = parser.parse_args()
    
    # Применяем пресет если указан
    kwargs = {
        "num_alternatives": args.alternatives,
        "num_experts": args.experts,
        "consensus_level": args.consensus,
        "consensus_position": args.position,
        "num_clusters": args.clusters,
        "cluster_balance": args.balance,
        "seed": args.seed,
    }
    
    if args.preset:
        preset = PRESETS[args.preset]
        print(f"Используется пресет '{args.preset}': {preset['description']}")
        for key, value in preset.items():
            if key != "description" and key in kwargs:
                kwargs[key.replace("consensus_position", "consensus_position")] = value
        # Применяем параметры пресета
        if "consensus_level" in preset:
            kwargs["consensus_level"] = preset["consensus_level"]
        if "consensus_position" in preset:
            kwargs["consensus_position"] = preset["consensus_position"]
        if "num_clusters" in preset:
            kwargs["num_clusters"] = preset["num_clusters"]
        if "cluster_balance" in preset:
            kwargs["cluster_balance"] = preset["cluster_balance"]
    
    if args.names:
        kwargs["alternative_names"] = [n.strip() for n in args.names.split(',')]
    
    # Генерация
    generator = RankingGenerator(**kwargs)
    generator.generate()
    generator.print_stats()
    
    # Вывод примера
    print(f"\nПример ранжировок:")
    for r in generator.rankings[:3]:
        print(f"  {r['voters']} голосов: {' > '.join(r['order'][:5])}...")
    
    # Сохранение
    if args.output:
        generator.export_json(args.output)
    else:
        # По умолчанию сохраняем в data/
        Path('data').mkdir(exist_ok=True)
        filename = f"data/generated_{args.preset or 'custom'}.json"
        generator.export_json(filename)


if __name__ == '__main__':
    main()

