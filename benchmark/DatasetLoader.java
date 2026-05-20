package benchmark;

import java.nio.file.*;
import java.util.*;

public class DatasetLoader {

    public static double[][] load(
            String filename)
            throws Exception {

        List<double[]> cities = new ArrayList<>();

        List<String> lines = Files.readAllLines(
                Paths.get(filename));

        boolean read = false;

        for (String line : lines) {

            line = line.trim();

            if (line.equals(
                    "NODE_COORD_SECTION")) {

                read = true;
                continue;
            }

            if (line.equals("EOF"))
                break;

            if (!read)
                continue;

            String[] p = line.split("\\s+");

            if (p.length < 3)
                continue;

            cities.add(
                    new double[] {

                            Double.parseDouble(
                                    p[1]),

                            Double.parseDouble(
                                    p[2])

                    });

        }

        return cities.toArray(
                new double[0][]);

    }

}