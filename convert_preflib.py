#!/usr/bin/env python3
"""
Конвертер файлов PrefLib в формат JSON для AggregationRules.

Поддерживаемые форматы:
  - SOC (Strict Orders Complete) — строгий полный порядок
  - SOI (Strict Orders Incomplete) — строгий неполный порядок
  - TOC (Ties Orders Complete) — полный порядок с ничьими
  - TOI (Ties Orders Incomplete) — неполный порядок с ничьими
  - CAT (Categorical) — категориальные предпочтения
  - WMD (Weighted Matching Data) — взвешенные графы

Использование:
    # Базовый запуск (результат в data/<имя_файла>.json)
    python convert_preflib.py input.soc
    python convert_preflib.py input.toc
    python convert_preflib.py input.soi
    python convert_preflib.py input.toi
    python convert_preflib.py input.cat
    python convert_preflib.py input.wmd
    
    # С указанием выходного файла
    python convert_preflib.py input.soc --output data/result.json
    python convert_preflib.py input.soc -o data/result.json
    
    # Только полные ранжировки (для SOI/TOI - отфильтровать неполные)
    python convert_preflib.py input.soi --complete-only
    python convert_preflib.py input.toi -c
    
    # Конвертация в формат DASS 
    python convert_preflib.py input.soc --dass
    python convert_preflib.py input.toc --dass -o data/result_dass.json
    
    # Примеры с реальными файлами
    python convert_preflib.py preflib/00006_skate/00006-00000003.soc
    python convert_preflib.py preflib/00006_skate/00006-00000001.toc -o data/skate.json
    python convert_preflib.py preflib/00006_skate/00006-00000003.soc --dass
"""

import argparse
import json
import re
from pathlib import Path
from typing import Union


def parse_order_string(order_str: str) -> list:
    """
    Парсит строку порядка с возможными ничьими (ties).
    
    Примеры:
        "1,2,3,4" -> [1, 2, 3, 4]
        "1,{2,3},4" -> [1, [2, 3], 4]
        "{1,2},{3,4}" -> [[1, 2], [3, 4]]
    
    Returns:
        Список элементов, где ничьи представлены как вложенные списки.
    """
    result = []
    i = 0
    order_str = order_str.strip()
    
    while i < len(order_str):
        if order_str[i] == '{':
            # Найти закрывающую скобку
            j = order_str.index('}', i)
            tie_content = order_str[i+1:j]
            tie_items = [int(x.strip()) for x in tie_content.split(',') if x.strip()]
            result.append(tie_items if len(tie_items) > 1 else tie_items[0])
            i = j + 1
            # Пропустить запятую после }
            if i < len(order_str) and order_str[i] == ',':
                i += 1
        elif order_str[i] == ',':
            i += 1
        elif order_str[i].isdigit() or order_str[i] == '-':
            # Найти конец числа
            j = i
            while j < len(order_str) and (order_str[j].isdigit() or order_str[j] == '-'):
                j += 1
            result.append(int(order_str[i:j]))
            i = j
            # Пропустить запятую
            if i < len(order_str) and order_str[i] == ',':
                i += 1
        else:
            i += 1
    
    return result


def parse_category_string(cat_str: str) -> list:
    """
    Парсит строку категорий для CAT формата.
    
    Примеры:
        "1,3,{}" -> [[1], [3], []]
        "{},{1,2,3}" -> [[], [1, 2, 3]]
        "{1,2},3,{4,5}" -> [[1, 2], [3], [4, 5]]
    """
    result = []
    i = 0
    cat_str = cat_str.strip()
    
    while i < len(cat_str):
        if cat_str[i] == '{':
            j = cat_str.index('}', i)
            content = cat_str[i+1:j]
            if content.strip():
                items = [int(x.strip()) for x in content.split(',') if x.strip()]
            else:
                items = []  # Пустая категория
            result.append(items)
            i = j + 1
            if i < len(cat_str) and cat_str[i] == ',':
                i += 1
        elif cat_str[i] == ',':
            i += 1
        elif cat_str[i].isdigit():
            j = i
            while j < len(cat_str) and cat_str[j].isdigit():
                j += 1
            result.append([int(cat_str[i:j])])
            i = j
            if i < len(cat_str) and cat_str[i] == ',':
                i += 1
        else:
            i += 1
    
    return result


def ids_to_names(order: list, alternatives: dict) -> list:
    """
    Преобразует ID альтернатив в имена.
    Обрабатывает вложенные списки (ties).
    """
    result = []
    for item in order:
        if isinstance(item, list):
            # Tie — список альтернатив на одном месте
            result.append([alternatives.get(aid, f"Alt_{aid}") for aid in item])
        else:
            result.append(alternatives.get(item, f"Alt_{item}"))
    return result


