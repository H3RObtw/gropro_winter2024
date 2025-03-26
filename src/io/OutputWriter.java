package io;

import model.CustomerOrder;
import model.InputData;
import model.PlacementResult;
import model.Point;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

public class OutputWriter {

    public static void writeOutput(String baseFilename, InputData input, PlacementResult result) throws IOException {
        writeTextOutput(baseFilename + ".out", input, result);
        writeGnuplotScript(baseFilename + ".gnu", baseFilename + ".png", input, result);
        runGnuplot(baseFilename + ".gnu"); // Optional: execute gnuplot
    }

    private static void writeTextOutput(String filename, InputData input, PlacementResult result) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(input.description() + "\n");
            writer.write(String.format(Locale.US,"Benötgte Länge: %.1fcm%n", (double)result.totalHeight() / 10.0)); // mm to cm
            writer.write(String.format(Locale.US,"Genutzte Flaeche: %.2f%%%n", result.utilization()));
            writer.newLine();
            writer.write("Positionierung der Kundenaufträge:\n");
            // Sort orders by ID for consistent output
            List<CustomerOrder> sortedOrders = result.placedOrders().stream()
                    .sorted(Comparator.comparingInt(o -> o.id))
                    .toList();

            for (CustomerOrder order : sortedOrders) {
                // x_LU y-LU x_RO y-RO - ID - Beschreibung
                writer.write(String.format("%d %d %d %d - %d - %s%n",
                        order.getXLU(), order.getYLU(), order.getXRO(), order.getYRO(),
                        order.id, order.description));
            }
            writer.newLine();
            writer.write("Verbleibende Andockpunkte:\n");
             // Sort docking points for consistent output
            List<Point> sortedPoints = result.finalDockingPoints().stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .toList();
            for (Point p : sortedPoints) {
                writer.write(String.format("%d %d%n", p.x(), p.y()));
            }
        }
    }

    private static void writeGnuplotScript(String scriptFilename, String pngFilename, InputData input, PlacementResult result) throws IOException {
        int rollWidth = input.rollWidth();
        int ymax = result.totalHeight(); // Use the calculated total height
        // Add some padding to ymax for better visualization unless it's 0
        int yRangeMax = (ymax == 0) ? 100 : (int) (ymax * 1.1); // e.g., 10% padding
         if (yRangeMax < 100) yRangeMax = 100; // Minimum range

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFilename))) {
            writer.write("reset\n");
            // Ensure ymax+100 doesn't cause issues if ymax is huge, use calculated range
            // Size calculation might need adjustment based on desired aspect ratio/clarity
            writer.write(String.format(Locale.US, "set term png size %d,%d\n", rollWidth, yRangeMax + 100)); // Adjust plot size
            writer.write(String.format("set output '%s'\n", pngFilename));
            writer.write(String.format(Locale.US, "set xrange [0:%d]\n", rollWidth));
            writer.write(String.format(Locale.US, "set yrange [0:%d]\n", yRangeMax));
            writer.write("set size ratio -1\n"); // Maintain aspect ratio (important!)
            writer.newLine();
            // Gnuplot title - use \n for newlines within the title string
            writer.write("set title \"\\\n"); // Start multi-line title
            writer.write(escapeGnuplotString(input.description()) + "\\n\\\n");
            writer.write(String.format(Locale.US, "Benötigte Länge: %.1fcm\\n\\\n", (double)result.totalHeight() / 10.0));
            writer.write(String.format(Locale.US, "Genutzte Fläche: %.2f%%\"\n", result.utilization()));
            writer.newLine();
            writer.write("set style fill transparent solid 0.5 border\n");
            writer.write("set key noautotitle\n"); // Correct option is 'set key noautotitle' or 'unset key'
            writer.newLine();

            // Data block for placed orders
            writer.write("$data <<EOD\n");
            // Sort orders by ID for consistent coloring (linecolor var)
            List<CustomerOrder> sortedOrders = result.placedOrders().stream()
                    .sorted(Comparator.comparingInt(o -> o.id))
                    .toList();
            for (CustomerOrder order : sortedOrders) {
                 // Format: x_LU y_LU x_RO y_RO "Description" ID
                 // Ensure description is properly quoted if it contains spaces
                writer.write(String.format(Locale.US, "%d %d %d %d \"%s\" %d\n",
                        order.getXLU(), order.getYLU(), order.getXRO(), order.getYRO(),
                        escapeGnuplotString(order.description), order.id));
            }
            writer.write("EOD\n");
            writer.newLine();

            // Data block for remaining docking points (visualized as red circles)
            writer.write("$anchor <<EOD\n");
             // Sort docking points for consistent output
            List<Point> sortedPoints = result.finalDockingPoints().stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .toList();
            for (Point p : sortedPoints) {
                writer.write(String.format(Locale.US, "%d %d\n", p.x(), p.y()));
            }
            writer.write("EOD\n");
            writer.newLine();

            // Plot commands
            writer.write("plot \\\n");
            // Rectangles with varying color based on order sequence/ID
             writer.write("'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):(($3-$1)/2):(($4-$2)/2):6 with boxxy linecolor var, \\\n");
           // Labels (using center of box) - Column 5 is description, 6 is ID
             writer.write("'$data' using (($3-$1)/2+$1):(($4-$2)/2+$2):5 with labels font \"arial,9\", \\\n");
             // Placed Anchor points (green dots)
            writer.write("'$anchor' using 1:2 with circles lc rgb \"red\", \\\n");
             // Free Docking points (red circles)
            writer.write("'$data' using 1:2 with points lw 8 lc rgb \"dark-green\"\n");
        }
    }

     // Helper to escape strings for gnuplot (e.g., backslashes)
    private static String escapeGnuplotString(String s) {
        if (s == null) return "";
        // Basic escaping, might need more depending on content
        return s.replace("\\", "").replace("\"", "\\\"").replace("\n", "\\n");
    }

     private static void runGnuplot(String scriptFilename) {
        try {
            // Ensure gnuplot is in the system's PATH
            ProcessBuilder pb = new ProcessBuilder("gnuplot", scriptFilename);
            pb.redirectErrorStream(true); // Combine stdout and stderr
            Process process = pb.start();
            // Capture output for debugging
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                System.out.println("--- gnuplot output ---");
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                System.out.println("----------------------");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Gnuplot executed successfully.");
            } else {
                System.err.println("Gnuplot execution failed with exit code: " + exitCode);
            }
        } catch (IOException e) {
            System.err.println("Failed to run gnuplot. Make sure it's installed and in the PATH.");
            System.err.println("Error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Gnuplot execution interrupted.");
        }
    }
}