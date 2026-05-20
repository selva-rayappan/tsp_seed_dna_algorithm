import java.io.*;
import java.util.*;

public class TSPScalingComparisonv1 {

    public static void main(String[] args) {
        System.out.println("=================================================================================");
        System.out.println("          TSP SCALING STUDY BENCHMARK HARNESS V1 (10, 20, 30, 40, 50, 60 CITIES) ");
        System.out.println("=================================================================================");
        System.out.println("Cities variants: 10, 20, 30, 40, 50, 60. Input sets per variant: 12.");
        System.out.println("Algorithms compared: Greedy, 2-Opt, SA, GA, ACO, SeedDNA_N, SeedDNA_2N, SeedDNA_3N, SeedDNA_6N, SeedDNA_9N.");
        System.out.println();

        int[] sizes = { 10, 20, 30, 40, 50, 60 };
        int numTestCases = 12;

        List<String> outputLines = new ArrayList<>();
        outputLines.add("Size,TestCase,Algorithm,Cost,TimeMs");

        // Structure to store metrics for final aggregation
        Map<String, List<Integer>> costStats = new LinkedHashMap<>();
        Map<String, List<Long>> timeStats = new LinkedHashMap<>();

        String[] algs = { "Greedy", "2-Opt", "SA", "GA", "ACO", "SeedDNA_N", "SeedDNA_2N", "SeedDNA_3N", "SeedDNA_6N", "SeedDNA_9N" };

        for (int size : sizes) {
            for (String alg : algs) {
                costStats.put(size + "_" + alg, new ArrayList<>());
                timeStats.put(size + "_" + alg, new ArrayList<>());
            }
        }

        // Print header for live monitoring
        System.out.printf("%-6s | %-6s | %-8s | %-8s | %-8s | %-8s | %-8s | %-9s | %-9s | %-9s | %-9s | %-9s\n",
                "Size", "Set", "Greedy", "2-Opt", "SA", "GA", "ACO", "SeedDNA_N", "SeedDNA_2N", "SeedDNA_3N", "SeedDNA_6N", "SeedDNA_9N");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------");

        for (int size : sizes) {
            for (int t = 1; t <= numTestCases; t++) {
                // Generate cities deterministically per test case/size
                Random setupRand = new Random(size * 1000 + t);
                TSPSeedDNAv1.City[] cities = new TSPSeedDNAv1.City[size];
                for (int c = 0; c < size; c++) {
                    double x = setupRand.nextDouble() * 100.0;
                    double y = setupRand.nextDouble() * 100.0;
                    cities[c] = new TSPSeedDNAv1.City(c, x, y);
                }

                // Create graph
                int[][] graph = new int[size][size];
                for (int u = 0; u < size; u++) {
                    for (int v = 0; v < size; v++) {
                        if (u == v) {
                            graph[u][v] = -1;
                        } else {
                            double dx = cities[u].x - cities[v].x;
                            double dy = cities[u].y - cities[v].y;
                            graph[u][v] = (int) Math.round(Math.sqrt(dx * dx + dy * dy));
                        }
                    }
                }

                Random algorithmRand = new Random(t * 777);

                // 1. Greedy
                long sTime = System.currentTimeMillis();
                TSPSeedDNAv1.Result resGreedy = TSPSeedDNAv1.pureGreedy(graph);
                long eTime = System.currentTimeMillis();
                costStats.get(size + "_Greedy").add(resGreedy.cost);
                timeStats.get(size + "_Greedy").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,Greedy,%d,%d", size, t, resGreedy.cost, eTime - sTime));

                // 2. 2-Opt
                sTime = System.currentTimeMillis();
                TSPSeedDNAv1.Result res2Opt = TSPSeedDNAv1.pure2Opt(graph);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_2-Opt").add(res2Opt.cost);
                timeStats.get(size + "_2-Opt").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,2-Opt,%d,%d", size, t, res2Opt.cost, eTime - sTime));

                // 3. SA
                sTime = System.currentTimeMillis();
                TSPSeedDNAv1.Result resSA = TSPSeedDNAv1.simulatedAnnealing(graph, algorithmRand);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SA").add(resSA.cost);
                timeStats.get(size + "_SA").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SA,%d,%d", size, t, resSA.cost, eTime - sTime));

                // 4. GA
                sTime = System.currentTimeMillis();
                TSPSeedDNAv1.Result resGA = TSPSeedDNAv1.geneticAlgorithm(graph, algorithmRand);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_GA").add(resGA.cost);
                timeStats.get(size + "_GA").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,GA,%d,%d", size, t, resGA.cost, eTime - sTime));

                // 5. ACO
                sTime = System.currentTimeMillis();
                TSPSeedDNAv1.Result resACO = TSPSeedDNAv1.antColonyOptimization(graph, algorithmRand);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_ACO").add(resACO.cost);
                timeStats.get(size + "_ACO").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,ACO,%d,%d", size, t, resACO.cost, eTime - sTime));

                // 6. SeedDNA_N (routeCountMultiplier = 1)
                sTime = System.currentTimeMillis();
                List<String> tempCsvN = new ArrayList<>();
                Random seedRandN = new Random(t * 777);
                TSPSeedDNAv1.Result resSeedDNA_N = TSPSeedDNAv1.tspSeedDNA(graph, seedRandN, t, tempCsvN, 1);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SeedDNA_N").add(resSeedDNA_N.cost);
                timeStats.get(size + "_SeedDNA_N").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SeedDNA_N,%d,%d", size, t, resSeedDNA_N.cost, eTime - sTime));

                // 7. SeedDNA_2N (routeCountMultiplier = 2)
                sTime = System.currentTimeMillis();
                List<String> tempCsv2N = new ArrayList<>();
                Random seedRand2N = new Random(t * 777);
                TSPSeedDNAv1.Result resSeedDNA_2N = TSPSeedDNAv1.tspSeedDNA(graph, seedRand2N, t, tempCsv2N, 2);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SeedDNA_2N").add(resSeedDNA_2N.cost);
                timeStats.get(size + "_SeedDNA_2N").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SeedDNA_2N,%d,%d", size, t, resSeedDNA_2N.cost, eTime - sTime));

                // 8. SeedDNA_3N (routeCountMultiplier = 3)
                sTime = System.currentTimeMillis();
                List<String> tempCsv3N = new ArrayList<>();
                Random seedRand3N = new Random(t * 777);
                TSPSeedDNAv1.Result resSeedDNA_3N = TSPSeedDNAv1.tspSeedDNA(graph, seedRand3N, t, tempCsv3N, 3);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SeedDNA_3N").add(resSeedDNA_3N.cost);
                timeStats.get(size + "_SeedDNA_3N").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SeedDNA_3N,%d,%d", size, t, resSeedDNA_3N.cost, eTime - sTime));

                // 9. SeedDNA_6N (routeCountMultiplier = 6)
                sTime = System.currentTimeMillis();
                List<String> tempCsv6N = new ArrayList<>();
                Random seedRand6N = new Random(t * 777);
                TSPSeedDNAv1.Result resSeedDNA_6N = TSPSeedDNAv1.tspSeedDNA(graph, seedRand6N, t, tempCsv6N, 6);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SeedDNA_6N").add(resSeedDNA_6N.cost);
                timeStats.get(size + "_SeedDNA_6N").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SeedDNA_6N,%d,%d", size, t, resSeedDNA_6N.cost, eTime - sTime));

                // 10. SeedDNA_9N (routeCountMultiplier = 9)
                sTime = System.currentTimeMillis();
                List<String> tempCsv9N = new ArrayList<>();
                Random seedRand9N = new Random(t * 777);
                TSPSeedDNAv1.Result resSeedDNA_9N = TSPSeedDNAv1.tspSeedDNA(graph, seedRand9N, t, tempCsv9N, 9);
                eTime = System.currentTimeMillis();
                costStats.get(size + "_SeedDNA_9N").add(resSeedDNA_9N.cost);
                timeStats.get(size + "_SeedDNA_9N").add(eTime - sTime);
                outputLines.add(String.format("%d,%d,SeedDNA_9N,%d,%d", size, t, resSeedDNA_9N.cost, eTime - sTime));

                // Validation
                if (!TSPSeedDNAv1.validateRoute(resGreedy.route, size))
                    System.err.printf("Greedy route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(res2Opt.route, size))
                    System.err.printf("2-Opt route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSA.route, size))
                    System.err.printf("SA route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resGA.route, size))
                    System.err.printf("GA route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resACO.route, size))
                    System.err.printf("ACO route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSeedDNA_N.route, size))
                    System.err.printf("SeedDNA_N route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSeedDNA_2N.route, size))
                    System.err.printf("SeedDNA_2N route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSeedDNA_3N.route, size))
                    System.err.printf("SeedDNA_3N route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSeedDNA_6N.route, size))
                    System.err.printf("SeedDNA_6N route invalid! Size=%d Set=%d\n", size, t);
                if (!TSPSeedDNAv1.validateRoute(resSeedDNA_9N.route, size))
                    System.err.printf("SeedDNA_9N route invalid! Size=%d Set=%d\n", size, t);

                // Print row progress
                System.out.printf("%-6d | %-6d | %-8d | %-8d | %-8d | %-8d | %-8d | %-9d | %-9d | %-9d | %-9d | %-9d\n",
                        size, t, resGreedy.cost, res2Opt.cost, resSA.cost, resGA.cost, resACO.cost, resSeedDNA_N.cost, resSeedDNA_2N.cost, resSeedDNA_3N.cost, resSeedDNA_6N.cost, resSeedDNA_9N.cost);
            }
            System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------");
        }

        // Export data
        writeCsv("tsp_scaling_outputs_v1.csv", outputLines);
        System.out.println("Raw results saved to: tsp_scaling_outputs_v1.csv");
        System.out.println();

        // Print aggregated study comparison report
        System.out.println("=================================================================================");
        System.out.println("                         AGGREGATED PERFORMANCE STUDY REPORT                    ");
        System.out.println("=================================================================================");
        System.out.printf("%-6s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s | %-12s\n",
                "Size", "Greedy", "2-Opt", "SA", "GA", "ACO", "SeedDNA_N", "SeedDNA_2N", "SeedDNA_3N", "SeedDNA_6N", "SeedDNA_9N");
        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("Average Tour Costs (Lower is better):");

        for (int size : sizes) {
            System.out.printf("%-6d | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f\n",
                    size,
                    getAverageCost(costStats.get(size + "_Greedy")),
                    getAverageCost(costStats.get(size + "_2-Opt")),
                    getAverageCost(costStats.get(size + "_SA")),
                    getAverageCost(costStats.get(size + "_GA")),
                    getAverageCost(costStats.get(size + "_ACO")),
                    getAverageCost(costStats.get(size + "_SeedDNA_N")),
                    getAverageCost(costStats.get(size + "_SeedDNA_2N")),
                    getAverageCost(costStats.get(size + "_SeedDNA_3N")),
                    getAverageCost(costStats.get(size + "_SeedDNA_6N")),
                    getAverageCost(costStats.get(size + "_SeedDNA_9N")));
        }

        System.out.println("-------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.println("Average Execution Times (ms):");
        for (int size : sizes) {
            System.out.printf("%-6d | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f | %-12.1f\n",
                    size,
                    getAverageTime(timeStats.get(size + "_Greedy")),
                    getAverageTime(timeStats.get(size + "_2-Opt")),
                    getAverageTime(timeStats.get(size + "_SA")),
                    getAverageTime(timeStats.get(size + "_GA")),
                    getAverageTime(timeStats.get(size + "_ACO")),
                    getAverageTime(timeStats.get(size + "_SeedDNA_N")),
                    getAverageTime(timeStats.get(size + "_SeedDNA_2N")),
                    getAverageTime(timeStats.get(size + "_SeedDNA_3N")),
                    getAverageTime(timeStats.get(size + "_SeedDNA_6N")),
                    getAverageTime(timeStats.get(size + "_SeedDNA_9N")));
        }
        System.out.println("=================================================================================");
    }

    private static double getAverageCost(List<Integer> list) {
        if (list == null || list.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (int val : list)
            sum += val;
        return sum / list.size();
    }

    private static double getAverageTime(List<Long> list) {
        if (list == null || list.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (long val : list)
            sum += val;
        return sum / list.size();
    }

    private static void writeCsv(String fileName, List<String> lines) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            for (String line : lines) {
                pw.println(line);
            }
        } catch (IOException e) {
            System.err.println("Error writing to file " + fileName + ": " + e.getMessage());
        }
    }
}
