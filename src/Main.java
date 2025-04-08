import io.InputReader;
import io.OutputWriter;
import model.InputData;
import model.PlacementResult;
import model.Point;
import verarbeitung.PlacementService;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Set;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <input_filename_base>");
            System.err.println("Example: java Main Beispiel1");
            System.err.println("(Assumes input file is <input_filename_base>.in)");
            return;
        }

        String baseFilename = args[0];
        String inputFilename = baseFilename + ".in";

        // --- Eingabe ---
        InputData inputData;
        try {
            System.out.println("Reading input from: " + inputFilename);
            inputData = InputReader.readInput(inputFilename);
            System.out.println("Input read successfully:");
            System.out.println("  Roll Width: " + inputData.rollWidth());
            System.out.println("  Optimization Depth: " + inputData.optimizationDepth());
            System.out.println("  Number of Orders: " + inputData.orders().size());
        } catch (IOException e) {
            System.err.println("Error reading input file: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // --- Verarbeitung ---
        System.out.println("\nStarting placement optimization...");
        PlacementService placementService = new PlacementService(inputData.rollWidth(), inputData.optimizationDepth(),false);

        // Implement Timer for performance
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        if (!threadMXBean.isCurrentThreadCpuTimeSupported()) {
            System.out.println("CPU time measurement not supported.");
        } else {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }

        long startTime = System.nanoTime();
        long startCpuTime = threadMXBean.getCurrentThreadCpuTime();

        PlacementResult result = placementService.findOptimalPlacementParallelBatches(inputData.orders());

        long endTime = System.nanoTime();
        long endCpuTime = threadMXBean.getCurrentThreadCpuTime();

        long elapsedTime = (endTime - startTime); // in nanoseconds
        double elapsedTimeInSeconds = (double) elapsedTime / 1_000_000_000.0;

        long cpuTime = (endCpuTime - startCpuTime);
        double cpuTimeInSeconds = (double) cpuTime / 1_000_000_000.0;

        System.out.println(
                "Optimization finished in: " + elapsedTimeInSeconds + " seconds (" + elapsedTime + " nanoseconds)");
        System.out.println(
                "CPU time used: " + cpuTimeInSeconds + " seconds (" + cpuTime + " nanoseconds)");

        System.out.println("Optimization finished.");

        if (result == null) {
            System.err.println("No placement solution found.");
            // Create an empty result to avoid null pointer errors in output
            result = new PlacementResult(List.of(), Set.of(new Point(0,0)), 0, 0.0);
        }

        // --- Ausgabe ---
        System.out.println("\nWriting output files...");
        try {
            OutputWriter.writeOutput(baseFilename, inputData, result);
            System.out.println("Output files generated: " + baseFilename + ".out, " + baseFilename + ".gnu, " + baseFilename + ".png");
        } catch (IOException e) {
            System.err.println("Error writing output files: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\nProcess completed.");
    }
}