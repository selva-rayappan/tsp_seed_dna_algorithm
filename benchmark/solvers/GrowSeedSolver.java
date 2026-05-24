package benchmark.solvers;

import benchmark.Metrics;
import benchmark.TSPSolver;

import java.util.*;
import java.util.stream.*;
import java.util.concurrent.ForkJoinPool;

public class GrowSeedSolver implements TSPSolver {

    private int threads = Runtime.getRuntime().availableProcessors();
    private int routeCountMultiplier = 5;

    public GrowSeedSolver() {
    }

    public GrowSeedSolver(int threads) {
        this.threads = threads;
    }

    @Override
    public String getName() {
        return "GrowSeed";
    }

    @Override
    public int[] solve(double[][] cities) {
        int n = cities.length;
        if (n <= 2) {
            int[] route = new int[n];
            for (int i = 0; i < n; i++)
                route[i] = i;
            return route;
        }

        int[][] graph = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                graph[i][j] = (int) Math.round(Metrics.distance(cities[i], cities[j]));
            }
        }

        Result res = tspSeedDNA(cities, graph, new Random(42));
        int[] routeArray = new int[n];
        for (int i = 0; i < n; i++) {
            routeArray[i] = res.route.get(i);
        }

        // Final Optimization Pipeline
        apply2Opt(graph, routeArray);
        apply3Opt(graph, routeArray);

        return routeArray;
    }

    static class Result {
        int cost;
        List<Integer> route;

        Result(int cost, List<Integer> route) {
            this.cost = cost;
            this.route = route;
        }
    }

    static class RouteWithCost {
        int[] route;
        int cost;

        RouteWithCost(int[] route, int cost) {
            this.route = route;
            this.cost = cost;
        }
    }

    static abstract class Seed {
        String key;
        int[] nodes;
        double weight;
        int bestCost;

        Seed(String key, int[] nodes) {
            this.key = key;
            this.nodes = nodes;
            this.weight = 1.0;
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

    // ==========================================
    // PRIMITIVE OPEN-ADDRESSING HASH MAPS
    // ==========================================
    static class PrimitiveLongIntMap {
        private long[] keys;
        private int[] values;
        private int capacity;
        private int mask;
        private static final long EMPTY = 0L;

        PrimitiveLongIntMap(int expectedSize) {
            this.capacity = 1;
            while (this.capacity < expectedSize * 2) {
                this.capacity <<= 1;
            }
            this.mask = this.capacity - 1;
            this.keys = new long[this.capacity];
            this.values = new int[this.capacity];
        }

        void clear() {
            Arrays.fill(keys, EMPTY);
        }

        void put(long key, int val) {
            if (key == EMPTY)
                return;
            int idx = (int) (hash(key) & mask);
            while (keys[idx] != EMPTY) {
                if (keys[idx] == key) {
                    values[idx] = val;
                    return;
                }
                idx = (idx + 1) & mask;
            }
            keys[idx] = key;
            values[idx] = val;
        }

        int get(long key, int defaultVal) {
            if (key == EMPTY)
                return defaultVal;
            int idx = (int) (hash(key) & mask);
            while (keys[idx] != EMPTY) {
                if (keys[idx] == key) {
                    return values[idx];
                }
                idx = (idx + 1) & mask;
            }
            return defaultVal;
        }

        private long hash(long x) {
            x ^= x >>> 33;
            x *= 0xff51afd7ed558ccdL;
            x ^= x >>> 33;
            x *= 0xc4ceb9fe1a85ec53L;
            x ^= x >>> 33;
            return x;
        }
    }

    // ==========================================
    // 2D GEOMETRIC SEGMENT CROSSING UTILITIES
    // ==========================================
    static boolean segmentsIntersect(double[] p1, double[] q1, double[] p2, double[] q2) {
        int o1 = orientation(p1, q1, p2);
        int o2 = orientation(p1, q1, q2);
        int o3 = orientation(p2, q2, p1);
        int o4 = orientation(p2, q2, q1);

        if (o1 != o2 && o3 != o4) {
            return true;
        }

        if (o1 == 0 && onSegment(p1, p2, q1))
            return true;
        if (o2 == 0 && onSegment(p1, q2, q1))
            return true;
        if (o3 == 0 && onSegment(p2, p1, q2))
            return true;
        if (o4 == 0 && onSegment(p2, q1, q2))
            return true;

        return false;
    }

    static int orientation(double[] p, double[] q, double[] r) {
        double val = (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1]);
        if (Math.abs(val) < 1e-9)
            return 0;
        return (val > 0) ? 1 : 2;
    }

    static boolean onSegment(double[] p, double[] q, double[] r) {
        return q[0] <= Math.max(p[0], r[0]) && q[0] >= Math.min(p[0], r[0]) &&
                q[1] <= Math.max(p[1], r[1]) && q[1] >= Math.min(p[1], r[1]);
    }

    static boolean segmentsCross(double[][] cities, int u1, int v1, int u2, int v2) {
        if (u1 == u2 || u1 == v2 || v1 == u2 || v1 == v2) {
            return false;
        }
        return segmentsIntersect(cities[u1], cities[v1], cities[u2], cities[v2]);
    }

    static boolean seedsConflict(double[][] cities, Seed s1, Seed s2) {
        for (int i = 0; i < s1.nodes.length - 1; i++) {
            for (int j = 0; j < s2.nodes.length - 1; j++) {
                if (segmentsCross(cities, s1.nodes[i], s1.nodes[i + 1], s2.nodes[j], s2.nodes[j + 1])) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean wouldCrossExistingPath(double[][] cities, List<Integer> path, List<int[]> newEdges) {
        for (int[] edge : newEdges) {
            for (int i = 0; i < path.size() - 1; i++) {
                if (segmentsCross(cities, edge[0], edge[1], path.get(i), path.get(i + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    static int calculateCost(int[][] graph, int[] route) {
        int cost = 0;
        int n = route.length;
        for (int i = 0; i < n - 1; i++) {
            cost += graph[route[i]][route[i + 1]];
        }
        cost += graph[route[n - 1]][route[0]];
        return cost;
    }

    static int calculateCost(int[][] graph, List<Integer> route) {
        int cost = 0;
        int n = route.size();
        for (int i = 0; i < n - 1; i++) {
            cost += graph[route.get(i)][route.get(i + 1)];
        }
        cost += graph[route.get(n - 1)][route.get(0)];
        return cost;
    }

    static int[] generateRandomNNRoute(int[][] graph, int[][] neighborList, int n, Random rand) {
        int[] route = new int[n];
        boolean[] visited = new boolean[n];
        int startCity = rand.nextInt(n);
        route[0] = startCity;
        visited[startCity] = true;
        int currentCity = startCity;

        for (int count = 1; count < n; count++) {
            int nextCity = -1;
            int minCost = Integer.MAX_VALUE;

            for (int neighbor : neighborList[currentCity]) {
                if (!visited[neighbor] && graph[currentCity][neighbor] != -1
                        && graph[currentCity][neighbor] < minCost) {
                    minCost = graph[currentCity][neighbor];
                    nextCity = neighbor;
                }
            }

            if (nextCity == -1) {
                for (int j = 0; j < n; j++) {
                    if (!visited[j] && graph[currentCity][j] != -1 && graph[currentCity][j] < minCost) {
                        minCost = graph[currentCity][j];
                        nextCity = j;
                    }
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

    Result tspSeedDNA(double[][] cities, int[][] graph, Random rand) {
        int n = graph.length;
        Map<Long, Seed> globalSeeds = new LinkedHashMap<>();

        int kNeighbor = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, kNeighbor);

        int maxIterations = graph.length;
        int iter = 0;
        boolean newGroupsFormed = true;

        ForkJoinPool customThreadPool = (threads == Runtime.getRuntime().availableProcessors()) ? null
                : new ForkJoinPool(threads);
        try {
            while (newGroupsFormed && iter < maxIterations) {
                newGroupsFormed = false;
                iter++;

                // 1. Initial Route Generation (with Fixed Oversampling & Top-N Selection)
                int poolSize = n * routeCountMultiplier;
                long[] threadSeeds = new long[poolSize];
                for (int i = 0; i < poolSize; i++) {
                    threadSeeds[i] = rand.nextLong();
                }

                List<int[]> initialRoutes;
                if (customThreadPool == null) {
                    initialRoutes = IntStream.range(0, poolSize)
                            .parallel()
                            .mapToObj(r -> {
                                Random threadRand = new Random(threadSeeds[r]);
                                return generateRandomNNRoute(graph, neighborList, n, threadRand);
                            })
                            .collect(Collectors.toList());
                } else {
                    try {
                        initialRoutes = customThreadPool.submit(() -> IntStream.range(0, poolSize)
                                .parallel()
                                .mapToObj(r -> {
                                    Random threadRand = new Random(threadSeeds[r]);
                                    return generateRandomNNRoute(graph, neighborList, n, threadRand);
                                })
                                .collect(Collectors.toList())).get();
                    } catch (Exception e) {
                        initialRoutes = IntStream.range(0, poolSize)
                                .parallel()
                                .mapToObj(r -> {
                                    Random threadRand = new Random(threadSeeds[r]);
                                    return generateRandomNNRoute(graph, neighborList, n, threadRand);
                                })
                                .collect(Collectors.toList());
                    }
                }

                List<RouteWithCost> pool = new ArrayList<>(poolSize);
                for (int[] route : initialRoutes) {
                    pool.add(new RouteWithCost(route, calculateCost(graph, route)));
                }

                pool.sort(Comparator.comparingInt(a -> a.cost));

                List<int[]> keptRoutes = new ArrayList<>(n);
                List<Integer> keptCosts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    RouteWithCost rwc = pool.get(i);
                    keptRoutes.add(rwc.route);
                    keptCosts.add(rwc.cost);
                }

                // 2. Primitive Packed Seed Hashing (Zero Allocation mining)
                Map<Long, List<Integer>> seedToRouteIndices = new HashMap<>();
                for (int r = 0; r < keptRoutes.size(); r++) {
                    int[] route = keptRoutes.get(r);
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

                Map<Long, Integer> subpathFreq = new HashMap<>();
                for (int[] route : keptRoutes) {
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

                for (Seed s : currentIterationSeeds) {
                    long key = s.getPackedKey();
                    if (!globalSeeds.containsKey(key)) {
                        List<Integer> rIds = seedToRouteIndices.get(key);
                        if (rIds != null && !rIds.isEmpty()) {
                            s.bestCost = keptCosts.get(rIds.get(0));
                        }
                        globalSeeds.put(key, s);
                        newGroupsFormed = true;
                    }
                }

                // 3. Probabilistic Seed Weighting (Z-Score Penalization)
                double tourCostSum = 0;
                for (int cost : keptCosts) {
                    tourCostSum += cost;
                }
                double mean = tourCostSum / keptCosts.size();
                double sumSq = 0;
                for (int cost : keptCosts) {
                    sumSq += (cost - mean) * (cost - mean);
                }
                double stdDev = Math.sqrt(sumSq / keptCosts.size());
                if (stdDev < 1e-6) {
                    stdDev = 1.0;
                }

                double beta = 1.0;
                for (Seed s : globalSeeds.values()) {
                    List<Integer> rIds = seedToRouteIndices.get(s.getPackedKey());
                    if (rIds != null && !rIds.isEmpty()) {
                        int bestR = rIds.get(0);
                        double z = (keptCosts.get(bestR) - mean) / stdDev;
                        double term = Math.exp(-beta * z) - 1.0;
                        s.weight += 0.5 * term;
                    }
                }
            }
        } finally {
            if (customThreadPool != null) {
                customThreadPool.shutdown();
            }
        }

        return finalizeRoute(cities, graph, globalSeeds);
    }

    static Result finalizeRoute(double[][] cities, int[][] graph, Map<Long, Seed> globalSeeds) {
        int n = graph.length;
        List<Seed> seedPool = new ArrayList<>();
        for (Seed s : globalSeeds.values()) {
            if (s.weight > 0) {
                seedPool.add(s);
            }
        }

        seedPool.sort((a, b) -> {
            int cmp = Double.compare(b.weight, a.weight);
            if (cmp != 0)
                return cmp;
            return Integer.compare(a.bestCost, b.bestCost);
        });

        // Seed Compatibility Graph (MWIS) pre-processing
        int poolSize = seedPool.size();
        boolean[] inIS = new boolean[poolSize];
        boolean[] active = new boolean[poolSize];
        Arrays.fill(active, true);

        for (int i = 0; i < poolSize; i++) {
            if (!active[i])
                continue;
            inIS[i] = true;
            Seed s1 = seedPool.get(i);
            for (int j = i + 1; j < poolSize; j++) {
                if (active[j] && seedsConflict(cities, s1, seedPool.get(j))) {
                    active[j] = false;
                }
            }
        }

        for (int i = 0; i < poolSize; i++) {
            if (!inIS[i]) {
                seedPool.get(i).weight *= 0.1;
            }
        }

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

        Seed bestSeed = seedPool.get(0);
        for (int node : bestSeed.nodes) {
            path.add(node);
            visited[node] = true;
        }
        bestSeed.weight = -1.0;

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
            int chosenType = 0;
            int chosenK = -1;
            List<Seed> newDecomposedSeeds = new ArrayList<>();
            boolean poolModified = false;

            Seed bestCandidateSeed = null;
            int bestCandidateType = 0;
            int bestCandidateK = -1;
            double bestCandidateScore = -1.0;

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

                int k = -1;
                for (int j = 0; j < L; j++) {
                    if (visited[nodes[j]]) {
                        k = j;
                        break;
                    }
                }

                if (k == -1)
                    continue;

                boolean hasValidStructuralExtension = false;

                if (nodes[k] == back) {
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
                        hasValidStructuralExtension = true;
                        List<int[]> newEdges = new ArrayList<>();
                        if (k + 1 < L) {
                            newEdges.add(new int[] { back, nodes[k + 1] });
                        }
                        for (int j = k + 1; j < L - 1; j++) {
                            newEdges.add(new int[] { nodes[j], nodes[j + 1] });
                        }
                        double penalty = wouldCrossExistingPath(cities, path, newEdges) ? 0.01 : 1.0;
                        double score = s.weight * penalty;
                        if (score > bestCandidateScore) {
                            bestCandidateScore = score;
                            bestCandidateSeed = s;
                            bestCandidateType = 1;
                            bestCandidateK = k;
                        }
                    }

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
                        hasValidStructuralExtension = true;
                        List<int[]> newEdges = new ArrayList<>();
                        if (k - 1 >= 0) {
                            newEdges.add(new int[] { back, nodes[k - 1] });
                        }
                        for (int j = k - 1; j >= 1; j--) {
                            newEdges.add(new int[] { nodes[j], nodes[j - 1] });
                        }
                        double penalty = wouldCrossExistingPath(cities, path, newEdges) ? 0.01 : 1.0;
                        double score = s.weight * penalty;
                        if (score > bestCandidateScore) {
                            bestCandidateScore = score;
                            bestCandidateSeed = s;
                            bestCandidateType = 2;
                            bestCandidateK = k;
                        }
                    }
                }

                if (nodes[k] == front) {
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
                        hasValidStructuralExtension = true;
                        List<int[]> newEdges = new ArrayList<>();
                        if (k - 1 >= 0) {
                            newEdges.add(new int[] { nodes[k - 1], front });
                        }
                        for (int j = 0; j < k - 1; j++) {
                            newEdges.add(new int[] { nodes[j], nodes[j + 1] });
                        }
                        double penalty = wouldCrossExistingPath(cities, path, newEdges) ? 0.01 : 1.0;
                        double score = s.weight * penalty;
                        if (score > bestCandidateScore) {
                            bestCandidateScore = score;
                            bestCandidateSeed = s;
                            bestCandidateType = 3;
                            bestCandidateK = k;
                        }
                    }

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
                        hasValidStructuralExtension = true;
                        List<int[]> newEdges = new ArrayList<>();
                        if (k + 1 < L) {
                            newEdges.add(new int[] { nodes[k + 1], front });
                        }
                        for (int j = L - 1; j >= k + 2; j--) {
                            newEdges.add(new int[] { nodes[j], nodes[j - 1] });
                        }
                        double penalty = wouldCrossExistingPath(cities, path, newEdges) ? 0.01 : 1.0;
                        double score = s.weight * penalty;
                        if (score > bestCandidateScore) {
                            bestCandidateScore = score;
                            bestCandidateSeed = s;
                            bestCandidateType = 4;
                            bestCandidateK = k;
                        }
                    }
                }

                if (!hasValidStructuralExtension) {
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

            if (bestCandidateSeed != null) {
                chosenSeed = bestCandidateSeed;
                chosenType = bestCandidateType;
                chosenK = bestCandidateK;
            }

            if (chosenSeed != null) {
                int[] nodes = chosenSeed.nodes;
                int L = nodes.length;
                int k = chosenK;
                if (chosenType == 1) {
                    for (int j = k + 1; j < L; j++) {
                        path.add(nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 2) {
                    for (int j = k - 1; j >= 0; j--) {
                        path.add(nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 3) {
                    for (int j = k - 1; j >= 0; j--) {
                        path.add(0, nodes[j]);
                        visited[nodes[j]] = true;
                    }
                } else if (chosenType == 4) {
                    for (int j = k + 1; j < L; j++) {
                        path.add(0, nodes[j]);
                        visited[nodes[j]] = true;
                    }
                }
                chosenSeed.weight = -1.0;
                extended = true;
            }

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

            if (!extended && path.size() < n) {
                int backNode = path.get(path.size() - 1);
                int nextCity = -1;
                int minCost = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!visited[i] && graph[backNode][i] != -1 && graph[backNode][i] < minCost) {
                        if (!segmentsCross(cities, backNode, i, path.get(path.size() - 2), backNode)) {
                            minCost = graph[backNode][i];
                            nextCity = i;
                        }
                    }
                }
                if (nextCity == -1) {
                    for (int i = 0; i < n; i++) {
                        if (!visited[i] && graph[backNode][i] != -1 && graph[backNode][i] < minCost) {
                            minCost = graph[backNode][i];
                            nextCity = i;
                        }
                    }
                }
                if (nextCity != -1) {
                    path.add(nextCity);
                    visited[nextCity] = true;
                    extended = true;
                }
            }
        }

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

    // ==========================================
    // OPTIMIZATION PIPELINE HELPERS
    // ==========================================
    static void reverse(int[] route, int i, int j) {
        while (i < j) {
            int temp = route[i];
            route[i] = route[j];
            route[j] = temp;
            i++;
            j--;
        }
    }

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

    static void apply2Opt(int[][] graph, int[] route, int[][] neighborList) {
        int n = graph.length;
        int[] pos = new int[n];
        for (int idx = 0; idx < n; idx++) {
            pos[route[idx]] = idx;
        }

        boolean[] dontLook = new boolean[n];
        boolean improved = true;
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

    static void apply2Opt(int[][] graph, int[] route) {
        int n = graph.length;
        int k = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, k);
        apply2Opt(graph, route, neighborList);
    }

    static void apply3Opt(int[][] graph, int[] route) {
        int n = graph.length;
        int kNeighbor = Math.min(20, n - 1);
        int[][] neighborList = computeNeighborList(graph, kNeighbor);

        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            pos[route[i]] = i;
        }

        boolean[] isCandidate = new boolean[n];
        int[] candidates = new int[4 * kNeighbor];

        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < n; i++) {
                int A = route[i];
                int B = route[(i + 1) % n];
                for (int nextNeighbor : neighborList[A]) {
                    int idx = pos[nextNeighbor];
                    if (idx == i || idx == (i + 1) % n || idx == (i - 1 + n) % n) {
                        continue;
                    }
                    int C = route[idx];
                    int D = route[(idx + 1) % n];

                    int candCount = 0;
                    for (int neighbor : neighborList[A]) {
                        int p3 = pos[neighbor];
                        if (p3 != i && p3 != idx && !isCandidate[p3]) {
                            isCandidate[p3] = true;
                            candidates[candCount++] = p3;
                        }
                    }
                    for (int neighbor : neighborList[B]) {
                        int p3 = pos[neighbor];
                        if (p3 != i && p3 != idx && !isCandidate[p3]) {
                            isCandidate[p3] = true;
                            candidates[candCount++] = p3;
                        }
                    }
                    for (int neighbor : neighborList[C]) {
                        int p3 = pos[neighbor];
                        if (p3 != i && p3 != idx && !isCandidate[p3]) {
                            isCandidate[p3] = true;
                            candidates[candCount++] = p3;
                        }
                    }
                    for (int neighbor : neighborList[D]) {
                        int p3 = pos[neighbor];
                        if (p3 != i && p3 != idx && !isCandidate[p3]) {
                            isCandidate[p3] = true;
                            candidates[candCount++] = p3;
                        }
                    }

                    for (int c = 0; c < candCount; c++) {
                        isCandidate[candidates[c]] = false;
                    }

                    for (int c = 0; c < candCount; c++) {
                        int p3 = candidates[c];

                        int iPrime = i;
                        int jPrime = idx;
                        int kPrime = p3;

                        if (iPrime > jPrime) {
                            int t = iPrime;
                            iPrime = jPrime;
                            jPrime = t;
                        }
                        if (jPrime > kPrime) {
                            int t = jPrime;
                            jPrime = kPrime;
                            kPrime = t;
                        }
                        if (iPrime > jPrime) {
                            int t = iPrime;
                            iPrime = jPrime;
                            jPrime = t;
                        }

                        int nodeA = route[iPrime];
                        int nodeB = route[(iPrime + 1) % n];
                        int nodeC = route[jPrime];
                        int nodeD = route[(jPrime + 1) % n];
                        int nodeE = route[kPrime];
                        int nodeF = route[(kPrime + 1) % n];

                        int d0 = graph[nodeA][nodeB] + graph[nodeC][nodeD] + graph[nodeE][nodeF];

                        int d1 = graph[nodeA][nodeD] + graph[nodeE][nodeB] + graph[nodeC][nodeF];
                        if (d1 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 1);
                            for (int p = 0; p < n; p++)
                                pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        int d2 = graph[nodeA][nodeD] + graph[nodeE][nodeC] + graph[nodeB][nodeF];
                        if (d2 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 2);
                            for (int p = 0; p < n; p++)
                                pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        int d3 = graph[nodeA][nodeE] + graph[nodeD][nodeB] + graph[nodeC][nodeF];
                        if (d3 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 3);
                            for (int p = 0; p < n; p++)
                                pos[route[p]] = p;
                            improved = true;
                            break;
                        }

                        int d4 = graph[nodeA][nodeE] + graph[nodeD][nodeC] + graph[nodeB][nodeF];
                        if (d4 < d0) {
                            reconnect3Opt(route, iPrime, jPrime, kPrime, 4);
                            for (int p = 0; p < n; p++)
                                pos[route[p]] = p;
                            improved = true;
                            break;
                        }
                    }
                    if (improved)
                        break;
                }
                if (improved)
                    break;
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
}
