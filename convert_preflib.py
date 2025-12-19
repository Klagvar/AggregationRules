#!/usr/bin/env python3
"""
Конвертирует файлы PrefLib (.soc, .soi) в формат JSON для AggregationRules.

Использование:
    python convert_preflib.py preflib/00004_netflix/00004-00000001.soc
    python convert_preflib.py preflib/00004_netflix/00004-00000001.soc --output data/netflix_001.json
    python convert_preflib.py preflib/00001_irish/00001-00000002.soi --complete-only
"""

import argparse
import json
import re
from pathlib import Path


def parse_preflib(filepath: Path, complete_only: bool = False) -> dict:
    """
    Парсит файл PrefLib и возвращает словарь в формате AggregationRules.
    
    Args:
        filepath: путь к .soc или .soi файлу
        complete_only: если True, берём только полные ранжировки (для .soi)
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    metadata = {}
    alternatives = {}
    rankings = []
    num_alternatives = 0
    
    for line in lines:
        line = line.strip()
        
        # Пропускаем пустые строки
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
                elif key == 'number_alternatives':
                    num_alternatives = int(value)
                elif key == 'number_voters':
                    metadata['total_voters'] = int(value)
                elif key.startswith('alternative_name_'):
                    alt_id = int(key.replace('alternative_name_', ''))
                    alternatives[alt_id] = value
            continue
        
        # Парсим ранжировки: "263: 2,1,3"
        if ':' in line:
            parts = line.split(':')
            if len(parts) == 2:
                try:
                    voters = int(parts[0].strip())
                    order_ids = [int(x.strip()) for x in parts[1].split(',')]
                    
                    # Для .soi: пропускаем неполные ранжировки если complete_only
                    if complete_only and len(order_ids) < num_alternatives:
                        continue
                    
                    # Преобразуем ID в имена альтернатив
                    order_names = [alternatives.get(aid, f"Alt_{aid}") for aid in order_ids]
                    
                    rankings.append({
                        'order': order_names,
                        'voters': voters
                    })
                except ValueError:
                    # Не удалось распарсить — пропускаем
                    continue
    
    # Собираем все альтернативы
    all_alternatives = [alternatives.get(i, f"Alt_{i}") for i in range(1, num_alternatives + 1)]
    
    # Формируем результат
    result = {
        'metadata': {
            'title': metadata.get('title', filepath.stem),
            'source': f"PrefLib ({filepath.name})",
            'total_voters': metadata.get('total_voters', sum(r['voters'] for r in rankings))
        },
        'alternatives': all_alternatives,
        'rankings': rankings
    }
    
    return result


def main():
    parser = argparse.ArgumentParser(description='Convert PrefLib files to JSON')
    parser.add_argument('input', type=Path, help='Input .soc or .soi file')
    parser.add_argument('--output', '-o', type=Path, help='Output JSON file (default: data/<input_name>.json)')
    parser.add_argument('--complete-only', '-c', action='store_true', 
                        help='For .soi files: only include complete rankings')
    
    args = parser.parse_args()
    
    if not args.input.exists():
        print(f"Error: File not found: {args.input}")
        return 1
    
    # Определяем выходной файл
    if args.output:
        output_path = args.output
    else:
        output_path = Path('data') / f"{args.input.stem}.json"
    
    # Создаём директорию если нужно
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    # Парсим и сохраняем
    data = parse_preflib(args.input, complete_only=args.complete_only)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print(f"Converted: {args.input.name}")
    print(f"  Alternatives: {len(data['alternatives'])}")
    print(f"  Rankings: {len(data['rankings'])}")
    print(f"  Total voters: {data['metadata']['total_voters']}")
    print(f"  Output: {output_path}")
    
    return 0


if __name__ == '__main__':
    exit(main())

