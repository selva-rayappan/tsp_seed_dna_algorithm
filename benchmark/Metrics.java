package benchmark;

import java.util.*;

public class Metrics {

    public static double distance(
            double[] a,
            double[] b) {

        double dx = a[0] - b[0];
        double dy = a[1] - b[1];

        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double routeCost(
            int[] route,
            double[][] cities) {

        double total = 0;

        for (int i = 0; i < route.length; i++) {

            int a = route[i];

            int b = route[(i + 1) % route.length];

            total += distance(
                    cities[a],
                    cities[b]);
        }

        return total;
    }

    public static double mean(
            List<Double> vals) {

        return vals.stream()
                .mapToDouble(x -> x)
                .average()
                .orElse(0);
    }

    public static double stddev(
            List<Double> vals) {

        double mean = mean(vals);

        double s = 0;

        for (double v : vals) {

            s += (v - mean) * (v - mean);

        }

        return Math.sqrt(
                s / vals.size());
    }

    public static int[] randomRoute(int n) {
        int[] route = new int[n];
        for (int i = 0; i < n; i++) {
            route[i] = i;
        }
        Random r = new Random();
        for (int i = n - 1; i > 0; i--) {
            int index = r.nextInt(i + 1);
            int temp = route[index];
            route[index] = route[i];
            route[i] = temp;
        }
        return route;
    }

}
