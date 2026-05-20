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
    private int routeCountMultiplier = 3; // Default scaling multiplier

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
            return (1L << 48) | (((long) nodes[0]) << 32) | (((long) nodes[1]) << 16) | nodes[2];
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
            return (((long) nodes[0]) << 16) | nodes[1];
        }
    }

    static long getPackedTripletKey(int u, int v, int w) {
        if (u > w) {
            return ((long) w << 32) | ((long) v << 16) | u;
        } else {
            return ((long) u << 32) | ((long) v << 16) | w;
        }
    }

    static long getPackedPairKey(int u, int v) {
        if (u > v) {
            return ((long) v << 16) | u;
        } else {
            return ((long) u << 16) | v;
        }
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

    // Helper: Apply 2-opt local search to a route
    static void apply2Opt(int[][] graph, int[] route) {
        int n = graph.length;
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                int prevI = i - 1;
                for (int j = i + 1; j < n; j++) {
                    int nextJ = (j == n - 1) ? 0 : j + 1;
                    if (nextJ == prevI)
                        continue;
                    int currentEdge1 = graph[route[prevI]][route[i]];
                    int currentEdge2 = graph[route[j]][route[nextJ]];
                    int newEdge1 = graph[route[prevI]][route[j]];
                    int newEdge2 = graph[route[i]][route[nextJ]];
                    if (newEdge1 + newEdge2 < currentEdge1 + currentEdge2) {
                        reverse(route, i, j);
                        improved = true;
                    }
                }
            }
        }
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
        Map<String, Seed> globalSeeds = new LinkedHashMap<>();
        int seedCounter = 1;

        int maxIterations = graph.length;
        int iter = 0;
        boolean newGroupsFormed = true;

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
                                apply2Opt(graph, route);
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
                                    apply2Opt(graph, route);
                                    return route;
                                })
                                .collect(Collectors.toList())).get();
                    } catch (Exception e) {
                        routes = IntStream.range(0, routeCount)
                                .parallel()
                                .mapToObj(r -> {
                                    Random threadRand = new Random(threadSeeds[r]);
                                    int[] route = generateRandomNNRoute(graph, n, threadRand);
                                    apply2Opt(graph, route);
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
                    for (int i = 0; i < n; i++) {
                        int u = route[i];
                        int v = route[i + 1 == n ? 0 : i + 1];
                        int w = route[(i + 2 >= n) ? (i + 2 - n) : i + 2];

                        // Triplet key
                        long tKey = (1L << 48) | getPackedTripletKey(u, v, w);
                        List<Integer> tList = seedToRouteIndices.computeIfAbsent(tKey, k -> new ArrayList<>());
                        if (tList.isEmpty() || tList.get(tList.size() - 1) != r) {
                            tList.add(r);
                        }

                        // Pair keys
                        long pKey1 = getPackedPairKey(u, v);
                        List<Integer> pList1 = seedToRouteIndices.computeIfAbsent(pKey1, k -> new ArrayList<>());
                        if (pList1.isEmpty() || pList1.get(pList1.size() - 1) != r) {
                            pList1.add(r);
                        }

                        long pKey2 = getPackedPairKey(v, w);
                        List<Integer> pList2 = seedToRouteIndices.computeIfAbsent(pKey2, k -> new ArrayList<>());
                        if (pList2.isEmpty() || pList2.get(pList2.size() - 1) != r) {
                            pList2.add(r);
                        }
                    }
                }

                // 3. Find repeating nodes in group of 3 (triplets)
                Map<Long, Integer> tripletFreq = new HashMap<>();

                for (int[] route : sortedRoutes) {
                    for (int i = 0; i < n; i++) {
                        int u = route[i];
                        int v = route[i + 1 == n ? 0 : i + 1];
                        int w = route[(i + 2 >= n) ? (i + 2 - n) : i + 2];
                        long pKey = getPackedTripletKey(u, v, w);
                        tripletFreq.put(pKey, tripletFreq.getOrDefault(pKey, 0) + 1);
                    }
                }

                // Identify repeating triplets in current iteration (frequency >= 2)
                List<TripletSeed> currentIterationSeeds = new ArrayList<>();
                for (Map.Entry<Long, Integer> entry : tripletFreq.entrySet()) {
                    if (entry.getValue() >= 2) {
                        long packed = entry.getKey();
                        int n0 = (int) (packed >> 32) & 0xFFFF;
                        int n1 = (int) (packed >> 16) & 0xFFFF;
                        int n2 = (int) packed & 0xFFFF;
                        currentIterationSeeds.add(new TripletSeed(n0, n1, n2));
                    }
                }

                // 6. Compare seeds from Iteration N and N+1, add weight (+0.6) for repeating
                // seeds
                for (TripletSeed ts : currentIterationSeeds) {
                    if (globalSeeds.containsKey(ts.key)) {
                        globalSeeds.get(ts.key).weight += 0.6;
                    } else {
                        ts.name = "S" + seedCounter++;
                        List<Integer> rIds = seedToRouteIndices.get(ts.getPackedKey());
                        ts.bestCost = sortedCosts.get(rIds.get(0));
                        globalSeeds.put(ts.key, ts);
                        newGroupsFormed = true;
                    }
                }

                if (overlapSeeds) {
                    // 7. If there are repeating nodes across existing seeds, name them as separate
                    // seeds (Pair Seeds)
                    Map<String, Integer> pairAcrossSeedsCount = new HashMap<>();
                    Map<String, PairSeed> pairAcrossSeedsObjects = new HashMap<>();

                    for (Seed s : globalSeeds.values()) {
                        List<PairSeed> edges = getEdges(s);
                        for (PairSeed ps : edges) {
                            pairAcrossSeedsCount.put(ps.key, pairAcrossSeedsCount.getOrDefault(ps.key, 0) + 1);
                            pairAcrossSeedsObjects.put(ps.key, ps);
                        }
                    }

                    for (Map.Entry<String, Integer> entry : pairAcrossSeedsCount.entrySet()) {
                        if (entry.getValue() >= 2) {
                            String pKey = entry.getKey();
                            if (!globalSeeds.containsKey(pKey)) {
                                PairSeed ps = pairAcrossSeedsObjects.get(pKey);
                                ps.name = "S" + seedCounter++;
                                List<Integer> rIds = seedToRouteIndices.get(ps.getPackedKey());
                                ps.bestCost = sortedCosts.get(rIds.get(0));
                                globalSeeds.put(pKey, ps);
                                newGroupsFormed = true;
                            }
                        }
                    }
                }

                if (penalization) {
                    // 8. Reduce weight (-0.2) for Seeds if they increase the cost in any 3
                    // iterations
                    int cMin = sortedCosts.get(0);
                    for (Seed s : globalSeeds.values()) {
                        List<Integer> rIds = seedToRouteIndices.get(s.getPackedKey());
                        if (rIds != null && !rIds.isEmpty()) {
                            int minCostWithS = sortedCosts.get(rIds.get(0));
                            if (minCostWithS > cMin) {
                                s.costIncreaseCount++;
                                if (s.costIncreaseCount >= 3) {
                                    s.weight -= 0.2;
                                }
                            }
                        }
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
        apply2Opt(graph, routeArray);
        List<Integer> finalRouteList = new ArrayList<>();
        for (int node : routeArray) {
            finalRouteList.add(node);
        }
        return new Result(calculateCost(graph, finalRouteList), finalRouteList);
    }

    // Helper: Finalize route based on seed weights
    static Result finalizeRoute(int[][] graph, Map<String, Seed> globalSeeds) {
        int n = graph.length;
        List<Seed> sortedSeeds = new ArrayList<>();
        for (Seed s : globalSeeds.values()) {
            if (s.weight > 0) {
                sortedSeeds.add(s);
            }
        }

        // Sort: primary key = weight (descending), secondary key = bestCost (ascending)
        sortedSeeds.sort((a, b) -> {
            int cmp = Double.compare(b.weight, a.weight);
            if (cmp != 0)
                return cmp;
            return Integer.compare(a.bestCost, b.bestCost);
        });

        if (sortedSeeds.isEmpty()) {
            return pureGreedy(graph);
        }

        List<Integer> path = new ArrayList<>();
        boolean[] visited = new boolean[n];

        // Seed with highest weight
        Seed bestSeed = sortedSeeds.get(0);
        for (int node : bestSeed.nodes) {
            path.add(node);
            visited[node] = true;
        }

        // Build endpoint/node-adjacency list map for fast seed lookups
        Map<Integer, List<Seed>> seedsByNode = new HashMap<>();
        for (Seed s : sortedSeeds) {
            for (int node : s.nodes) {
                seedsByNode.computeIfAbsent(node, k -> new ArrayList<>()).add(s);
            }
        }

        boolean extended = true;
        while (path.size() < n && extended) {
            extended = false;
            int back = path.get(path.size() - 1);
            int front = path.get(0);

            List<Seed> backCandidates = seedsByNode.getOrDefault(back, Collections.emptyList());
            List<Seed> frontCandidates = seedsByNode.getOrDefault(front, Collections.emptyList());

            // Try back extension first
            for (Seed s : backCandidates) {
                if (s instanceof PairSeed) {
                    int u = s.nodes[0];
                    int v = s.nodes[1];
                    if (back == u && !visited[v]) {
                        path.add(v);
                        visited[v] = true;
                        extended = true;
                        break;
                    } else if (back == v && !visited[u]) {
                        path.add(u);
                        visited[u] = true;
                        extended = true;
                        break;
                    }
                } else if (s instanceof TripletSeed) {
                    int u = s.nodes[0];
                    int v = s.nodes[1];
                    int w = s.nodes[2];
                    if (path.size() >= 2) {
                        int prevBack = path.get(path.size() - 2);
                        if (prevBack == u && back == v && !visited[w]) {
                            path.add(w);
                            visited[w] = true;
                            extended = true;
                            break;
                        }
                        if (prevBack == w && back == v && !visited[u]) {
                            path.add(u);
                            visited[u] = true;
                            extended = true;
                            break;
                        }
                    }
                    if (back == u && !visited[v] && !visited[w]) {
                        path.add(v);
                        path.add(w);
                        visited[v] = true;
                        visited[w] = true;
                        extended = true;
                        break;
                    } else if (back == w && !visited[v] && !visited[u]) {
                        path.add(v);
                        path.add(u);
                        visited[v] = true;
                        visited[u] = true;
                        extended = true;
                        break;
                    }
                }
            }

            if (extended)
                continue;

            // Try front extension
            for (Seed s : frontCandidates) {
                if (s instanceof PairSeed) {
                    int u = s.nodes[0];
                    int v = s.nodes[1];
                    if (front == u && !visited[v]) {
                        path.add(0, v);
                        visited[v] = true;
                        extended = true;
                        break;
                    } else if (front == v && !visited[u]) {
                        path.add(0, u);
                        visited[u] = true;
                        extended = true;
                        break;
                    }
                } else if (s instanceof TripletSeed) {
                    int u = s.nodes[0];
                    int v = s.nodes[1];
                    int w = s.nodes[2];
                    if (path.size() >= 2) {
                        int nextFront = path.get(1);
                        if (front == v && nextFront == w && !visited[u]) {
                            path.add(0, u);
                            visited[u] = true;
                            extended = true;
                            break;
                        }
                        if (front == v && nextFront == u && !visited[w]) {
                            path.add(0, w);
                            visited[w] = true;
                            extended = true;
                            break;
                        }
                    }
                    if (front == u && !visited[v] && !visited[w]) {
                        path.add(0, v);
                        path.add(0, w);
                        visited[v] = true;
                        visited[w] = true;
                        extended = true;
                        break;
                    } else if (front == w && !visited[v] && !visited[u]) {
                        path.add(0, v);
                        path.add(0, u);
                        visited[v] = true;
                        visited[u] = true;
                        extended = true;
                        break;
                    }
                }
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
