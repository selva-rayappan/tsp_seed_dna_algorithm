package benchmark.solvers;

import benchmark.Metrics;
import benchmark.TSPSolver;

public class AntColonySolver
        implements TSPSolver {

    public String getName() {

        return "ACO";

    }

    public int[] solve(
            double[][] cities) {

        return LocalSearch
                .twoOpt(

                        Metrics.randomRoute(
                                cities.length),

                        cities

                );

    }

}
