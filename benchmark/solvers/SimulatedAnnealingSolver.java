package benchmark.solvers;

import java.util.*;

import benchmark.Metrics;
import benchmark.TSPSolver;

public class SimulatedAnnealingSolver
                implements TSPSolver {

        public String getName() {

                return "SA";

        }

        public int[] solve(
                        double[][] cities) {

                Random rand = new Random();

                int[] cur = Metrics.randomRoute(
                                cities.length);

                double temp = 1000;

                while (temp > 1) {

                        int[] next = cur.clone();

                        int a = rand.nextInt(
                                        next.length);

                        int b = rand.nextInt(
                                        next.length);

                        int t = next[a];

                        next[a] = next[b];

                        next[b] = t;

                        double dc =

                                        Metrics.routeCost(
                                                        next,
                                                        cities)

                                                        -

                                                        Metrics.routeCost(
                                                                        cur,
                                                                        cities);

                        if (dc < 0 ||

                                        Math.exp(
                                                        -dc / temp)

                                                        > rand.nextDouble())

                                cur = next;

                        temp *= 0.995;

                }

                return cur;

        }

}