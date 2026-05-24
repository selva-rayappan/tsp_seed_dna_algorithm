package benchmark.solvers;

import benchmark.Metrics;
import benchmark.TSPSolver;

import java.util.*;
import java.util.stream.*;
import java.util.concurrent.ForkJoinPool;

public class SeedMiningSolver implements TSPSolver {

    private int threads = Runtime.getRuntime().availableProcessors();
    private boolean seedMining = true;
    private boolean penalization = true;
    private boolean overlapSeeds = true;
    private int routeCountMultiplier = 1; // Default scaling multiplier

    // Constructors
    public SeedMiningSolver() {
    }

    public SeedMiningSolver(int threads) {
        this.threads = threads;
    }

    public SeedMiningSolver(boolean seedMining, boolean penalization, boolean overlapSeeds) {
        this.seedMining = seedMining;
        this.penalization = penalization;
        this.overlapSeeds = overlapSeeds;
    }

    @Override
    public String getName() {
        return "SeedMining";
    }

    public String variantName() {
        if (seedMining && penalization && overlapSeeds)
            return "Full";
        if (!seedMining && penalization && overlapSeeds)
            return "NoSeedMining";
        if (seedMining && !penalization && overlapSeeds)
            return "NoPenalization";
        if (seedMining && penalization && !overlapSeeds)
            return "NoOverlap";
        return "Custom";
    }

