package benchmark;

import java.io.*;
import java.util.*;
import benchmark.solvers.*;

public class BenchmarkRunner {

    static final int RUNS = 30;

    static List<TSPSolver> solvers = List.of(

            new NearestNeighborSolver(),
            new TwoOptSolver(),
            new SimulatedAnnealingSolver(),
            new GeneticSolver(),
            new AntColonySolver(),

            new SeedMiningSolver());

    static Map<String, Double> optimal = Map.of(

            "berlin52", 7542.0,
            "eil51", 426.0,
            "kroA100", 21282.0);

    public static void main(
            String[] args)
            throws Exception {

        List<String> datasets = List.of(

                "berlin52",
                "eil51",
                "kroA100"

        );

        FileWriter csv = new FileWriter(
                "benchmark/output/results.csv");

        csv.write(
                "Dataset,Algorithm,"
                        + "BestCost,"
                        + "MeanCost,"
                        + "StdDev,"
                        + "Gap,"
                        + "Runtime\n");

        for (String ds : datasets) {

            double[][] cities = DatasetLoader.load(
                    "benchmark/datasets/"
                            + ds + ".tsp");

            for (TSPSolver solver : solvers) {

                benchmarkSolver(
                        solver,
                        ds,
                        cities,
                        csv);

            }

        }

        csv.close();

        benchmarkScaling();

        benchmarkParallel();

        benchmarkAblation();
    }

    static void benchmarkSolver(

            TSPSolver solver,

            String dataset,

            double[][] cities,

            FileWriter csv)

            throws Exception {

        List<Double> costs = new ArrayList<>();

        List<Double> runtimes = new ArrayList<>();

        double best = Double.MAX_VALUE;

        for (int r = 0; r < RUNS; r++) {

            long t0 = System.nanoTime();

            int[] route = solver.solve(
                    cities);

            long t1 = System.nanoTime();

            double runtime = (t1 - t0)
                    / 1e6;

            double cost = Metrics.routeCost(
                    route,
                    cities);

            runtimes.add(
                    runtime);

            costs.add(cost);

            best = Math.min(
                    best,
                    cost);

        }

        double mean = Metrics.mean(
                costs);

        double sd = Metrics.stddev(
                costs);

        double rt = Metrics.mean(
                runtimes);

        double opt = optimal.get(
                dataset);

        double gap = ((mean - opt)
                / opt)
                * 100;

        csv.write(

                dataset + ","
                        + solver.getName()
                        + ","
                        + best
                        + ","
                        + mean
                        + ","
                        + sd
                        + ","
                        + gap
                        + ","
                        + rt
                        + "\n");

    }

    static void benchmarkScaling() throws Exception {
        int[] sizes = {10, 20, 30, 40, 50, 60};
        int runs = 10;
        FileWriter csv = new FileWriter("benchmark/output/scaling.csv");
        csv.write("Size,Algorithm,BestCost,MeanCost,StdDev,Runtime\n");

        for (int size : sizes) {
            for (TSPSolver solver : solvers) {
                List<Double> costs = new ArrayList<>();
                List<Double> runtimes = new ArrayList<>();
                double best = Double.MAX_VALUE;

                for (int r = 0; r < runs; r++) {
                    double[][] cities = CityGenerator.generate(size);
                    long t0 = System.nanoTime();
                    int[] route = solver.solve(cities);
                    long t1 = System.nanoTime();
                    double runtime = (t1 - t0) / 1e6;
                    double cost = Metrics.routeCost(route, cities);

                    costs.add(cost);
                    runtimes.add(runtime);
                    best = Math.min(best, cost);
                }

                double mean = Metrics.mean(costs);
                double sd = Metrics.stddev(costs);
                double rt = Metrics.mean(runtimes);

                csv.write(size + "," + solver.getName() + "," + best + "," + mean + "," + sd + "," + rt + "\n");
            }
        }
        csv.close();
    }

    static void benchmarkParallel() throws Exception {
        int[] threadCounts = {1, 2, 4, 8};
        int size = 50;
        int runs = 10;
        FileWriter csv = new FileWriter("benchmark/output/parallel.csv");
        csv.write("Threads,BestCost,MeanCost,Runtime\n");

        for (int threads : threadCounts) {
            SeedMiningSolver solver = new SeedMiningSolver(threads);
            List<Double> costs = new ArrayList<>();
            List<Double> runtimes = new ArrayList<>();
            double best = Double.MAX_VALUE;

            for (int r = 0; r < runs; r++) {
                double[][] cities = CityGenerator.generate(size);
                long t0 = System.nanoTime();
                int[] route = solver.solve(cities);
                long t1 = System.nanoTime();
                double runtime = (t1 - t0) / 1e6;
                double cost = Metrics.routeCost(route, cities);

                costs.add(cost);
                runtimes.add(runtime);
                best = Math.min(best, cost);
            }

            double mean = Metrics.mean(costs);
            double rt = Metrics.mean(runtimes);

            csv.write(threads + "," + best + "," + mean + "," + rt + "\n");
        }
        csv.close();
    }

    static void benchmarkAblation() throws Exception {
        int size = 50;
        int runs = 10;
        FileWriter csv = new FileWriter("benchmark/output/ablation.csv");
        csv.write("Variant,BestCost,MeanCost,Runtime\n");

        List<SeedMiningSolver> variants = List.of(
            new SeedMiningSolver(true, true, true),   // Full
            new SeedMiningSolver(false, true, true),  // NoSeedMining
            new SeedMiningSolver(true, false, true),  // NoPenalization
            new SeedMiningSolver(true, true, false)   // NoOverlap
        );

        for (SeedMiningSolver solver : variants) {
            List<Double> costs = new ArrayList<>();
            List<Double> runtimes = new ArrayList<>();
            double best = Double.MAX_VALUE;

            for (int r = 0; r < runs; r++) {
                double[][] cities = CityGenerator.generate(size);
                long t0 = System.nanoTime();
                int[] route = solver.solve(cities);
                long t1 = System.nanoTime();
                double runtime = (t1 - t0) / 1e6;
                double cost = Metrics.routeCost(route, cities);

                costs.add(cost);
                runtimes.add(runtime);
                best = Math.min(best, cost);
            }

            double mean = Metrics.mean(costs);
            double rt = Metrics.mean(runtimes);

            csv.write(solver.variantName() + "," + best + "," + mean + "," + rt + "\n");
        }
        csv.close();
    }

}
