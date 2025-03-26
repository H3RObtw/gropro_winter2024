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

    /**
     * Recursively tries to place orders, minimizing the maximum Y coordinate for the batch.
     * Operates with coordinates relative to the batch's own origin (0,0).
     * SPECIAL HANDLING: When currentlyPlaced is empty, it tries *each* order at (0,0) first.
     *
     * @param ordersToPlace      Orders remaining to be placed in this batch.
     * @param currentlyPlaced    Orders already placed in the current recursive path (relative coordinates).
     * @param availableDockingPoints Docking points available for the next placement (relative coordinates).
     */
    private void recursivePlace(List<CustomerOrder> ordersToPlace,
                                List<CustomerOrder> currentlyPlaced,
                                Set<Point> availableDockingPoints
    ) {

        // --- Base Case: No more orders to place in this batch ---
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

        // --- Pruning (optional) ---
        int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        if (bestBatchResult != null && intermediateMaxY >= bestBatchResult.totalHeight()) {
            return; // Prune
        }


        // --- Recursive Step ---

        if (currentlyPlaced.isEmpty()) {
            // --- SPECIAL INITIAL PLACEMENT: Try placing EACH order at (0,0) ---
            Point initialDockPoint = new Point(0, 0); // The only relevant point at the start
            if (!availableDockingPoints.contains(initialDockPoint)) {
                // Should not happen if called correctly, but safety check
                System.err.println("Warning: Initial dock point (0,0) missing in recursive call.");
                return;
            }

            for (int i = 0; i < ordersToPlace.size(); i++) {
                CustomerOrder orderToTry = ordersToPlace.get(i);

                // Create list of remaining orders for the recursive call *if* this one is placed
                List<CustomerOrder> remainingForNextCall = new ArrayList<>(ordersToPlace.size() - 1);
                for (int j = 0; j < ordersToPlace.size(); j++) {
                    if (i != j) {
                        remainingForNextCall.add(ordersToPlace.get(j));
                    }
                }

                // Try both orientations at (0,0)
                for (boolean rotate : new boolean[]{false, true}) {
                    int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    // Check only width bounds (overlap not possible yet)
                    if (initialDockPoint.x() + width > rollWidth) {
                        continue;
                    }

                    // Tentatively place the order at (0,0)
                    orderToTry.setPlacement(initialDockPoint.x(), initialDockPoint.y(), rotate);

                    // --- Valid Initial Placement ---
                    List<CustomerOrder> nextPlaced = Collections.singletonList(orderToTry); // List with just this order

                    // Calculate next docking points (TL and BR corners)
                    Set<Point> nextDockingPoints = new HashSet<>();
                    Point newTopLeft = new Point(orderToTry.getXLU(), orderToTry.getYRO());
                    Point newBottomRight = new Point(orderToTry.getXRO(), orderToTry.getYLU());
                    if (newTopLeft.x() <= rollWidth) nextDockingPoints.add(newTopLeft);
                    if (newBottomRight.x() <= rollWidth) nextDockingPoints.add(newBottomRight);

                    // --- Make the recursive call ---
                    recursivePlace(remainingForNextCall, nextPlaced, nextDockingPoints);

                    // --- Backtrack ---
                    orderToTry.unsetPlacement();

                    // Optimization: If square, don't re-try rotation
                    if (orderToTry.originalWidth == orderToTry.originalHeight) break;
                } // End rotation loop
            } // End loop trying each order first
        }
        else {
            // --- SUBSEQUENT PLACEMENTS: Place the *next* order at available points ---
            CustomerOrder orderToTry = ordersToPlace.getFirst();
            List<CustomerOrder> remainingForNextCall = ordersToPlace.subList(1, ordersToPlace.size());

            // Sort available points (bottom-left heuristic)
            List<Point> sortedDockingPoints = availableDockingPoints.stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .collect(Collectors.toList());

            for (Point dockPoint : sortedDockingPoints) {
                for (boolean rotate : new boolean[]{false, true}) {
                    int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    if (dockPoint.x() + width > rollWidth) continue; // Check width bounds

                    orderToTry.setPlacement(dockPoint.x(), dockPoint.y(), rotate); // Tentative placement

                    // Check overlap
                    boolean overlaps = false;
                    for (CustomerOrder placed : currentlyPlaced) {
                        if (orderToTry.overlaps(placed)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        // Valid Placement Found
                        List<CustomerOrder> nextPlaced = new ArrayList<>(currentlyPlaced);
                        nextPlaced.add(orderToTry);

                        Set<Point> nextDockingPoints = new HashSet<>(availableDockingPoints);
                        nextDockingPoints.remove(dockPoint);

                        Point newTopLeft = new Point(orderToTry.getXLU(), orderToTry.getYRO());
                        Point newBottomRight = new Point(orderToTry.getXRO(), orderToTry.getYLU());
                        if (newTopLeft.x() <= rollWidth) nextDockingPoints.add(newTopLeft);
                        if (newBottomRight.x() <= rollWidth) nextDockingPoints.add(newBottomRight);

                        // Optional filtering:
                        // Set<Point> filteredNextDockingPoints = nextDockingPoints.stream() ... filter ...

                        // Make the recursive call
                        recursivePlace(remainingForNextCall, nextPlaced, nextDockingPoints); // or filteredNextDockingPoints
                    }
                    orderToTry.unsetPlacement(); // Backtrack

                    if (orderToTry.originalWidth == orderToTry.originalHeight) break; // Opt: Skip rotation if square
                } // End rotation loop
            } // End docking point loop
        } // End else (subsequent placements)

        // --- Optional: Exploration without placing the current order ---
        // Needs careful integration with the initial placement logic if used.
        // If currentlyPlaced.isEmpty(), this skip logic needs to apply *after* trying all initial orders.
        // If not currentlyPlaced.isEmpty(), it applies after trying to place orderToTry at all points.
         /*
         if (!currentlyPlaced.isEmpty()) { // Apply skip only after trying subsequent placements
             // Consider the path where orderToTry is *not* placed
             recursivePlace(remainingForNextCall, currentlyPlaced, availableDockingPoints);
         } else {
             // If it's the initial placement phase, and *none* of the orders could be placed at (0,0)
             // maybe trigger skip logic here? More complex. Let's omit for now.
         }
         */
    } // End recursivePlace


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