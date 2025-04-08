package verarbeitung;

import model.CustomerOrder;
import model.PlacementResult;
import model.Point;

import java.util.*;
import java.util.stream.Collectors;

public class PlacementService {

    private final int rollWidth;
    private PlacementResult bestBatchResult = null;
    // OPTIMIZATION: Counter for recursive calls to gauge complexity
    // Using long to avoid overflow on very deep/wide searches
    private long recursiveCallCounter = 0;

    public PlacementService(final int rollWidth) {
        this.rollWidth = rollWidth;
    }

    public PlacementResult findOptimalPlacement(final List<CustomerOrder> allOrders, final int optimizationDepth) {
        this.recursiveCallCounter = 0; // Reset counter for each new call

        List<CustomerOrder> remainingOrders = new ArrayList<>(allOrders);
        final List<CustomerOrder> globallyPlacedOrders = new ArrayList<>();
        int globalYOffset = 0; // The Y-coordinate where the current batch starts placing

        while (!remainingOrders.isEmpty()) {
            final int ordersToOptimize = Math.min(remainingOrders.size(), optimizationDepth);
            // Make copies for the batch processing
            final List<CustomerOrder> batchToOptimize = new ArrayList<>(remainingOrders.subList(0, ordersToOptimize));
            final List<CustomerOrder> nextRemainingOrders = new ArrayList<>(remainingOrders.subList(ordersToOptimize, remainingOrders.size()));

            final Set<Point> batchStartDockingPoints = Set.of(new Point(0, 0)); // Relative start for recursion

            System.out.printf("Optimizing batch of %d orders starting at global Y=%d.%n",
                    batchToOptimize.size(), globalYOffset);

            final long batchStartCounter = this.recursiveCallCounter;
            bestBatchResult = null; // Reset for this batch

            // Perform recursive search - operates relative to (0,0) for this batch
            recursivePlace(batchToOptimize, Collections.emptyList(), batchStartDockingPoints); // Start with empty immutable list

            final long batchCalls = this.recursiveCallCounter - batchStartCounter;
            System.out.printf("Batch recursion calls: %,d%n", batchCalls);


            if (bestBatchResult == null) {
                System.err.printf("Warning: No valid placement found for batch starting at Y=%d. Stopping optimization.%n", globalYOffset);
                // Stop processing further batches if one fails completely
                // Or, alternatively, just continue without adding anything from this batch:
                // remainingOrders = nextRemainingOrders; continue;
                remainingOrders.clear(); // Stop processing
                continue;
            }

            System.out.printf("Batch finished. Best relative height: %d. Orders placed: %d%n",
                    bestBatchResult.totalHeight(), bestBatchResult.placedOrders().size());

            // Add successfully placed orders to the global list, adjusting Y-coordinates
            for (final CustomerOrder batchPlacedOrder : bestBatchResult.placedOrders()) {
                batchPlacedOrder.placedY += globalYOffset; // Adjust Y by the offset
                globallyPlacedOrders.add(batchPlacedOrder);
            }

            // Update the global Y offset for the NEXT batch based on the highest point reached so far
            globalYOffset = globallyPlacedOrders.stream()
                    .mapToInt(CustomerOrder::getYRO)
                    .max()
                    .orElse(globalYOffset); // Keep current offset if no orders placed globally yet


            remainingOrders = nextRemainingOrders; // Update remaining orders

        } // End while loop

        System.out.printf("Total recursive calls: %,d%n", this.recursiveCallCounter);

        // --- Final Calculation ---
        final int finalMaxY = globallyPlacedOrders.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        final double totalOrderArea = globallyPlacedOrders.stream()
                .mapToDouble(o -> (double) o.currentWidth * o.currentHeight)
                .sum();
        final double totalRollAreaUsed = (double) this.rollWidth * finalMaxY;
        final double utilization = (totalRollAreaUsed > 0) ? (totalOrderArea / totalRollAreaUsed) * 100.0 : 0.0;

        // Calculate final docking points based on ALL placed orders
        final Set<Point> finalAbsoluteDockingPoints = calculateDockingPoints(globallyPlacedOrders, this.rollWidth);

        // Cleanup unplaced orders
        final Set<Integer> placedOrderIds = globallyPlacedOrders.stream().map(CustomerOrder::getId).collect(Collectors.toSet());
        for (final CustomerOrder initialOrder : allOrders) {
            if (!placedOrderIds.contains(initialOrder.id)) {
                initialOrder.unsetPlacement();
            }
        }

        return new PlacementResult(globallyPlacedOrders, finalAbsoluteDockingPoints, finalMaxY, utilization);
    }

