package benchmark.solvers;

import benchmark.Metrics;
import benchmark.TSPSolver;

public class GeneticSolver
        implements TSPSolver {

    public String getName() {

        return "GA";

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
