package io;

import model.CustomerOrder;
import model.InputData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InputReader {

    public static InputData readInput(String filename) throws IOException {
        List<CustomerOrder> orders = new ArrayList<>();
        String description = "";
        int rollWidth = 0;
        int optimizationDepth = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            description = reader.readLine(); // First line is description
            String[] config = reader.readLine().trim().split("\\s+");
            rollWidth = Integer.parseInt(config[0]);
            optimizationDepth = Integer.parseInt(config[1]);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) { // Allow comments/empty lines
                    continue;
                }
                // Tolerate different separators and spacing
                String[] parts = line.split("\\s*,\\s*|\\s+"); // Split by comma or space, trimming whitespace
                 if (parts.length >= 4) {
                    try {
                        int width = Integer.parseInt(parts[0]);
                        int height = Integer.parseInt(parts[1]);
                        int id = Integer.parseInt(parts[2]);
                        // Reconstruct description if it contained spaces/commas
                        StringBuilder descBuilder = new StringBuilder();
                        for (int i = 3; i < parts.length; i++) {
                            descBuilder.append(parts[i]).append(" ");
                        }
                        String desc = descBuilder.toString().trim();

                        orders.add(new CustomerOrder(width, height, id, desc));
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping invalid line: " + line + " - " + e.getMessage());
                    }
                 } else {
                     System.err.println("Skipping invalid line (not enough parts): " + line);
                 }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid number format in config or order line.", e);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("Invalid format in config line (expected width and depth).", e);
        }

        return new InputData(description, rollWidth, optimizationDepth, orders);
    }
}