    private void recursivePlace(final List<CustomerOrder> ordersToPlace,
                                final List<CustomerOrder> currentlyPlaced, // Note: This is conceptually immutable for the call
                                final Set<Point> availableDockingPoints     // Note: This is conceptually immutable for the call
    ) {
        this.recursiveCallCounter++;
        // Avoid excessive printing, adjust threshold as needed
        if (this.recursiveCallCounter > 0 && this.recursiveCallCounter % 5_000_000 == 0) {
            System.out.printf("...recursive calls: %,d (Current best height: %d)%n",
                    this.recursiveCallCounter, bestBatchResult != null ? bestBatchResult.totalHeight() : -1);
        }

        // --- Base Case: No more orders to place ---
        if (ordersToPlace.isEmpty()) {
            final int currentRelativeMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
            // Synchronized access not needed if single-threaded. If parallelizing later, this needs protection.
            if (bestBatchResult == null || currentRelativeMaxY < bestBatchResult.totalHeight()) {
                final List<CustomerOrder> placedCopy = currentlyPlaced.stream()
                        .map(o -> {
                            // Create a true copy for the result state
                            CustomerOrder copy = new CustomerOrder(o.originalWidth, o.originalHeight, o.id, o.description);
                            copy.setPlacement(o.placedX, o.placedY, o.isRotated);
                            return copy;
                        })
                        .toList(); // Immutable list (Java 16+)
                bestBatchResult = new PlacementResult(placedCopy, Set.copyOf(availableDockingPoints), currentRelativeMaxY, 0);
            }
            return;
        }

        // --- Pruning 1: Current intermediate height already >= best known ---
        // Only calculate if needed and if there's a best result to compare against
        if (bestBatchResult != null && !currentlyPlaced.isEmpty()) {
            final int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().getAsInt(); // Will exist if not empty
            if (intermediateMaxY >= bestBatchResult.totalHeight()) {
                return; // Prune this branch
            }
        }


        // --- Recursive Step ---

        if (currentlyPlaced.isEmpty()) {
            // --- SPECIAL INITIAL PLACEMENT: Try placing EACH order at (0,0) ---
            final Point initialDockPoint = new Point(0, 0);
            if (!availableDockingPoints.contains(initialDockPoint)) {
                System.err.println("Warning: Initial dock point (0,0) missing.");
                return; // Should not happen
            }

            for (int i = 0; i < ordersToPlace.size(); i++) {
                final CustomerOrder orderToTry = ordersToPlace.get(i);
                // Create the list of remaining orders efficiently
                final List<CustomerOrder> remainingForNextCall = new ArrayList<>(ordersToPlace.size() - 1);
                if (i > 0) remainingForNextCall.addAll(ordersToPlace.subList(0, i));
                if (i < ordersToPlace.size() - 1) remainingForNextCall.addAll(ordersToPlace.subList(i + 1, ordersToPlace.size()));

                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    // Check width bounds only
                    if (initialDockPoint.x() + width > this.rollWidth) continue;

                    // Pruning 4: If placing even this first item exceeds current best height
                    final int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;
                    if (bestBatchResult != null && initialDockPoint.y() + height >= bestBatchResult.totalHeight()) {
                        continue; // Cannot lead to a better solution
                    }

                    orderToTry.setPlacement(initialDockPoint.x(), initialDockPoint.y(), rotate);
                    final List<CustomerOrder> nextPlaced = List.of(orderToTry); // Start new placement list

                    // Create next docking points
                    final Set<Point> nextDockingPoints = new HashSet<>(2); // Initial capacity
                    final Point newTopLeft = new Point(orderToTry.getXLU(), orderToTry.getYRO());
                    final Point newBottomRight = new Point(orderToTry.getXRO(), orderToTry.getYLU());

                    // Only add valid points (within width, optionally check coverage early)
                    if (newTopLeft.x() <= this.rollWidth /* && !isPointCovered(newTopLeft, nextPlaced) */)
                        nextDockingPoints.add(newTopLeft);
                    if (newBottomRight.x() <= this.rollWidth /* && !isPointCovered(newBottomRight, nextPlaced) */)
                        nextDockingPoints.add(newBottomRight);

                    recursivePlace(remainingForNextCall, nextPlaced, nextDockingPoints);
                    orderToTry.unsetPlacement(); // Backtrack

                    if (orderToTry.originalWidth == orderToTry.originalHeight) break; // Opt: Skip rotation if square
                }
            }
        } else {
            // --- SUBSEQUENT PLACEMENTS ---
            final CustomerOrder orderToTry = ordersToPlace.getFirst(); // Get next order per original logic
            final List<CustomerOrder> remainingForNextCall = ordersToPlace.subList(1, ordersToPlace.size());

            // Sort points once for predictable iteration order (bottom-left heuristic)
            final List<Point> sortedDockingPoints = availableDockingPoints.stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .toList(); // Immutable list

            for (final Point dockPoint : sortedDockingPoints) {

                // --- Pruning 2: Docking point Y coordinate itself is already too high ---
                if (bestBatchResult != null && dockPoint.y() >= bestBatchResult.totalHeight()) {
                    break; // Since points are sorted by Y, no further point can be better
                }

                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    final int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    // --- Pruning 3: Docking point + order's MINIMUM dimension is too high ---
                    // Uses original dimensions for a safe lower bound check
                    final int minDim = Math.min(orderToTry.originalWidth, orderToTry.originalHeight);
                    if (bestBatchResult != null && dockPoint.y() + minDim >= bestBatchResult.totalHeight()) {
                        // If placing even the smallest dimension *must* meet or exceed the best height,
                        // trying this dock point (or specifically this rotation) is pointless.
                        // If we 'continue', we check the other rotation. If we 'break' (inner loop), we go to next dock point.
                        // Let's 'continue' to allow the other rotation if it might be shorter.
                        continue;
                    }

                    // Check width bounds
                    if (dockPoint.x() + width > this.rollWidth) continue;

                    // Check height bounds explicitly *before* overlap check (might be faster)
                    if (bestBatchResult != null && dockPoint.y() + height >= bestBatchResult.totalHeight()) {
                        continue; // Cannot lead to a better solution
                    }

                    // Tentative placement before overlap check
                    orderToTry.setPlacement(dockPoint.x(), dockPoint.y(), rotate);

                    // Check overlap (can be expensive if currentlyPlaced is large)
                    boolean overlaps = false;
                    for (final CustomerOrder placed : currentlyPlaced) {
                        if (orderToTry.overlaps(placed)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        // Build next state (use ArrayList for efficient modification)
                        final List<CustomerOrder> nextPlaced = new ArrayList<>(currentlyPlaced.size() + 1);
                        nextPlaced.addAll(currentlyPlaced);
                        nextPlaced.add(orderToTry);

                        final Set<Point> nextDockingPoints = new HashSet<>(availableDockingPoints); // Copy
                        nextDockingPoints.remove(dockPoint); // Consume

                        final Point newTopLeft = new Point(orderToTry.getXLU(), orderToTry.getYRO());
                        final Point newBottomRight = new Point(orderToTry.getXRO(), orderToTry.getYLU());

                        // Add new points if valid and potentially not covered
                        // Pass `nextPlaced` which includes the `orderToTry` for coverage check
                        if (newTopLeft.x() <= this.rollWidth && !isPointCovered(newTopLeft, nextPlaced)) {
                            nextDockingPoints.add(newTopLeft);
                        }
                        if (newBottomRight.x() <= this.rollWidth && !isPointCovered(newBottomRight, nextPlaced)) {
                            nextDockingPoints.add(newBottomRight);
                        }

                        recursivePlace(remainingForNextCall, nextPlaced, nextDockingPoints);
                    }
                    // Backtrack mandatory *after* exploring this placement attempt
                    orderToTry.unsetPlacement();

                    // Opt: Skip rotation if square
                    if (orderToTry.originalWidth == orderToTry.originalHeight) break;
                } // End rotation loop
            } // End docking point loop
        } // End else (subsequent placements)
    } // End recursivePlace

    /** Optimized isPointCovered Check (minor change for clarity/safety) */
    private boolean isPointCovered(final Point p, final List<CustomerOrder> orders) {
        for (final CustomerOrder order : orders) {
            // Ensure order has valid placement data before checking
            if (!order.isPlaced) continue; // Safety check

            // Standard check: [xLU, xRO) x [yLU, yRO)
            if (p.x() >= order.getXLU() && p.x() < order.getXRO() &&
                    p.y() >= order.getYLU() && p.y() < order.getYRO()) {
                return true;
            }
        }
        return false;
    }


    /** calculateDockingPoints (no changes from previous correct version) */
    private static Set<Point> calculateDockingPoints(final List<CustomerOrder> globallyPlacedOrders, final int rollWidth) {
        // If empty, only (0,0) is possible
        if (globallyPlacedOrders.isEmpty()) {
            return Set.of(new Point(0, 0)); // Use immutable Set.of
        }

        // Use HashSet for efficient building
        final Set<Point> potentialPoints = new HashSet<>();
        for (final CustomerOrder order : globallyPlacedOrders) {
            potentialPoints.add(new Point(order.getXLU(), order.getYRO())); // Top-left
            potentialPoints.add(new Point(order.getXRO(), order.getYLU())); // Bottom-right
        }
        potentialPoints.add(new Point(0,0)); // Always consider origin

        // Filter points efficiently
        final Set<Point> validPoints = new HashSet<>();
        for (final Point p : potentialPoints) {
            if (p.x() < 0 || p.x() > rollWidth || p.y() < 0) continue;

            boolean covered = false;
            for (final CustomerOrder order : globallyPlacedOrders) {
                // Check coverage using the standard area definition
                if (p.x() >= order.getXLU() && p.x() < order.getXRO() &&
                        p.y() >= order.getYLU() && p.y() < order.getYRO()) {
                    covered = true;
                    break; // Found covering order
                }
            }
            if (!covered) {
                validPoints.add(p);
            }
        }
        // Return potentially mutable HashSet, or Set.copyOf(validPoints) for immutable
        return validPoints;
        // return Set.copyOf(validPoints); // Java 10+ for immutable result
    }
}