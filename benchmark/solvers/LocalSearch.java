package benchmark.solvers;

import benchmark.Metrics;

public class LocalSearch {

    public static int[] twoOpt(

            int[] route,

            double[][] cities) {

        boolean improved = true;

        while (improved) {

            improved = false;

            double best = Metrics.routeCost(
                    route,
                    cities);

            for (int i = 1; i < route.length - 2; i++) {

                for (int j = i + 1; j < route.length; j++) {

                    int[] copy = route.clone();

                    reverse(
                            copy,
                            i,
                            j);

                    double cost = Metrics.routeCost(
                            copy,
                            cities);

                    if (cost < best) {

                        route = copy;

                        best = cost;

                        improved = true;

                    }

                }

            }

        }

        return route;

    }

    private static void reverse(int[] route, int i, int j) {
        while (i < j) {
            int temp = route[i];
            route[i] = route[j];
            route[j] = temp;
            i++;
            j--;
        }
    }

}