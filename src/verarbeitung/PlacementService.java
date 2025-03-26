package verarbeitung;

import model.CustomerOrder;
import model.PlacementResult;
import model.Point;

import java.util.*;
import java.util.stream.Collectors;

public class PlacementService {

    private final int rollWidth;
    private PlacementResult bestBatchResult = null;

    public PlacementService(int rollWidth) {
        this.rollWidth = rollWidth;
    }

    // findOptimalPlacement remains the same as the previous version

    public PlacementResult findOptimalPlacement(List<CustomerOrder> allOrders, int optimizationDepth) {
        List<CustomerOrder> remainingOrders = new ArrayList<>(allOrders);
        List<CustomerOrder> globallyPlacedOrders = new ArrayList<>();
        int globalYOffset = 0; // The Y-coordinate where the current batch starts placing

        while (!remainingOrders.isEmpty()) {
            int ordersToOptimize = Math.min(remainingOrders.size(), optimizationDepth);
            List<CustomerOrder> batchToOptimize = new ArrayList<>(remainingOrders.subList(0, ordersToOptimize));
            List<CustomerOrder> nextRemainingOrders = new ArrayList<>(remainingOrders.subList(ordersToOptimize, remainingOrders.size()));

            // Determine the starting docking point(s) for this batch.
            // For this algorithm, it always restarts the search at X=0 on the new line.
            Set<Point> batchStartDockingPoints = new HashSet<>();
            batchStartDockingPoints.add(new Point(0, 0)); // Relative start for recursion

            System.out.printf("Optimizing batch of %d orders starting at global Y=%d.%n",
                    batchToOptimize.size(), globalYOffset);

            bestBatchResult = null; // Reset for this batch

            // Perform recursive search - operates relative to (0,0) for this batch
            recursivePlace(batchToOptimize, new ArrayList<>(), batchStartDockingPoints);

            if (bestBatchResult == null) {
                System.err.println("Warning: No valid placement found for the current batch. Stopping optimization.");
                remainingOrders.clear(); // Stop processing further batches
                continue;
            }

            System.out.printf("Batch finished. Best relative height: %d. Orders placed: %d%n",
                    bestBatchResult.totalHeight(), bestBatchResult.placedOrders().size());

            int batchRelativeMaxY = bestBatchResult.totalHeight();

            // Add successfully placed orders to the global list, adjusting Y-coordinates
            for (CustomerOrder batchPlacedOrder : bestBatchResult.placedOrders()) {
                batchPlacedOrder.placedY += globalYOffset; // Adjust Y by the offset
                globallyPlacedOrders.add(batchPlacedOrder);
            }

            // Update the global Y offset for the NEXT batch based on the highest point reached so far
            // Use orElse(globalYOffset) instead of orElse(globalYOffset + batchRelativeMaxY)
            // because if orders were placed, max() will exist; if not, offset shouldn't jump arbitrarily.
            globalYOffset = globallyPlacedOrders.stream()
                    .mapToInt(CustomerOrder::getYRO)
                    .max()
                    .orElse(globalYOffset); // Keep current offset if no orders placed globally yet


            remainingOrders = nextRemainingOrders; // Update remaining orders

        } // End while loop

        // --- Final Calculation ---
        int finalMaxY = globallyPlacedOrders.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        double totalOrderArea = globallyPlacedOrders.stream()
                .mapToDouble(o -> (double)o.currentWidth * o.currentHeight)
                .sum();
        double totalRollAreaUsed = (double)rollWidth * finalMaxY;
        double utilization = (totalRollAreaUsed > 0) ? (totalOrderArea / totalRollAreaUsed) * 100.0 : 0.0;

        // Calculate final docking points based on ALL placed orders
        Set<Point> finalAbsoluteDockingPoints = calculateDockingPoints(globallyPlacedOrders, rollWidth);

        // Cleanup unplaced orders
        Set<Integer> placedOrderIds = globallyPlacedOrders.stream().map(o -> o.id).collect(Collectors.toSet());
        for(CustomerOrder initialOrder : allOrders) {
            if(!placedOrderIds.contains(initialOrder.id)) {
                initialOrder.unsetPlacement();
            }
        }

        return new PlacementResult(globallyPlacedOrders, finalAbsoluteDockingPoints, finalMaxY, utilization);
    }

    // recursivePlace remains the same as the previous version

