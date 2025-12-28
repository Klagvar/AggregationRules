"""
График 2: Зависимость времени выполнения от количества экспертов
"""

import json
import matplotlib.pyplot as plt
import numpy as np

# Загрузка данных
with open('../results/timing.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Извлечение данных
experts = []
methods = ['Borda', 'Classic', 'Hyperbolic', 'Conflict', 'Consensus']
timing = {method: [] for method in methods}

for result in data['results']:
    experts.append(result['experts'])
    for method in methods:
        timing[method].append(result['timing_ms'][method])

# Настройка стиля
plt.style.use('seaborn-v0_8-whitegrid')
fig, ax = plt.subplots(figsize=(12, 7))

# Цвета и маркеры
colors = {
    'Borda': '#e74c3c',
    'Classic': '#3498db', 
    'Hyperbolic': '#2ecc71',
    'Conflict': '#9b59b6',
    'Consensus': '#f39c12'
}
markers = {
    'Borda': 'o',
    'Classic': 's',
    'Hyperbolic': '^',
    'Conflict': 'D',
    'Consensus': 'v'
}

# Построение графиков
for method in methods:
    ax.plot(experts, timing[method], 
            color=colors[method], 
            marker=markers[method],
            markersize=8,
            linewidth=2,
            label=method,
            alpha=0.8)

# Оформление
ax.set_xlabel('Количество экспертов', fontsize=12)
ax.set_ylabel('Время выполнения (мс)', fontsize=12)
ax.set_title('Зависимость времени выполнения от количества экспертов', fontsize=14)
ax.set_xscale('log')
ax.set_yscale('log')
ax.legend(loc='upper left', fontsize=10)

# Добавим сетку
ax.grid(True, which='both', linestyle='-', alpha=0.3)

plt.tight_layout()
plt.savefig('../results/graph_timing.png', dpi=150, bbox_inches='tight')
plt.savefig('../results/graph_timing.pdf', bbox_inches='tight')
print("Сохранено: results/graph_timing.png")
print("Сохранено: results/graph_timing.pdf")

plt.show()

