package benchmark.solvers;

import benchmark.Metrics;
import benchmark.TSPSolver;

public class TwoOptSolver
        implements TSPSolver {

    public String getName() {

        return "2Opt";

    }

    public int[] solve(
            double[][] cities) {

        int[] r = Metrics.randomRoute(
                cities.length);

        return LocalSearch
                .twoOpt(
                        r,
                        cities);

    }

}