def parse_preflib_ordinal(filepath: Path, complete_only: bool = False) -> dict:
    """
    Парсит файл PrefLib с порядковыми предпочтениями (SOC, SOI, TOC, TOI).
    
    Args:
        filepath: путь к файлу
        complete_only: если True, берём только полные ранжировки
    
    Returns:
        Словарь в формате AggregationRules.
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    metadata = {}
    alternatives = {}
    rankings = []
    num_alternatives = 0
    data_type = filepath.suffix[1:].lower()  # soc, soi, toc, toi
    
    for line in lines:
        line = line.strip()
        
        if not line:
            continue
        
        # Парсим заголовки
        if line.startswith('#'):
            if ': ' in line:
                key, value = line[2:].split(': ', 1)
                key = key.strip().lower().replace(' ', '_')
                
                if key == 'title':
                    metadata['title'] = value
                elif key == 'file_name':
                    metadata['source_file'] = value
                elif key == 'data_type':
                    data_type = value.lower()
                elif key == 'number_alternatives':
                    num_alternatives = int(value)
                elif key == 'number_voters':
                    metadata['total_voters'] = int(value)
                elif key.startswith('alternative_name_'):
                    alt_id = int(key.replace('alternative_name_', ''))
                    alternatives[alt_id] = value
            continue
        
        # Парсим ранжировки: "263: 2,1,3" или "1: 30,21,{2,3},4"
        if ':' in line:
            parts = line.split(':', 1)
            if len(parts) == 2:
                try:
                    voters = int(parts[0].strip())
                    order_str = parts[1].strip()
                    
                    # Парсим с учётом возможных ties
                    order_ids = parse_order_string(order_str)
                    
                    # Считаем количество альтернатив в ранжировке
                    def count_alternatives(order):
                        count = 0
                        for item in order:
                            if isinstance(item, list):
                                count += len(item)
                            else:
                                count += 1
                        return count
                    
                    order_count = count_alternatives(order_ids)
                    
                    # Для incomplete: пропускаем неполные ранжировки если complete_only
                    if complete_only and order_count < num_alternatives:
                        continue
                    
                    # Преобразуем ID в имена альтернатив
                    order_names = ids_to_names(order_ids, alternatives)
                    
                    rankings.append({
                        'order': order_names,
                        'voters': voters
                    })
                except (ValueError, IndexError) as e:
                    # Не удалось распарсить — пропускаем
                    continue
    
    # Собираем все альтернативы
    all_alternatives = [alternatives.get(i, f"Alt_{i}") for i in range(1, num_alternatives + 1)]
    
    # Определяем есть ли ties
    has_ties = any(
        any(isinstance(item, list) for item in r['order'])
        for r in rankings
    )
    
    # Определяем полные ли ранжировки
    def count_alternatives(order):
        count = 0
        for item in order:
            if isinstance(item, list):
                count += len(item)
            else:
                count += 1
        return count
    
    is_complete = all(count_alternatives(r['order']) == num_alternatives for r in rankings)
    
    result = {
        'metadata': {
            'title': metadata.get('title', filepath.stem),
            'source': f"PrefLib ({filepath.name})",
            'data_type': data_type,
            'has_ties': has_ties,
            'is_complete': is_complete,
            'total_voters': metadata.get('total_voters', sum(r['voters'] for r in rankings)),
            'num_alternatives': num_alternatives
        },
        'alternatives': all_alternatives,
        'rankings': rankings
    }
    
    return result


def parse_preflib_categorical(filepath: Path) -> dict:
    """
    Парсит файл PrefLib с категориальными предпочтениями (CAT).
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    metadata = {}
    alternatives = {}
    categories = {}
    preferences = []
    num_alternatives = 0
    num_categories = 0
    
    for line in lines:
        line = line.strip()
        
        if not line:
            continue
        
        if line.startswith('#'):
            if ': ' in line:
                key, value = line[2:].split(': ', 1)
                key = key.strip().lower().replace(' ', '_')
                
                if key == 'title':
                    metadata['title'] = value
                elif key == 'file_name':
                    metadata['source_file'] = value
                elif key == 'number_alternatives':
                    num_alternatives = int(value)
                elif key == 'number_voters':
                    metadata['total_voters'] = int(value)
                elif key == 'number_categories':
                    num_categories = int(value)
                elif key.startswith('alternative_name_'):
                    alt_id = int(key.replace('alternative_name_', ''))
                    alternatives[alt_id] = value
                elif key.startswith('category_name_'):
                    cat_id = int(key.replace('category_name_', ''))
                    categories[cat_id] = value
            continue
        
        # Парсим предпочтения: "13: 6,{1,2,3,4,5,7,8,9,10,11,12,13,14,15,16}"
        if ':' in line:
            parts = line.split(':', 1)
            if len(parts) == 2:
                try:
                    voters = int(parts[0].strip())
                    cat_str = parts[1].strip()
                    cat_assignments = parse_category_string(cat_str)
                    
                    # Преобразуем в словарь категория -> альтернативы
                    pref = {}
                    for i, items in enumerate(cat_assignments):
                        cat_name = categories.get(i + 1, f"Category_{i + 1}")
                        pref[cat_name] = [alternatives.get(aid, f"Alt_{aid}") for aid in items]
                    
                    preferences.append({
                        'categories': pref,
                        'voters': voters
                    })
                except (ValueError, IndexError):
                    continue
    
    all_alternatives = [alternatives.get(i, f"Alt_{i}") for i in range(1, num_alternatives + 1)]
    all_categories = [categories.get(i, f"Category_{i}") for i in range(1, num_categories + 1)]
    
    result = {
        'metadata': {
            'title': metadata.get('title', filepath.stem),
            'source': f"PrefLib ({filepath.name})",
            'data_type': 'cat',
            'total_voters': metadata.get('total_voters', sum(p['voters'] for p in preferences)),
            'num_alternatives': num_alternatives,
            'num_categories': num_categories
        },
        'alternatives': all_alternatives,
        'categories': all_categories,
        'preferences': preferences
    }
    
    return result


