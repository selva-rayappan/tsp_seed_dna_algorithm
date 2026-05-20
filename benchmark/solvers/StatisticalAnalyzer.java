package benchmark.solvers;

import java.util.*;

public class StatisticalAnalyzer {

    public static double mean(List<Double> x) {

        return x.stream()

                .mapToDouble(
                        v -> v)

                .average()

                .orElse(0);

    }

    public static double std(List<Double> x) {

        double m = mean(x);

        double s = 0;

        for (double v : x)

            s += (v - m) * (v - m);

        return Math.sqrt(
                s / x.size());

    }

}