    private void recursivePlace(List<CustomerOrder> ordersToPlace,
                                List<CustomerOrder> currentlyPlaced,
                                Set<Point> availableDockingPoints
    ) {

        // Base Case: No more orders to place in this batch
        if (ordersToPlace.isEmpty()) {
            int currentRelativeMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
            if (bestBatchResult == null || currentRelativeMaxY < bestBatchResult.totalHeight()) {
                // System.out.printf("  Found new best batch solution. Relative Height: %d Orders: %d%n",
                //                 currentRelativeMaxY, currentlyPlaced.size());
                List<CustomerOrder> placedCopy = currentlyPlaced.stream()
                        .map(o -> {
                            CustomerOrder copy = new CustomerOrder(o.originalWidth, o.originalHeight, o.id, o.description);
                            copy.setPlacement(o.placedX, o.placedY, o.isRotated);
                            return copy;
                        })
                        .toList();
                bestBatchResult = new PlacementResult(placedCopy, new HashSet<>(availableDockingPoints), currentRelativeMaxY, 0);
            }
            return;
        }

        // Pruning (optional, can be aggressive)
        int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        if (bestBatchResult != null && intermediateMaxY >= bestBatchResult.totalHeight()) {
            return; // Prune
        }

        CustomerOrder orderToTry = ordersToPlace.getFirst();
        List<CustomerOrder> remainingForNextCall = ordersToPlace.subList(1, ordersToPlace.size());

        List<Point> sortedDockingPoints = availableDockingPoints.stream()
                .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                .collect(Collectors.toList());

        // boolean placedSomewhere = false; // Keep track if needed for skipping logic

        for (Point dockPoint : sortedDockingPoints) {
            for (boolean rotate : new boolean[]{false, true}) {
                int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;

                if (dockPoint.x() + width > rollWidth) continue; // Check width bounds

                orderToTry.setPlacement(dockPoint.x(), dockPoint.y(), rotate); // Tentative placement

                // Check overlap with already placed orders in this path
                boolean overlaps = false;
                for (CustomerOrder placed : currentlyPlaced) {
                    if (orderToTry.overlaps(placed)) {
                        overlaps = true;
                        break;
                    }
                }

                if (!overlaps) {
                    // Valid Placement Found
                    // placedSomewhere = true;
                    List<CustomerOrder> nextPlaced = new ArrayList<>(currentlyPlaced);
                    nextPlaced.add(orderToTry);

                    Set<Point> nextDockingPoints = new HashSet<>(availableDockingPoints);
                    nextDockingPoints.remove(dockPoint); // Consume used point

                    Point newTopLeft = new Point(orderToTry.getXLU(), orderToTry.getYRO());
                    Point newBottomRight = new Point(orderToTry.getXRO(), orderToTry.getYLU());

                    // Simple add - filtering happens later or via overlap checks
                    if (newTopLeft.x() <= rollWidth) nextDockingPoints.add(newTopLeft);
                    if (newBottomRight.x() <= rollWidth) nextDockingPoints.add(newBottomRight);

                    // Filter out docking points strictly inside the newly placed order? Optional optimization.
                    // Set<Point> filteredNextDockingPoints = nextDockingPoints.stream()
                    //    .filter(p -> !(p.x() >= orderToTry.getXLU() && p.x() < orderToTry.getXRO() &&
                    //                   p.y() >= orderToTry.getYLU() && p.y() < orderToTry.getYRO()))
                    //    .collect(Collectors.toSet());

                    // Make the recursive call
                    recursivePlace(remainingForNextCall, nextPlaced, nextDockingPoints); // Use nextDockingPoints or filteredNextDockingPoints
                }
                orderToTry.unsetPlacement(); // Backtrack placement for the specific order object

                if (orderToTry.originalWidth == orderToTry.originalHeight) break; // Opt: Skip rotation if square
            } // End rotation loop
        } // End docking point loop

        // --- Optional: Exploration without placing the current order ---
        // If you want to allow skipping orders within a batch:
        // recursivePlace(remainingForNextCall, currentlyPlaced, availableDockingPoints);
    }


    /**
     * Calculates the set of valid docking points based on the final global placement.
     * A point is valid if it's a potential corner (TL or BR) and is not
     * within the occupied area [xLU, xRO) x [yLU, yRO) of ANY placed order.
     *
     * @param globallyPlacedOrders List of all orders with their final absolute coordinates.
     * @param rollWidth            The width constraint of the roll.
     * @return A set of valid, unique docking points.
     */
    private static Set<Point> calculateDockingPoints(List<CustomerOrder> globallyPlacedOrders, int rollWidth) {
        Set<Point> potentialPoints = new HashSet<>();
        if (globallyPlacedOrders.isEmpty()) {
            // If nothing is placed, only (0,0) is available.
            potentialPoints.add(new Point(0, 0));
            return potentialPoints;
        }

        // 1. Generate potential points from ALL TL and BR corners
        for (CustomerOrder order : globallyPlacedOrders) {
            potentialPoints.add(new Point(order.getXLU(), order.getYRO())); // Top-left corner
            potentialPoints.add(new Point(order.getXRO(), order.getYLU())); // Bottom-right corner
        }
        // Also always consider (0,0) as a potential starting point if available
        potentialPoints.add(new Point(0,0));


        // 2. Filter potential points
        Set<Point> validPoints = new HashSet<>();
        for (Point p : potentialPoints) {
            // Basic bounds check (must be on or inside the roll width, Y must be non-negative)
            if (p.x() < 0 || p.x() > rollWidth || p.y() < 0) {
                continue;
            }

            // Check if the point 'p' is covered by the area of ANY placed order.
            // Area is defined as [xLU, xRO) x [yLU, yRO)
            boolean covered = false;
            for (CustomerOrder order : globallyPlacedOrders) {
                if (p.x() >= order.getXLU() && p.x() < order.getXRO() &&
                        p.y() >= order.getYLU() && p.y() < order.getYRO()) {
                    covered = true;
                    break; // Point is covered by this order, no need to check others
                }
            }

            // If the point was not covered by any order's area, it's a valid docking point.
            if (!covered) {
                validPoints.add(p);
            }
        }

        return validPoints;
    }

}