def parse_preflib_matching(filepath: Path) -> dict:
    """
    Парсит файл PrefLib с данными о матчинге (WMD).
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    metadata = {}
    alternatives = {}
    edges = []
    num_alternatives = 0
    
    for line in lines:
        line = line.strip()
        
        if not line:
            continue
        
        if line.startswith('#'):
            if ': ' in line:
                key, value = line[2:].split(': ', 1)
                key = key.strip().lower().replace(' ', '_')
                
                if key == 'title':
                    metadata['title'] = value
                elif key == 'file_name':
                    metadata['source_file'] = value
                elif key == 'number_alternatives':
                    num_alternatives = int(value)
                elif key == 'number_edges':
                    metadata['num_edges'] = int(value)
                elif key.startswith('alternative_name_'):
                    alt_id = int(key.replace('alternative_name_', ''))
                    alternatives[alt_id] = value
            continue
        
        # Парсим рёбра: "1,5,1.0"
        parts = line.split(',')
        if len(parts) == 3:
            try:
                source = int(parts[0].strip())
                target = int(parts[1].strip())
                weight = float(parts[2].strip())
                
                edges.append({
                    'source': alternatives.get(source, f"Alt_{source}"),
                    'target': alternatives.get(target, f"Alt_{target}"),
                    'weight': weight
                })
            except ValueError:
                continue
    
    all_alternatives = [alternatives.get(i, f"Alt_{i}") for i in range(1, num_alternatives + 1)]
    
    result = {
        'metadata': {
            'title': metadata.get('title', filepath.stem),
            'source': f"PrefLib ({filepath.name})",
            'data_type': 'wmd',
            'num_alternatives': num_alternatives,
            'num_edges': len(edges)
        },
        'alternatives': all_alternatives,
        'edges': edges
    }
    
    return result


def parse_preflib(filepath: Path, complete_only: bool = False) -> dict:
    """
    Автоматически определяет тип файла и парсит его.
    """
    suffix = filepath.suffix.lower()
    
    if suffix in ['.soc', '.soi', '.toc', '.toi']:
        return parse_preflib_ordinal(filepath, complete_only)
    elif suffix == '.cat':
        return parse_preflib_categorical(filepath)
    elif suffix == '.wmd':
        return parse_preflib_matching(filepath)
    else:
        raise ValueError(f"Неподдерживаемый формат файла: {suffix}")


def convert_to_dass(data: dict) -> dict:
    """
    Конвертирует данные из формата PrefLib в формат DASS.
    
    Формат DASS:
    - alternatives: список альтернатив
    - criteria: [{name, type}] — один критерий "Rank" с type="negative"
    - dms: [{id, scores}] — каждый voter становится отдельным DM
      - scores: [[rank]] для каждой альтернативы (одномерный, т.к. один критерий)
    
    Логика: order ["A", "B", "C"] означает A=1, B=2, C=3 (ранги).
    При ties (ничьих) все альтернативы в группе получают средний ранг.
    """
    if 'rankings' not in data:
        raise ValueError("Конвертация в DASS поддерживается только для ранжировок (SOC/SOI/TOC/TOI)")
    
    alternatives = data['alternatives']
    rankings = data['rankings']
    
    dms = []
    dm_counter = 1
    
    for ranking in rankings:
        order = ranking['order']
        voters = ranking['voters']
        
        # Создаём словарь альтернатива -> ранг
        alt_to_rank = {}
        current_rank = 1
        
        for item in order:
            if isinstance(item, list):
                # Tie: все альтернативы получают средний ранг
                # Например, если на позициях 2-3 ничья, обе получают 2.5
                avg_rank = current_rank + (len(item) - 1) / 2
                for alt in item:
                    alt_to_rank[alt] = avg_rank
                current_rank += len(item)
            else:
                alt_to_rank[item] = current_rank
                current_rank += 1
        
        # Для неполных ранжировок: альтернативы без ранга получают последний ранг
        max_rank = len(alternatives)
        for alt in alternatives:
            if alt not in alt_to_rank:
                alt_to_rank[alt] = max_rank
        
        # Создаём scores: [[rank]] для каждой альтернативы в порядке alternatives
        scores = [[alt_to_rank.get(alt, max_rank)] for alt in alternatives]
        
        # Создаём voters копий этого DM
        for _ in range(voters):
            dms.append({
                'id': f'DM{dm_counter}',
                'scores': scores
            })
            dm_counter += 1
    
    result = {
        'alternatives': alternatives,
        'criteria': [
            {
                'name': 'Rank',
                'type': 'negative'  # Меньше ранг = лучше
            }
        ],
        'dms': dms
    }
    
    return result


def main():
    parser = argparse.ArgumentParser(
        description='Конвертер файлов PrefLib в JSON',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Поддерживаемые форматы:
  SOC - Strict Orders Complete (строгий полный порядок)
  SOI - Strict Orders Incomplete (строгий неполный порядок)
  TOC - Ties Orders Complete (полный порядок с ничьими)
  TOI - Ties Orders Incomplete (неполный порядок с ничьими)
  CAT - Categorical (категориальные предпочтения)
  WMD - Weighted Matching Data (взвешенные графы)

Примеры:
  python convert_preflib.py data.soc
  python convert_preflib.py data.toc --output result.json
  python convert_preflib.py data.soi --complete-only
  python convert_preflib.py data.soc --dass
        """
    )
    parser.add_argument('input', type=Path, help='Входной файл PrefLib')
    parser.add_argument('--output', '-o', type=Path, 
                        help='Выходной JSON файл (по умолчанию: data/<имя_файла>.json)')
    parser.add_argument('--complete-only', '-c', action='store_true',
                        help='Только полные ранжировки (для SOI/TOI)')
    parser.add_argument('--dass', '-d', action='store_true',
                        help='Конвертировать в формат DASS (ранги как числовые оценки)')
    
    args = parser.parse_args()
    
    if not args.input.exists():
        print(f"Ошибка: Файл не найден: {args.input}")
        return 1
    
    # Определяем выходной файл
    if args.output:
        output_path = args.output
    else:
        output_path = Path('data') / f"{args.input.stem}.json"
    
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        data = parse_preflib(args.input, complete_only=args.complete_only)
        
        # Конвертация в формат DASS если указан флаг
        if args.dass:
            data = convert_to_dass(data)
    except ValueError as e:
        print(f"Ошибка: {e}")
        return 1
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    # Вывод информации
    print(f"Конвертирован: {args.input.name}")
    
    if args.dass:
        print(f"  Формат: DASS")
        print(f"  Альтернатив: {len(data['alternatives'])}")
        print(f"  Критериев: {len(data['criteria'])}")
        print(f"  Экспертов (DM): {len(data['dms'])}")
    else:
        print(f"  Тип данных: {data['metadata'].get('data_type', 'unknown')}")
        print(f"  Альтернатив: {len(data['alternatives'])}")
        
        if 'rankings' in data:
            print(f"  Ранжировок: {len(data['rankings'])}")
            print(f"  Есть ничьи: {'да' if data['metadata'].get('has_ties') else 'нет'}")
            print(f"  Полные: {'да' if data['metadata'].get('is_complete') else 'нет'}")
            print(f"  Голосов: {data['metadata'].get('total_voters', 'N/A')}")
        elif 'preferences' in data:
            print(f"  Предпочтений: {len(data['preferences'])}")
            print(f"  Категорий: {len(data.get('categories', []))}")
            print(f"  Голосов: {data['metadata'].get('total_voters', 'N/A')}")
        elif 'edges' in data:
            print(f"  Рёбер: {len(data['edges'])}")
    
    print(f"  Выход: {output_path}")
    
    return 0


if __name__ == '__main__':
    exit(main())
