package aggregation.kemeny;

import java.util.Arrays;

/**
 * Реализация венгерского алгоритма для задачи о назначениях (минимизация стоимости).
 */
public final class HungarianSolver {
    private HungarianSolver() {
    }

    /**
     * Возвращает оптимальное назначение: для каждой строки (альтернативы) индекс выбранного столбца (ранга).
     *
     * @param costMatrix квадратная матрица стоимостей d_{ik}
     */
    public static int[] solve(double[][] costMatrix) {
        int n = costMatrix.length;
        double[][] cost = new double[n][n];
        for (int i = 0; i < n; i++) {
            cost[i] = Arrays.copyOf(costMatrix[i], n);
        }

        double[] u = new double[n + 1]; // потенциалы строк
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];

        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            boolean[] used = new boolean[n + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            Arrays.fill(used, false);
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double current = cost[i0 - 1][j - 1] - u[i0] - v[j];
                    if (current < minv[j]) {
                        minv[j] = current;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        int[] assignment = new int[n];
        for (int j = 1; j <= n; j++) {
            if (p[j] != 0) {
                assignment[p[j] - 1] = j - 1;
            }
        }
        return assignment;
    }
}

