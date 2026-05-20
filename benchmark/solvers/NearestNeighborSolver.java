package benchmark.solvers;

import benchmark.TSPSolver;
import benchmark.Metrics;

public class NearestNeighborSolver
        implements TSPSolver {

    public String getName() {

        return "Greedy";

    }

    public int[] solve(
            double[][] cities) {

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

                if (used[j])
                    continue;

                double d = Metrics.distance(
                        cities[prev],
                        cities[j]);

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

}
