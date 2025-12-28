"""
График: Устойчивость методов агрегации к предвзятым экспертам
"""

import json
import matplotlib.pyplot as plt
import numpy as np

# Загрузка данных
with open('../results/entropy_multi.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# ВСЕ методы
methods = ['Borda', 'Classic', 'Hyperbolic', 'Conflict', 'Consensus']

# Сортируем по noise_level
sorted_results = sorted(data['results'], key=lambda x: x['noise_level'])

# Ограничиваем до 70% предвзятых
max_noise = 0.70

noise_levels = []
weighted_score = {method: [] for method in methods}

for result in sorted_results:
    if result['noise_level'] <= max_noise:
        noise_levels.append(result['noise_level'])
        for method in methods:
            weighted_score[method].append(result['avg_weighted_score'][method])

noise_levels = np.array(noise_levels)

print(f"Точек на графике: {len(noise_levels)}")

# Настройка стиля
plt.style.use('seaborn-v0_8-whitegrid')
fig, ax = plt.subplots(figsize=(11, 6))

# Цвета и стили
colors = {
    'Borda': '#9b59b6',       # Фиолетовый
    'Classic': '#3498db',      # Синий
    'Hyperbolic': '#e74c3c',   # Красный
    'Conflict': '#f39c12',     # Оранжевый
    'Consensus': '#27ae60',    # Зелёный
}
labels = {
    'Borda': 'Borda',
    'Classic': 'Классический Kemeny',
    'Hyperbolic': 'Гиперболический Kemeny',
    'Conflict': 'Conflict Focus',
    'Consensus': 'Consensus Focus',
}

# Построение графиков
for method in methods:
    ax.plot(noise_levels, weighted_score[method], 
            color=colors[method], 
            linewidth=2,
            label=labels[method],
            alpha=0.85)

# Оформление
ax.set_xlabel('Доля ангажированных экспертов', fontsize=12)
ax.set_ylabel('Взвешенная точность', fontsize=12)
ax.set_title('Устойчивость методов агрегации к ангажированным экспертам', fontsize=13)
ax.set_xlim(-0.02, max_noise + 0.02)
ax.set_ylim(-0.1, 3.2)
ax.legend(loc='lower left', fontsize=10)

plt.tight_layout()
plt.savefig('../results/graph_entropy_accuracy.png', dpi=150, bbox_inches='tight')
plt.savefig('../results/graph_entropy_accuracy.pdf', bbox_inches='tight')
print("Saved: results/graph_entropy_accuracy.png")
print("Saved: results/graph_entropy_accuracy.pdf")

plt.show()
