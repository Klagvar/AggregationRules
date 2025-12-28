#!/usr/bin/env python3
"""
Генератор ранжировок для тестирования методов агрегации.

Позволяет создавать синтетические датасеты с контролируемыми характеристиками:
- Уровень консенсуса экспертов
- Распределение согласия по позициям (топ/низ/равномерно)
- Кластеры мнений (группы экспертов с похожими предпочтениями)

Использование:
    python ranking_generator.py --preset high_consensus
    python ranking_generator.py -a 10 -e 20 -c 0.7 --position top -o data.json
    
Батч-генерация для экспериментов:
    python ranking_generator.py --batch entropy    # Серия с разной энтропией
    python ranking_generator.py --batch timing     # Серия для замеров времени
"""

import argparse
import json
import random
import numpy as np
from typing import List, Optional
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
                swap_count = len(base) // 2
                for _ in range(swap_count):
                    i1, i2 = random.sample(range(len(base)), 2)
                    new_center[i1], new_center[i2] = new_center[i2], new_center[i1]
                centers.append(new_center)
        
        return centers
    
    def _get_position_weight(self, position: int) -> float:
        """Возвращает вес позиции для определения вероятности изменения."""
        n = self.num_alternatives
        normalized_pos = position / (n - 1) if n > 1 else 0
        
        if self.consensus_position == "uniform":
            return 1.0
        elif self.consensus_position == "top":
            return 0.2 + 0.8 * normalized_pos
        elif self.consensus_position == "bottom":
            return 1.0 - 0.8 * normalized_pos
        return 1.0
    
    def _perturb_ranking(self, base: List[str]) -> List[str]:
        """Вносит случайные изменения в ранжировку."""
        result = base.copy()
        n = len(result)
        
        max_swaps = n * (n - 1) // 2
        num_swaps = int(max_swaps * (1 - self.consensus_level) * 0.5)
        
        for _ in range(num_swaps):
            weights = [self._get_position_weight(i) for i in range(n - 1)]
            total = sum(weights)
            if total == 0:
                break
            probs = [w / total for w in weights]
            
            pos = np.random.choice(range(n - 1), p=probs)
            
            if random.random() < 0.7:
                swap_pos = pos + 1
            else:
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
                count = int(self.num_experts * self.cluster_balance)
            else:
                count = remaining // (self.num_clusters - i)
            
            assignments.extend([i] * count)
            remaining -= count
        
        assignments.extend([self.num_clusters - 1] * remaining)
        random.shuffle(assignments)
        return assignments
    
    def generate(self) -> List[dict]:
        """Генерирует ранжировки."""
        self.cluster_centers = self._generate_cluster_centers()
        cluster_assignments = self._assign_experts_to_clusters()
        
        rankings_raw = []
        
        for expert_id in range(self.num_experts):
            cluster_id = cluster_assignments[expert_id]
            base = self.cluster_centers[cluster_id]
            ranking = self._perturb_ranking(base)
            rankings_raw.append(tuple(ranking))
        
        from collections import Counter
        ranking_counts = Counter(rankings_raw)
        
        self.rankings = [
            {"order": list(ranking), "voters": count}
            for ranking, count in ranking_counts.items()
        ]
        
        return self.rankings
    
    def get_ground_truth(self) -> List[str]:
        """Возвращает эталонное ранжирование (центр первого кластера)."""
        if not self.cluster_centers:
            raise ValueError("Сначала вызовите generate()")
        return self.cluster_centers[0]
    
    def to_dict(self) -> dict:
        """Преобразует в словарь для JSON."""
        result = {
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
        
        if self.cluster_centers:
            result["ground_truth"] = self.cluster_centers[0]
        
        return result
    
    def export_json(self, filepath: str):
        """Экспортирует в JSON файл."""
        Path(filepath).parent.mkdir(parents=True, exist_ok=True)
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
            print(f"\nЭталон (ground_truth): {' > '.join(self.cluster_centers[0][:5])}...")


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


def generate_entropy_series(output_dir: str = "data/entropy",
                            num_alternatives: int = 30,
                            num_experts: int = 100,
                            num_seeds: int = 50,
                            noise_step: float = 0.02):
    """
    Генерирует датасеты для графика "Доля предвзятых экспертов → Точность".
    
    Сценарий:
    - A1, A2, A3 = "настоящие лидеры" (стабильно в топ-6 у ВСЕХ)
    - A28, A29, A30 = "аутсайдеры" (в конце у нормальных, в топ-3 у предвзятых)
    
    Нормальные эксперты (100-X%):
    - A1, A2, A3 на позициях 1-3 (случайный порядок)
    - A28, A29, A30 в конце
    
    Предвзятые эксперты (X%):
    - A28, A29, A30 строго на 1, 2, 3
    - A1, A2, A3 на позициях 4, 5, 6
    
    При X > 33%: A28 имеет больше первых мест чем A1 → Classic ошибается.
    Hyperbolic видит что A1, A2, A3 стабильнее в топе у 100% → выбирает их.
    """
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    # Удаляем старые файлы
    import shutil
    for item in Path(output_dir).iterdir():
        if item.is_dir():
            shutil.rmtree(item)
        else:
            item.unlink()
    
    noise_levels = np.arange(0.0, 1.01, noise_step)
    
    n = num_alternatives  # 30 альтернатив
    alternatives = [f"A{i}" for i in range(1, n + 1)]
    ground_truth = ["A1", "A2", "A3"]  # Настоящие лидеры
    outsiders = ["A28", "A29", "A30"]  # Аутсайдеры (последние 3)
    middle = [f"A{i}" for i in range(4, 28)]  # A4-A27: середнячки
    
    print(f"=== Генерация: предвзятые эксперты ===")
    print(f"Директория: {output_dir}")
    print(f"Альтернатив: {n}, Экспертов: {num_experts}")
    print(f"Лидеры (ground truth): {ground_truth}")
    print(f"Аутсайдеры: {outsiders}")
    print(f"Нормальные: A1,A2,A3 в топ-3, аутсайдеры в конце")
    print(f"Предвзятые: A28,A29,A30 в топ-3, A1,A2,A3 на 4,5,6")
    print(f"Уровней: {len(noise_levels)}, Seed'ов: {num_seeds}")
    print()
    
    for noise in noise_levels:
        level_dir = Path(output_dir) / f"n{noise:.2f}"
        level_dir.mkdir(parents=True, exist_ok=True)
        
        for seed_i in range(num_seeds):
            np.random.seed(seed_i)
            random.seed(seed_i)
            
            rankings_raw = []
            
            num_biased = int(num_experts * noise)
            num_normal = num_experts - num_biased
            
            # Нормальные эксперты: A1, A2, A3 в топ-3, аутсайдеры в конце
            for _ in range(num_normal):
                # Топ-3: A1, A2, A3 в случайном порядке
                top = ground_truth.copy()
                np.random.shuffle(top)
                
                # Середина: A4-A27 в случайном порядке
                mid = middle.copy()
                np.random.shuffle(mid)
                
                # Конец: A28, A29, A30 в случайном порядке
                bottom = outsiders.copy()
                np.random.shuffle(bottom)
                
                order = top + mid + bottom
                rankings_raw.append(order)
            
            # Предвзятые эксперты: A28, A29, A30 в топ-3, A1, A2, A3 на 4, 5, 6
            for _ in range(num_biased):
                # Топ-3: A28, A29, A30 (фиксированный порядок!)
                top = outsiders.copy()  # A28 всегда первый
                
                # Позиции 4-6: A1, A2, A3 в случайном порядке
                second_tier = ground_truth.copy()
                np.random.shuffle(second_tier)
                
                # Остальные: A4-A27 в случайном порядке
                rest = middle.copy()
                np.random.shuffle(rest)
                
                order = top + second_tier + rest
                rankings_raw.append(order)
            
            # Агрегируем одинаковые
            from collections import Counter
            ranking_counts = Counter(tuple(r) for r in rankings_raw)
            
            data = {
                "metadata": {
                    "generator": "biased_experts",
                    "num_alternatives": n,
                    "num_experts": num_experts,
                    "noise_level": float(noise),
                    "seed": seed_i,
                },
                "alternatives": alternatives,
                "ground_truth": ground_truth,  # Только A1, A2, A3
                "rankings": [
                    {"order": list(r), "voters": count}
                    for r, count in ranking_counts.items()
                ]
            }
            
            filepath = level_dir / f"seed_{seed_i:03d}.json"
            with open(filepath, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        
        print(f"  noise={noise:.0%}: {num_seeds} датасетов")
    
    print(f"\nГотово!")




def generate_timing_series(output_dir: str = "data/timing",
                           num_alternatives: int = 10,
                           seed: int = 42):
    """
    Генерирует серию датасетов с разным количеством экспертов для замера времени.
    """
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    expert_counts = [10, 50, 100, 500, 1000, 5000, 10000, 50000, 100000]
    
    print(f"=== Генерация серии для замера времени ===")
    print(f"Директория: {output_dir}")
    print(f"Датасетов: {len(expert_counts)}")
    print()
    
    for num_experts in expert_counts:
        generator = RankingGenerator(
            num_alternatives=num_alternatives,
            num_experts=num_experts,
            consensus_level=0.5,
            consensus_position="uniform",
            seed=seed,
        )
        generator.generate()
        
        filename = f"timing_e{num_experts:06d}.json"
        filepath = Path(output_dir) / filename
        generator.export_json(str(filepath))
    
    print(f"\nГотово! Создано {len(expert_counts)} датасетов")




def main():
    parser = argparse.ArgumentParser(
        description='Генератор ранжировок для тестирования методов агрегации',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Пресеты:
  high_consensus   - Высокий консенсус (судейство)
  low_consensus    - Низкий консенсус (субъективные вкусы)
  top_consensus    - Согласие на топе, споры внизу
  bottom_consensus - Споры на топе, согласие внизу
  polarized        - Две противоположные группы
  three_factions   - Три фракции

Батч-генерация:
  --batch entropy       - Серия с консенсусным топ-3 (100 seed'ов) → data/entropy/
  --batch timing        - Серия для замера времени → data/timing/

Примеры:
  python ranking_generator.py --preset high_consensus
  python ranking_generator.py --preset polarized -o data/standard/polarized.json
  python ranking_generator.py -a 10 -e 30 -c 0.5 --position bottom
  python ranking_generator.py --batch entropy
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
    parser.add_argument('--seed', type=int, default=42,
                        help='Seed для воспроизводимости (default: 42)')
    parser.add_argument('--preset', choices=list(PRESETS.keys()),
                        help='Использовать пресет настроек')
    parser.add_argument('-o', '--output', type=str, default=None,
                        help='Выходной JSON файл')
    parser.add_argument('--batch', choices=['entropy', 'timing'],
                        help='Батч-генерация для экспериментов')
    parser.add_argument('--num-seeds', type=int, default=100,
                        help='Количество seed\'ов для entropy-multi (default: 100)')
    
    args = parser.parse_args()
    
    # Батч-генерация
    if args.batch == 'entropy':
        generate_entropy_series(num_seeds=args.num_seeds)
        return
    elif args.batch == 'timing':
        generate_timing_series(seed=args.seed)
        return
    
    # Параметры генерации
    kwargs = {
        "num_alternatives": args.alternatives,
        "num_experts": args.experts,
        "consensus_level": args.consensus,
        "consensus_position": args.position,
        "num_clusters": args.clusters,
        "cluster_balance": args.balance,
        "seed": args.seed,
    }
    
    # Применяем пресет если указан
    if args.preset:
        preset = PRESETS[args.preset]
        print(f"Используется пресет '{args.preset}': {preset['description']}")
        kwargs["consensus_level"] = preset["consensus_level"]
        kwargs["consensus_position"] = preset["consensus_position"]
        kwargs["num_clusters"] = preset.get("num_clusters", 1)
        kwargs["cluster_balance"] = preset.get("cluster_balance", 0.5)
    
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
    elif args.preset:
        generator.export_json(f"data/standard/{args.preset}.json")
    else:
        generator.export_json("data/generated_custom.json")


if __name__ == '__main__':
    main()