    @Override
    public int[] solve(double[][] cities) {
        int n = cities.length;
        int[][] graph = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                graph[i][j] = (int) Math.round(Metrics.distance(cities[i], cities[j]));
            }
        }
        Result res = tspSeedDNA(graph, new Random(42), 0, new ArrayList<>(), routeCountMultiplier);
        int[] routeArray = new int[n];
        for (int i = 0; i < n; i++) {
            routeArray[i] = res.route.get(i);
        }
        return routeArray;
    }

    static class City {
        int id;
        double x;
        double y;

        City(int id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    static class Result {
        int cost;
        List<Integer> route;

        Result(int cost, List<Integer> route) {
            this.cost = cost;
            this.route = route;
        }
    }

    static abstract class Seed {
        String name; // S1, S2, etc.
        String key; // e.g. "0-4-12" or "3-15"
        int[] nodes;
        double weight;
        int costIncreaseCount;
        int bestCost;

        Seed(String key, int[] nodes) {
            this.key = key;
            this.nodes = nodes;
            this.weight = 1.0;
            this.costIncreaseCount = 0;
            this.bestCost = Integer.MAX_VALUE;
        }

        abstract long getPackedKey();
    }

    static class TripletSeed extends Seed {
        TripletSeed(int u, int v, int w) {
            super(getTripletKey(u, v, w), canonicalTriplet(u, v, w));
        }

        static int[] canonicalTriplet(int u, int v, int w) {
            if (u > w) {
                return new int[] { w, v, u };
            } else {
                return new int[] { u, v, w };
            }
        }

        static String getTripletKey(int u, int v, int w) {
            if (u > w) {
                return w + "-" + v + "-" + u;
            } else {
                return u + "-" + v + "-" + w;
            }
        }

        @Override
        long getPackedKey() {
            return getPackedSubpathKey(nodes);
        }
    }

    static class PairSeed extends Seed {
        PairSeed(int u, int v) {
            super(getPairKey(u, v), canonicalPair(u, v));
        }

        static int[] canonicalPair(int u, int v) {
            if (u > v) {
                return new int[] { v, u };
            } else {
                return new int[] { u, v };
            }
        }

        static String getPairKey(int u, int v) {
            if (u > v) {
                return v + "-" + u;
            } else {
                return u + "-" + v;
            }
        }

        @Override
        long getPackedKey() {
            return getPackedSubpathKey(nodes);
        }
    }

    static class GeneralSeed extends Seed {
        GeneralSeed(int[] nodes) {
            super(getGeneralKey(nodes), nodes);
        }

        static String getGeneralKey(int[] nodes) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nodes.length; i++) {
                sb.append(nodes[i]).append(i == nodes.length - 1 ? "" : "-");
            }
            return sb.toString();
        }

        @Override
        long getPackedKey() {
            return getPackedSubpathKey(nodes);
        }
    }

    static int[] canonicalSubpath(int[] nodes) {
        int L = nodes.length;
        if (nodes[0] > nodes[L - 1]) {
            int[] rev = new int[L];
            for (int j = 0; j < L; j++) {
                rev[j] = nodes[L - 1 - j];
            }
            return rev;
        }
        return nodes;
    }

    static long getPackedSubpathKey(int[] nodes) {
        int L = nodes.length;
        long key = ((long) L) << 60;
        for (int j = 0; j < L; j++) {
            key |= ((long) nodes[j]) << (10 * j);
        }
        return key;
    }

    // Helper: Reverse a section of the route in place (for 2-opt)
    static void reverse(int[] route, int i, int j) {
        while (i < j) {
            int temp = route[i];
            route[i] = route[j];
            route[j] = temp;
            i++;
            j--;
        }
    }

    // Helper: Calculate cost of route array
    static int calculateCost(int[][] graph, int[] route) {
        int cost = 0;
        int n = route.length;
        for (int i = 0; i < n - 1; i++) {
            cost += graph[route[i]][route[i + 1]];
        }
        cost += graph[route[n - 1]][route[0]];
        return cost;
    }

    // Helper: Calculate cost of route list
    static int calculateCost(int[][] graph, List<Integer> route) {
        int cost = 0;
        int n = route.size();
        for (int i = 0; i < n - 1; i++) {
            cost += graph[route.get(i)][route.get(i + 1)];
        }
        cost += graph[route.get(n - 1)][route.get(0)];
        return cost;
    }

    // Helper: Generate a randomized Nearest-Neighbor route
    static int[] generateRandomNNRoute(int[][] graph, int n, Random rand) {
        int[] route = new int[n];
        boolean[] visited = new boolean[n];
        int startCity = rand.nextInt(n);
        route[0] = startCity;
        visited[startCity] = true;
        int currentCity = startCity;

        for (int count = 1; count < n; count++) {
            int nextCity = -1;
            int minCost = Integer.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (!visited[j] && graph[currentCity][j] != -1 && graph[currentCity][j] < minCost) {
                    minCost = graph[currentCity][j];
                    nextCity = j;
                }
            }
            if (nextCity == -1) {
                for (int j = 0; j < n; j++) {
                    if (!visited[j]) {
                        nextCity = j;
                        break;
                    }
                }
            }
            visited[nextCity] = true;
            route[count] = nextCity;
            currentCity = nextCity;
        }
        return route;
    }

    // Helper: Compute K-Nearest Neighbors for all cities
    static int[][] computeNeighborList(int[][] graph, int k) {
        int n = graph.length;
        int[][] neighborList = new int[n][k];
        for (int i = 0; i < n; i++) {
            final int city = i;
            Integer[] neighbors = new Integer[n - 1];
            int idx = 0;
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    neighbors[idx++] = j;
                }
            }
            Arrays.sort(neighbors, (a, b) -> Integer.compare(graph[city][a], graph[city][b]));
            for (int j = 0; j < k; j++) {
                neighborList[i][j] = neighbors[j];
            }
        }
        return neighborList;
    }

    // Helper: Apply 2-opt local search to a route using KNN candidate list and
    // Don't Look Bits (DLB)
    static void apply2Opt(int[][] graph, int[] route, int[][] neighborList, int maxSwaps) {
        int n = graph.length;
        int[] pos = new int[n];
        for (int idx = 0; idx < n; idx++) {
            pos[route[idx]] = idx;
        }

        boolean[] dontLook = new boolean[n];
        boolean improved = true;
        int swapCount = 0;
        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                int prevI = i - 1;
                int u = route[prevI];
                if (dontLook[u]) {
                    continue;
                }
                int v = route[i];
                boolean nodeImproved = false;
                for (int x : neighborList[u]) {
                    int j = pos[x];
                    if (j <= i || j >= n) {
                        continue;
                    }
                    int nextJ = (j == n - 1) ? 0 : j + 1;
                    if (nextJ == prevI) {
                        continue;
                    }
                    int currentEdge1 = graph[u][v];
                    int currentEdge2 = graph[route[j]][route[nextJ]];
                    int newEdge1 = graph[u][route[j]];
                    int newEdge2 = graph[v][route[nextJ]];
                    if (newEdge1 + newEdge2 < currentEdge1 + currentEdge2) {
                        reverse(route, i, j);
                        for (int k = i; k <= j; k++) {
                            pos[route[k]] = k;
                        }
                        dontLook[u] = false;
                        dontLook[v] = false;
                        dontLook[route[j]] = false;
                        dontLook[route[nextJ]] = false;
                        improved = true;
                        nodeImproved = true;
                        swapCount++;
                        if (maxSwaps > 0 && swapCount >= maxSwaps) {
                            return;
                        }
                        break;
                    }
                }
                if (nodeImproved) {
                    break;
                } else {
                    dontLook[u] = true;
                }
            }
        }
    }

    static void apply2Opt(int[][] graph, int[] route, int[][] neighborList) {
        apply2Opt(graph, route, neighborList, -1);
    }

    static void apply2Opt(int[][] graph, int[] route) {
        int n = graph.length;
        int k = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, k);
        apply2Opt(graph, route, neighborList, -1);
    }

    static void apply3Opt(int[][] graph, int[] route) {
        int n = graph.length;
        int kNeighbor = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, kNeighbor);

        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            pos[route[i]] = i;
        }

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n; i++) {
                int A = route[i];
                for (int nextNeighbor : neighborList[A]) {
                    int idx = pos[nextNeighbor];
                    if (idx == i || idx == (i + 1) % n || idx == (i - 1 + n) % n) {
                        continue;
                    }
                    for (int p3 = 0; p3 < n; p3++) {
                        if (p3 == i || p3 == idx) continue;

                        int iPrime = i;
                        int jPrime = idx;
                        int kPrime = p3;

                        if (iPrime > jPrime) { int t = iPrime; iPrime = jPrime; jPrime = t; }
                        if (jPrime > kPrime) { int t = jPrime; jPrime = kPrime; kPrime = t; }
                        if (iPrime > jPrime) { int t = iPrime; iPrime = jPrime; jPrime = t; }

                        int nodeA = route[iPrime];
                        int nodeB = route[(iPrime + 1) % n];
                        int nodeC = route[jPrime];
                        int nodeD = route[(jPrime + 1) % n];
                        int nodeE = route[kPrime];
                        int nodeF = route[(kPrime + 1) % n];

                        int d0 = graph[nodeA][nodeB] + graph[nodeC][nodeD] + graph[nodeE][nodeF];

                        // Case 1: A -> D ... E -> B ... C -> F
                        int d1 = graph[nodeA][nodeD] + graph[nodeE][nodeB] + graph[nodeC][nodeF];
                        if (d1 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 1);
                            for (int p = 0; p < n; p++) pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        // Case 2: A -> D ... E -> C ... B -> F
                        int d2 = graph[nodeA][nodeD] + graph[nodeE][nodeC] + graph[nodeB][nodeF];
                        if (d2 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 2);
                            for (int p = 0; p < n; p++) pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        // Case 3: A -> E ... D -> B ... C -> F
                        int d3 = graph[nodeA][nodeE] + graph[nodeD][nodeB] + graph[nodeC][nodeF];
                        if (d3 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 3);
                            for (int p = 0; p < n; p++) pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        // Case 4: A -> E ... D -> C ... B -> F
                        int d4 = graph[nodeA][nodeE] + graph[nodeD][nodeC] + graph[nodeB][nodeF];
                        if (d4 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 4);
                            for (int p = 0; p < n; p++) pos[route[p]] = p;
                            improved = true;
                            break;
                        }
                    }
                    if (improved) break;
                }
                if (improved) break;
            }
        }
    }

    static void reconnect3Opt(int[] route, int i, int j, int k, int caseNo) {
        int n = route.length;
        int[] nextRoute = new int[n];
        int idx = 0;

        for (int p = 0; p <= i; p++) {
            nextRoute[idx++] = route[p];
        }

        if (caseNo == 1) {
            for (int p = j + 1; p <= k; p++) {
                nextRoute[idx++] = route[p];
            }
            for (int p = i + 1; p <= j; p++) {
                nextRoute[idx++] = route[p];
            }
        } else if (caseNo == 2) {
            for (int p = j + 1; p <= k; p++) {
                nextRoute[idx++] = route[p];
            }
            for (int p = j; p >= i + 1; p--) {
                nextRoute[idx++] = route[p];
            }
        } else if (caseNo == 3) {
            for (int p = k; p >= j + 1; p--) {
                nextRoute[idx++] = route[p];
            }
            for (int p = i + 1; p <= j; p++) {
                nextRoute[idx++] = route[p];
            }
        } else if (caseNo == 4) {
            for (int p = k; p >= j + 1; p--) {
                nextRoute[idx++] = route[p];
            }
            for (int p = j; p >= i + 1; p--) {
                nextRoute[idx++] = route[p];
            }
        }

        for (int p = k + 1; p < n; p++) {
            nextRoute[idx++] = route[p];
        }

        System.arraycopy(nextRoute, 0, route, 0, n);
    }

    // Helper: Get adjacent edges of a seed as PairSeeds
    static List<PairSeed> getEdges(Seed s) {
        List<PairSeed> list = new ArrayList<>();
        if (s instanceof TripletSeed) {
            list.add(new PairSeed(s.nodes[0], s.nodes[1]));
            list.add(new PairSeed(s.nodes[1], s.nodes[2]));
        } else if (s instanceof PairSeed) {
            list.add(new PairSeed(s.nodes[0], s.nodes[1]));
        }
        return list;
    }

    // ==========================================
    // TSP SEED DNA ALGORITHM IMPLEMENTATION
    // ==========================================
    Result tspSeedDNA(int[][] graph, Random rand, int testCaseId, List<String> inputCsvLines,
            int routeCountMultiplier) {
        int n = graph.length;
        int routeCount = n * routeCountMultiplier;
        Map<Long, Seed> globalSeeds = new LinkedHashMap<>();
        int seedCounter = 1;

        int maxIterations = graph.length;
        int iter = 0;
        boolean newGroupsFormed = true;

        int kNeighbor = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, kNeighbor);
        int maxSwaps = Math.max(50, n / 5);

        ForkJoinPool customThreadPool = (threads == Runtime.getRuntime().availableProcessors()) ? null
                : new ForkJoinPool(threads);
        try {
            while (newGroupsFormed && iter < maxIterations) {
                newGroupsFormed = false;
                iter++;

                // 1. Generate random routeCount NN routes using parallel 2-opt and find the
                // cost
                long[] threadSeeds = new long[routeCount];
                for (int i = 0; i < routeCount; i++) {
                    threadSeeds[i] = rand.nextLong();
                }

                List<int[]> routes;
                if (customThreadPool == null) {
                    routes = IntStream.range(0, routeCount)
                            .parallel()
                            .mapToObj(r -> {
                                Random threadRand = new Random(threadSeeds[r]);
                                int[] route = generateRandomNNRoute(graph, n, threadRand);
                                apply2Opt(graph, route, neighborList, maxSwaps);
                                return route;
                            })
                            .collect(Collectors.toList());
                } else {
                    try {
                        routes = customThreadPool.submit(() -> IntStream.range(0, routeCount)
                                .parallel()
                                .mapToObj(r -> {
                                    Random threadRand = new Random(threadSeeds[r]);
                                    int[] route = generateRandomNNRoute(graph, n, threadRand);
                                    apply2Opt(graph, route, neighborList, maxSwaps);
                                    return route;
                                })
                                .collect(Collectors.toList())).get();
                    } catch (Exception e) {
                        routes = IntStream.range(0, routeCount)
                                .parallel()
                                .mapToObj(r -> {
                                    Random threadRand = new Random(threadSeeds[r]);
                                    int[] route = generateRandomNNRoute(graph, n, threadRand);
                                    apply2Opt(graph, route, neighborList, maxSwaps);
                                    return route;
                                })
                                .collect(Collectors.toList());
                    }
                }

                List<Integer> costs = new ArrayList<>();
                for (int[] route : routes) {
                    costs.add(calculateCost(graph, route));
                }

                // 2. Order by minimum cost
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < routes.size(); i++)
                    indices.add(i);
                indices.sort(Comparator.comparingInt(costs::get));

                List<int[]> sortedRoutes = new ArrayList<>();
                List<Integer> sortedCosts = new ArrayList<>();
                for (int idx : indices) {
                    sortedRoutes.add(routes.get(idx));
                    sortedCosts.add(costs.get(idx));
                }

                if (!seedMining) {
                    List<Integer> bestRoute = new ArrayList<>();
                    for (int node : sortedRoutes.get(0)) {
                        bestRoute.add(node);
                    }
                    return new Result(sortedCosts.get(0), bestRoute);
                }

                // Record iteration 1 routes in input CSV lines
                if (iter == 1) {
                    for (int r = 0; r < sortedRoutes.size(); r++) {
                        int[] route = sortedRoutes.get(r);
                        int cost = sortedCosts.get(r);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            sb.append(route[i]).append(i == n - 1 ? "" : "-");
                        }
                        inputCsvLines
                                .add(String.format("SEED_ROUTE,%d,%d,%d,%s", testCaseId, r + 1, cost, sb.toString()));
                    }
                }

                // Build pre-computed hash lookup for containment checks
                Map<Long, List<Integer>> seedToRouteIndices = new HashMap<>();
                for (int r = 0; r < sortedRoutes.size(); r++) {
                    int[] route = sortedRoutes.get(r);
                    for (int L = 2; L <= 3; L++) {
                        for (int i = 0; i < n; i++) {
                            int[] sub = new int[L];
                            for (int j = 0; j < L; j++) {
                                sub[j] = route[(i + j) % n];
                            }
                            int[] canSub = canonicalSubpath(sub);
                            long key = getPackedSubpathKey(canSub);
                            List<Integer> list = seedToRouteIndices.computeIfAbsent(key, k -> new ArrayList<>());
                            if (list.isEmpty() || list.get(list.size() - 1) != r) {
                                list.add(r);
                            }
                        }
                    }
                }

                // Identify repeating subpaths of sizes 2 and 3 (frequency >= 2)
                Map<Long, Integer> subpathFreq = new HashMap<>();
                for (int[] route : sortedRoutes) {
                    for (int L = 2; L <= 3; L++) {
                        for (int i = 0; i < n; i++) {
                            int[] sub = new int[L];
                            for (int j = 0; j < L; j++) {
                                sub[j] = route[(i + j) % n];
                            }
                            int[] canSub = canonicalSubpath(sub);
                            long key = getPackedSubpathKey(canSub);
                            subpathFreq.put(key, subpathFreq.getOrDefault(key, 0) + 1);
                        }
                    }
                }

                List<Seed> currentIterationSeeds = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : subpathFreq.entrySet()) {
                    if (entry.getValue() >= 2) {
                        long packed = entry.getKey();
                        int L = (int) (packed >> 60) & 0xF;
                        int[] nodes = new int[L];
                        for (int j = 0; j < L; j++) {
                            nodes[j] = (int) (packed >> (10 * j)) & 0x3FF;
                        }
                        currentIterationSeeds.add(new GeneralSeed(nodes));
                    }
                }

                // Add newly discovered seeds to global seeds
                for (Seed s : currentIterationSeeds) {
                    long key = s.getPackedKey();
                    if (!globalSeeds.containsKey(key)) {
                        s.name = "S" + seedCounter++;
                        List<Integer> rIds = seedToRouteIndices.get(key);
                        if (rIds != null && !rIds.isEmpty()) {
                            s.bestCost = sortedCosts.get(rIds.get(0));
                        }
                        globalSeeds.put(key, s);
                        newGroupsFormed = true;
                    }
                }

                // 8. Probabilistic Z-score based weighting (Unified Reward and Penalization)
                double costSum = 0;
                for (int cost : sortedCosts) {
                    costSum += cost;
                }
                double mean = costSum / sortedCosts.size();
                double sumSq = 0;
                for (int cost : sortedCosts) {
                    sumSq += (cost - mean) * (cost - mean);
                }
                double stdDev = Math.sqrt(sumSq / sortedCosts.size());
                if (stdDev < 1e-6) {
                    stdDev = 1.0;
                }

                double beta = 1.0;
                for (Seed s : globalSeeds.values()) {
                    List<Integer> rIds = seedToRouteIndices.get(s.getPackedKey());
                    if (rIds != null && !rIds.isEmpty()) {
                        int bestR = rIds.get(0);
                        double z = (sortedCosts.get(bestR) - mean) / stdDev;
                        double term = Math.exp(-beta * z) - 1.0;
                        double update = penalization ? term : Math.max(0.0, term);
                        s.weight += 0.5 * update;
                    }
                }
            }
        } finally {
            if (customThreadPool != null) {
                customThreadPool.shutdown();
            }
        }

        // Finalize route by stitching sorted seeds and apply a final 2-opt optimization
        Result res = finalizeRoute(graph, globalSeeds);
        int[] routeArray = new int[n];
        for (int i = 0; i < n; i++) {
            routeArray[i] = res.route.get(i);
        }
        apply2Opt(graph, routeArray, neighborList);
        apply3Opt(graph, routeArray);
        List<Integer> finalRouteList = new ArrayList<>();
        for (int node : routeArray) {
            finalRouteList.add(node);
        }
        return new Result(calculateCost(graph, finalRouteList), finalRouteList);
    }

    // Helper: Finalize route based on seed weights
    // Helper: Finalize route based on seed weights with dynamic decomposition of
    // seeds up to size 6
    static Result finalizeRoute(int[][] graph, Map<Long, Seed> globalSeeds) {
        int n = graph.length;
        List<Seed> seedPool = new ArrayList<>();
        for (Seed s : globalSeeds.values()) {
            if (s.weight > 0) {
                seedPool.add(s);
            }
        }

        // Sort: primary key = weight (descending), secondary key = bestCost (ascending)
        seedPool.sort((a, b) -> {
            int cmp = Double.compare(b.weight, a.weight);
            if (cmp != 0)
                return cmp;
            return Integer.compare(a.bestCost, b.bestCost);
        });

        if (seedPool.isEmpty()) {
            return pureGreedy(graph);
        }

        List<Integer> path = new ArrayList<>();
        boolean[] visited = new boolean[n];

        // Seed with highest weight
        Seed bestSeed = seedPool.get(0);
        for (int node : bestSeed.nodes) {
            path.add(node);
            visited[node] = true;
        }
        bestSeed.weight = -1.0; // deactivate

        // Clean out deactivated seeds from seedPool initially
        List<Seed> initialPool = new ArrayList<>();
        for (Seed s : seedPool) {
            if (s.weight > 0) {
                initialPool.add(s);
            }
        }
        seedPool = initialPool;

        boolean extended = true;
        while (path.size() < n && extended) {
            extended = false;
            int back = path.get(path.size() - 1);
            int front = path.get(0);

            Seed chosenSeed = null;
            int chosenType = 0; // 1 = back forward, 2 = back backward, 3 = front forward, 4 = front backward
            int chosenK = -1;
            List<Seed> newDecomposedSeeds = new ArrayList<>();
            boolean poolModified = false;

            for (int idx = 0; idx < seedPool.size(); idx++) {
                Seed s = seedPool.get(idx);
                if (s.weight <= 0)
                    continue;

                int[] nodes = s.nodes;
                int L = nodes.length;

                int visitedCount = 0;
                for (int node : nodes) {
                    if (visited[node])
                        visitedCount++;
                }

                if (visitedCount == L) {
                    s.weight = -1.0;
                    poolModified = true;
                    continue;
                }

                if (visitedCount > 1) {
                    // Break down!
                    List<int[]> segments = getUnvisitedSegments(nodes, visited);
                    for (int[] seg : segments) {
                        if (seg.length >= 2) {
                            GeneralSeed segSeed = new GeneralSeed(seg);
                            segSeed.weight = s.weight * ((double) seg.length / L);
                            segSeed.bestCost = s.bestCost;
                            newDecomposedSeeds.add(segSeed);
                        }
                    }
                    s.weight = -1.0;
                    poolModified = true;
                    continue;
                }

                // At this point, visitedCount must be 1.
                // Find which node in the seed is visited.
                int k = -1;
                for (int j = 0; j < L; j++) {
                    if (visited[nodes[j]]) {
                        k = j;
                        break;
                    }
                }

                if (k == -1)
                    continue;

                if (nodes[k] == back) {
                    // Check back forward extension
                    boolean forwardValid = true;
                    for (int j = 1; j <= k; j++) {
                        if (path.size() - 1 - j < 0 || nodes[k - j] != path.get(path.size() - 1 - j)) {
                            forwardValid = false;
                            break;
                        }
                    }
                    if (forwardValid) {
                        for (int j = k + 1; j < L; j++) {
                            if (visited[nodes[j]]) {
                                forwardValid = false;
                                break;
                            }
                        }
                    }
                    if (forwardValid) {
                        chosenSeed = s;
                        chosenType = 1;
                        chosenK = k;
                        break;
                    }

                    // Check back backward extension
                    boolean backwardValid = true;
                    for (int j = 1; j < L - k; j++) {
                        if (path.size() - 1 - j < 0 || nodes[k + j] != path.get(path.size() - 1 - j)) {
                            backwardValid = false;
                            break;
                        }
                    }
                    if (backwardValid) {
                        for (int j = k - 1; j >= 0; j--) {
                            if (visited[nodes[j]]) {
                                backwardValid = false;
                                break;
                            }
                        }
                    }
                    if (backwardValid) {
                        chosenSeed = s;
                        chosenType = 2;
                        chosenK = k;
                        break;
                    }
                }

                if (nodes[k] == front) {
                    // Check front forward extension (prepending to front)
                    boolean forwardValid = true;
                    for (int j = 1; j < L - k; j++) {
                        if (j >= path.size() || nodes[k + j] != path.get(j)) {
                            forwardValid = false;
                            break;
                        }
                    }
                    if (forwardValid) {
                        for (int j = k - 1; j >= 0; j--) {
                            if (visited[nodes[j]]) {
                                forwardValid = false;
                                break;
                            }
                        }
                    }
                    if (forwardValid) {
                        chosenSeed = s;
                        chosenType = 3;
                        chosenK = k;
                        break;
                    }

                    // Check front backward extension (prepending to front)
                    boolean backwardValid = true;
                    for (int j = 1; j <= k; j++) {
                        if (j >= path.size() || nodes[k - j] != path.get(j)) {
                            backwardValid = false;
                            break;
                        }
                    }
                    if (backwardValid) {
                        for (int j = k + 1; j < L; j++) {
                            if (visited[nodes[j]]) {
                                backwardValid = false;
                                break;
                            }
                        }
                    }
                    if (backwardValid) {
                        chosenSeed = s;
                        chosenType = 4;
                        chosenK = k;
                        break;
                    }
                }

                // If visitedCount == 1 but we couldn't extend (either because visited node was
                // in the middle
                // and didn't align, or prefix/suffix didn't match), break it down!
                if (chosenSeed == null) {
                    List<int[]> segments = getUnvisitedSegments(nodes, visited);
                    for (int[] seg : segments) {
                        if (seg.length >= 2) {
                            GeneralSeed segSeed = new GeneralSeed(seg);
                            segSeed.weight = s.weight * ((double) seg.length / L);
                            segSeed.bestCost = s.bestCost;
                            newDecomposedSeeds.add(segSeed);
                        }
                    }
                    s.weight = -1.0;
                    poolModified = true;
                }
            }

            if (chosenSeed != null) {
                int[] nodes = chosenSeed.nodes;
                int L = nodes.length;
                int k = chosenK;
                if (chosenType == 1) { // back forward
                    for (int j = k + 1; j < L; j++) {
                        path.add(nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 2) { // back backward
                    for (int j = k - 1; j >= 0; j--) {
                        path.add(nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 3) { // front forward
                    for (int j = k - 1; j >= 0; j--) {
                        path.add(0, nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 4) { // front backward
                    for (int j = k + 1; j < L; j++) {
                        path.add(0, nodes[j]);
                        visited[nodes[j]] = true;
                    }
                }
                chosenSeed.weight = -1.0;
                extended = true;
            }

            // Remove deactivated seeds and add decomposed seeds
            if (poolModified || extended || !newDecomposedSeeds.isEmpty()) {
                List<Seed> nextPool = new ArrayList<>();
                for (Seed s : seedPool) {
                    if (s.weight > 0) {
                        nextPool.add(s);
                    }
                }
                nextPool.addAll(newDecomposedSeeds);
                nextPool.sort((a, b) -> {
                    int cmp = Double.compare(b.weight, a.weight);
                    if (cmp != 0)
                        return cmp;
                    return Integer.compare(a.bestCost, b.bestCost);
                });
                seedPool = nextPool;
            }

            // Fallback greedy
            if (!extended && path.size() < n) {
                int backNode = path.get(path.size() - 1);
                int nextCity = -1;
                int minCost = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!visited[i] && graph[backNode][i] != -1 && graph[backNode][i] < minCost) {
                        minCost = graph[backNode][i];
                        nextCity = i;
                    }
                }
                if (nextCity != -1) {
                    path.add(nextCity);
                    visited[nextCity] = true;
                    extended = true;
                }
            }
        }

        // Catch remaining unvisited cities, if any
        if (path.size() < n) {
            for (int i = 0; i < n; i++) {
                if (!visited[i]) {
                    path.add(i);
                    visited[i] = true;
                }
            }
        }

        return new Result(calculateCost(graph, path), path);
    }

    static List<int[]> getUnvisitedSegments(int[] nodes, boolean[] visited) {
        List<int[]> segments = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int node : nodes) {
            if (!visited[node]) {
                current.add(node);
            } else {
                if (!current.isEmpty()) {
                    int[] seg = new int[current.size()];
                    for (int i = 0; i < current.size(); i++) {
                        seg[i] = current.get(i);
                    }
                    segments.add(seg);
                    current.clear();
                }
            }
        }
        if (!current.isEmpty()) {
            int[] seg = new int[current.size()];
            for (int i = 0; i < current.size(); i++) {
                seg[i] = current.get(i);
            }
            segments.add(seg);
        }
        return segments;
    }

    // ==========================================
    // COMPARISON ALGORITHMS
    // ==========================================

    // 1. GREEDY SOLVER
    static Result pureGreedy(int[][] graph) {
        int n = graph.length;
        boolean[] visited = new boolean[n];
        List<Integer> path = new ArrayList<>();
        int current = 0;
        path.add(current);
        visited[current] = true;

        for (int count = 1; count < n; count++) {
            int nextCity = -1;
            int minCost = Integer.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (!visited[j] && graph[current][j] != -1 && graph[current][j] < minCost) {
                    minCost = graph[current][j];
                    nextCity = j;
                }
            }
            visited[nextCity] = true;
            current = nextCity;
            path.add(current);
        }
        return new Result(calculateCost(graph, path), path);
    }

    // 2. STANDALONE 2-OPT (Greedy + 2-Opt)
    static Result pure2Opt(int[][] graph) {
        int n = graph.length;
        Result greedyResult = pureGreedy(graph);
        int[] route = new int[n];
        for (int i = 0; i < n; i++) {
            route[i] = greedyResult.route.get(i);
        }
        apply2Opt(graph, route);
        List<Integer> list = new ArrayList<>();
        for (int node : route) {
            list.add(node);
        }
        return new Result(calculateCost(graph, list), list);
    }

    // 3. SIMULATED ANNEALING (SA)
    static Result simulatedAnnealing(int[][] graph, Random rand) {
        int n = graph.length;
        int[] currentRoute = new int[n];
        for (int i = 0; i < n; i++)
            currentRoute[i] = i;

        // Shuffle for random start
        for (int i = n - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = currentRoute[i];
            currentRoute[i] = currentRoute[j];
            currentRoute[j] = temp;
        }

        int currentCost = calculateCost(graph, currentRoute);
        int[] bestRoute = currentRoute.clone();
        int bestCost = currentCost;

        double tempVal = 100.0;
        double coolingRate = 0.995;
        double minTemp = 0.001;

        while (tempVal > minTemp) {
            for (int step = 0; step < 100; step++) {
                int i = rand.nextInt(n);
                int j = rand.nextInt(n);
                if (i == j)
                    continue;
                if (i > j) {
                    int t = i;
                    i = j;
                    j = t;
                }
                int[] nextRoute = currentRoute.clone();
                reverse(nextRoute, i, j);
                int nextCost = calculateCost(graph, nextRoute);

                int delta = nextCost - currentCost;
                if (delta < 0) {
                    currentRoute = nextRoute;
                    currentCost = nextCost;
                    if (currentCost < bestCost) {
                        bestCost = currentCost;
                        bestRoute = currentRoute.clone();
                    }
                } else {
                    if (rand.nextDouble() < Math.exp(-delta / tempVal)) {
                        currentRoute = nextRoute;
                        currentCost = nextCost;
                    }
                }
            }
            tempVal *= coolingRate;
        }

        List<Integer> list = new ArrayList<>();
        for (int node : bestRoute) {
            list.add(node);
        }
        return new Result(bestCost, list);
    }

    // 4. GENETIC ALGORITHM (GA)
    static Result geneticAlgorithm(int[][] graph, Random rand) {
        int n = graph.length;
        int popSize = 100;
        int generations = 200;
        double mutationRate = 0.05;

        List<int[]> population = new ArrayList<>();
        for (int p = 0; p < popSize; p++) {
            int[] route = new int[n];
            for (int i = 0; i < n; i++)
                route[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = rand.nextInt(i + 1);
                int temp = route[i];
                route[i] = route[j];
                route[j] = temp;
            }
            population.add(route);
        }

        int[] bestGlobalRoute = population.get(0);
        int bestGlobalCost = calculateCost(graph, bestGlobalRoute);

        for (int gen = 0; gen < generations; gen++) {
            List<int[]> newPopulation = new ArrayList<>();

            // Carry over best (Elitism)
            int bestIdx = 0;
            int minCost = Integer.MAX_VALUE;
            for (int i = 0; i < popSize; i++) {
                int cost = calculateCost(graph, population.get(i));
                if (cost < minCost) {
                    minCost = cost;
                    bestIdx = i;
                }
            }
            newPopulation.add(population.get(bestIdx).clone());
            if (minCost < bestGlobalCost) {
                bestGlobalCost = minCost;
                bestGlobalRoute = population.get(bestIdx).clone();
            }

            while (newPopulation.size() < popSize) {
                int[] parent1 = tournamentSelect(population, graph, 3, rand);
                int[] parent2 = tournamentSelect(population, graph, 3, rand);

                int[] child = orderedCrossover(parent1, parent2, rand);

                if (rand.nextDouble() < mutationRate) {
                    int idx1 = rand.nextInt(n);
                    int idx2 = rand.nextInt(n);
                    int temp = child[idx1];
                    child[idx1] = child[idx2];
                    child[idx2] = temp;
                }

                newPopulation.add(child);
            }

            population = newPopulation;
        }

        List<Integer> list = new ArrayList<>();
        for (int node : bestGlobalRoute) {
            list.add(node);
        }
        return new Result(bestGlobalCost, list);
    }

    static int[] tournamentSelect(List<int[]> population, int[][] graph, int size, Random rand) {
        int[] best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int[] ind = population.get(rand.nextInt(population.size()));
            int cost = calculateCost(graph, ind);
            if (cost < bestCost) {
                bestCost = cost;
                best = ind;
            }
        }
        return best;
    }

    static int[] orderedCrossover(int[] p1, int[] p2, Random rand) {
        int n = p1.length;
        int[] child = new int[n];
        Arrays.fill(child, -1);

        int c1 = rand.nextInt(n);
        int c2 = rand.nextInt(n);
        if (c1 > c2) {
            int t = c1;
            c1 = c2;
            c2 = t;
        }

        for (int i = c1; i <= c2; i++) {
            child[i] = p1[i];
        }

        int currentIdx = (c2 + 1) % n;
        for (int i = 0; i < n; i++) {
            int p2Gene = p2[(c2 + 1 + i) % n];
            boolean contains = false;
            for (int j = 0; j < n; j++) {
                if (child[j] == p2Gene) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                child[currentIdx] = p2Gene;
                currentIdx = (currentIdx + 1) % n;
            }
        }

        return child;
    }

    // 5. ANT COLONY OPTIMIZATION (ACO)
    static Result antColonyOptimization(int[][] graph, Random rand) {
        int n = graph.length;
        double[][] pheromones = new double[n][n];
        double initialPheromone = 1.0 / n;
        for (int i = 0; i < n; i++)
            Arrays.fill(pheromones[i], initialPheromone);

        int maxIterations = 100;
        int numAnts = 50;
        double alpha = 1.0;
        double beta = 5.0;
        double evaporation = 0.5;
        double Q = 1000.0;

        int bestGlobalCost = Integer.MAX_VALUE;
        List<Integer> bestGlobalRoute = new ArrayList<>();

        for (int iter = 0; iter < maxIterations; iter++) {
            int[][] antPaths = new int[numAnts][n];
            int[] antCosts = new int[numAnts];

            for (int k = 0; k < numAnts; k++) {
                boolean[] visited = new boolean[n];
                int currentCity = rand.nextInt(n);
                antPaths[k][0] = currentCity;
                visited[currentCity] = true;
                int cost = 0;

                for (int step = 1; step < n; step++) {
                    double[] probabilities = new double[n];
                    double sum = 0.0;
                    for (int i = 0; i < n; i++) {
                        if (!visited[i] && graph[currentCity][i] != -1) {
                            double tau = Math.pow(pheromones[currentCity][i], alpha);
                            double eta = Math.pow(1.0 / graph[currentCity][i], beta);
                            probabilities[i] = tau * eta;
                            sum += probabilities[i];
                        }
                    }

                    double r = rand.nextDouble() * sum;
                    double cumulative = 0.0;
                    int nextCity = -1;
                    for (int i = 0; i < n; i++) {
                        if (!visited[i] && graph[currentCity][i] != -1) {
                            cumulative += probabilities[i];
                            if (cumulative >= r) {
                                nextCity = i;
                                break;
                            }
                        }
                    }
                    if (nextCity == -1) {
                        for (int i = 0; i < n; i++) {
                            if (!visited[i] && graph[currentCity][i] != -1) {
                                nextCity = i;
                                break;
                            }
                        }
                    }

                    antPaths[k][step] = nextCity;
                    visited[nextCity] = true;
                    cost += graph[currentCity][nextCity];
                    currentCity = nextCity;
                }
                cost += graph[antPaths[k][n - 1]][antPaths[k][0]];
                antCosts[k] = cost;

                if (cost < bestGlobalCost) {
                    bestGlobalCost = cost;
                    bestGlobalRoute.clear();
                    for (int city : antPaths[k]) {
                        bestGlobalRoute.add(city);
                    }
                }
            }

            // Evaporate
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    pheromones[i][j] *= (1.0 - evaporation);
                }
            }

            // Deposit
            for (int k = 0; k < numAnts; k++) {
                double deposit = Q / antCosts[k];
                for (int i = 0; i < n - 1; i++) {
                    int u = antPaths[k][i];
                    int v = antPaths[k][i + 1];
                    pheromones[u][v] += deposit;
                    pheromones[v][u] += deposit;
                }
                pheromones[antPaths[k][n - 1]][antPaths[k][0]] += deposit;
                pheromones[antPaths[k][0]][antPaths[k][n - 1]] += deposit;
            }
        }
        return new Result(bestGlobalCost, bestGlobalRoute);
    }

    // Validation
    static boolean validateRoute(List<Integer> route, int n) {
        if (route.size() != n)
            return false;
        Set<Integer> unique = new HashSet<>(route);
        return unique.size() == n;
    }
}
