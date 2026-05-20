package benchmark;

public interface TSPSolver {
    String getName();

    int[] solve(double[][] cities);

}
