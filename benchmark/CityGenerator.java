package benchmark;

import java.util.*;

public class CityGenerator {

    static Random r = new Random(42);

    public static double[][] generate(
            int n) {

        double[][] cities = new double[n][2];

        for (int i = 0; i < n; i++) {

            cities[i][0] = r.nextDouble()
                    * 1000;

            cities[i][1] = r.nextDouble()
                    * 1000;

        }

        return cities;

    }

}
