package benchmark.solvers;

import java.util.*;
import benchmark.Metrics;
import benchmark.TSPSolver;

public class SimulatedAnnealingSolver implements TSPSolver {

    @Override
    public String getName() {
        return "SA";
    }

    @Override
    public int[] solve(double[][] cities) {
        int n = cities.length;
        if (n <= 2) {
            int[] route = new int[n];
            for (int i = 0; i < n; i++) route[i] = i;
            return route;
        }

        // 1. Initial route using Greedy (Nearest Neighbor)
        int[] cur = getGreedyRoute(cities);
        double curCost = Metrics.routeCost(cur, cities);

        int[] bestRoute = cur.clone();
        double bestCost = curCost;

        // 2. Setup SA parameters
        Random rand = new Random(42); // Seeded for reproducibility in benchmarks
        int maxIter = Math.max(200000, 1000 * n);
        
        // Temperature scales with the average edge length of the initial tour
        double tStart = curCost / (n * 10.0);
        double tEnd = tStart * 0.0001;
        double temp = tStart;
        double cooling = Math.pow(0.0001, 1.0 / maxIter);

        // 3. SA Loop
        for (int iter = 0; iter < maxIter; iter++) {
            int i = rand.nextInt(n);
            int j = rand.nextInt(n);
            if (i == j) continue;
            if (i > j) {
                int t = i; i = j; j = t;
            }

            int prevI = (i - 1 + n) % n;
            int nextJ = (j + 1) % n;
            if (nextJ == i) continue; // Reversing whole tour is redundant

            int u = cur[prevI];
            int v = cur[i];
            int x = cur[j];
            int y = cur[nextJ];

            double distUV = Metrics.distance(cities[u], cities[v]);
            double distXY = Metrics.distance(cities[x], cities[y]);
            double distUX = Metrics.distance(cities[u], cities[x]);
            double distVY = Metrics.distance(cities[v], cities[y]);

            double delta = (distUX + distVY) - (distUV + distXY);

            if (delta < 0 || Math.exp(-delta / temp) > rand.nextDouble()) {
                reverse(cur, i, j);
                curCost += delta;

                if (curCost < bestCost) {
                    bestCost = curCost;
                    System.arraycopy(cur, 0, bestRoute, 0, n);
                }
            }
            temp *= cooling;
        }

        return bestRoute;
    }

    private int[] getGreedyRoute(double[][] cities) {
        int n = cities.length;
        boolean[] used = new boolean[n];
        int[] route = new int[n];
        route[0] = 0;
        used[0] = true;

        for (int k = 1; k < n; k++) {
            int prev = route[k - 1];
            double best = Double.MAX_VALUE;
            int next = -1;
            for (int j = 0; j < n; j++) {
                if (used[j]) continue;
                double d = Metrics.distance(cities[prev], cities[j]);
                if (d < best) {
                    best = d;
                    next = j;
                }
            }
            route[k] = next;
            used[next] = true;
        }
        return route;
    }

    private void reverse(int[] route, int i, int j) {
        while (i < j) {
            int temp = route[i];
            route[i] = route[j];
            route[j] = temp;
            i++;
            j--;
        }
    }
}