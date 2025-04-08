package verarbeitung;

import model.CustomerOrder;
import model.PlacementResult;
import model.Point;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class PlacementService implements AutoCloseable {

    private final int rollWidth;
    private final AtomicReference<PlacementResult> bestBatchResultRef = new AtomicReference<>(null);
    private final AtomicLong recursiveCallCounter = new AtomicLong(0);
    private final ExecutorService executor;
    private final boolean manageExecutorLifecycle;
    // OPTIMIZATION: Counter threshold for progress printing
    private static final long PRINT_PROGRESS_INTERVAL = 50_000_000; // Adjust as needed

    public PlacementService(final int rollWidth) {
        this(rollWidth, Executors.newWorkStealingPool(), true);
    }

    public PlacementService(final int rollWidth, ExecutorService executorService, boolean manageLifecycle) {
        if (rollWidth <= 0) {
            throw new IllegalArgumentException("Roll width must be positive.");
        }
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null.");
        }
        this.rollWidth = rollWidth;
        this.executor = executorService;
        this.manageExecutorLifecycle = manageLifecycle;
        try {
            CustomerOrder.class.getMethod("copy");
            CustomerOrder.class.getMethod("updateDerivedPlacementFields");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("CustomerOrder class must have public copy() and updateDerivedPlacementFields() methods.", e);
        }
    }

    @Override
    public void close() {
        // ... (shutdown logic remains the same)
        if (manageExecutorLifecycle) {
            System.out.println("Shutting down PlacementService ExecutorService...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate in the specified time.");
                    List<Runnable> droppedTasks = executor.shutdownNow();
                    System.err.println("Executor was shut down forcefully. " + droppedTasks.size() + " tasks were dropped.");
                }
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting for executor termination.");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("ExecutorService shut down.");
        } else {
            System.out.println("PlacementService closed (external executor not shut down).");
        }
    }

    public PlacementResult findOptimalPlacement(final List<CustomerOrder> allOrders, final int optimizationDepth) {
        this.recursiveCallCounter.set(0);
        allOrders.forEach(CustomerOrder::unsetPlacement);

        List<CustomerOrder> remainingOrders = new ArrayList<>(allOrders);
        final List<CustomerOrder> globallyPlacedOrders = new ArrayList<>();
        int globalYOffset = 0;

        while (!remainingOrders.isEmpty()) {
            final int ordersToOptimize = Math.min(remainingOrders.size(), optimizationDepth);
            final List<CustomerOrder> batchToOptimizeInput = remainingOrders.subList(0, ordersToOptimize);
            final List<CustomerOrder> nextRemainingOrders = new ArrayList<>(remainingOrders.subList(ordersToOptimize, remainingOrders.size()));

            // --- Create COPIES for the batch ---
            List<CustomerOrder> batchToOptimize = batchToOptimizeInput
                    .stream()
                    .map(CustomerOrder::copy).sorted(Comparator.comparingDouble((CustomerOrder o) ->
                            (double) o.originalWidth * o.originalHeight).reversed()).collect(Collectors.toList());

            // --- OPTIMIZATION 1: Sort batch by area descending ---
            // Place larger items first (heuristic)
            // --- End Optimization 1 ---

            final Set<Point> batchStartDockingPoints = Set.of(new Point(0, 0));

            System.out.printf("Optimizing batch of %d orders (sorted by area desc) starting at global Y=%d.%n",
                    batchToOptimize.size(), globalYOffset);

            final long batchStartCounterValue = this.recursiveCallCounter.get();
            bestBatchResultRef.set(null); // Reset best result for this batch

            // Launch the top-level recursive call for the batch
            recursivePlace(batchToOptimize, Collections.emptyList(), batchStartDockingPoints);

            final long batchCalls = this.recursiveCallCounter.get() - batchStartCounterValue;
            System.out.printf("Batch recursion calls: %,d%n", batchCalls);

            PlacementResult currentBestBatch = bestBatchResultRef.get();

            if (currentBestBatch == null) {
                System.err.printf("Warning: No valid placement found for batch (sorted) starting at Y=%d. Adding originals back and stopping.%n", globalYOffset);
                // Add the orders we tried to optimize back to the remaining list
                // If we stop, they become unplaced. If we continue, they'll be tried in the next batch.
                remainingOrders = new ArrayList<>(batchToOptimizeInput); // Use originals
                remainingOrders.addAll(nextRemainingOrders);
                // Decide whether to stop entirely or just skip placing this batch
                break; // Stop optimization entirely if one batch fails
                // continue; // Or uncomment this to try the next batch (after adding back)
            }

            System.out.printf("Batch finished. Best relative height: %d. Orders placed: %d%n",
                    currentBestBatch.totalHeight(), currentBestBatch.placedOrders().size());

            // Add copies of successfully placed orders from the batch result to the global list
            Set<Integer> placedInBatchIds = new HashSet<>();
            for (final CustomerOrder batchPlacedOrder : currentBestBatch.placedOrders()) {
                CustomerOrder globalOrder = batchPlacedOrder.copy(); // Copy from result
                globalOrder.placedY += globalYOffset; // Adjust Y
                globalOrder.updateDerivedPlacementFields(); // Update derived coords
                globallyPlacedOrders.add(globalOrder);
                placedInBatchIds.add(globalOrder.getId());
            }

            // Update global Y offset
            globalYOffset = globallyPlacedOrders.stream()
                    .mapToInt(CustomerOrder::getYRO)
                    .max()
                    .orElse(globalYOffset);

            // Prepare the list of remaining orders for the next iteration
            // Start with orders skipped in this batch optimization (use originals based on ID)
            List<CustomerOrder> ordersNotPlacedInBatch = batchToOptimizeInput.stream()
                    .filter(o -> !placedInBatchIds.contains(o.getId()))
                    .toList();

            remainingOrders = new ArrayList<>(ordersNotPlacedInBatch);
            remainingOrders.addAll(nextRemainingOrders);


        } // End while loop

        System.out.printf("Total recursive calls: %,d%n", this.recursiveCallCounter.get());

        // --- Final Calculation & Cleanup --- (remains the same)
        final int finalMaxY = globallyPlacedOrders.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        final double totalOrderArea = globallyPlacedOrders.stream()
                .mapToDouble(o -> (double) o.currentWidth * o.currentHeight)
                .sum();
        final double totalRollAreaUsed = (double) this.rollWidth * finalMaxY;
        final double utilization = (totalRollAreaUsed > 0) ? (totalOrderArea / totalRollAreaUsed) * 100.0 : 0.0;

        final Set<Point> finalAbsoluteDockingPoints = calculateDockingPoints(globallyPlacedOrders, this.rollWidth);

        final Set<Integer> finalPlacedOrderIds = globallyPlacedOrders.stream().map(CustomerOrder::getId).collect(Collectors.toSet());
        for (final CustomerOrder initialOrder : allOrders) {
            if (!finalPlacedOrderIds.contains(initialOrder.id)) {
                initialOrder.unsetPlacement();
            } else {
                globallyPlacedOrders.stream()
                        .filter(p -> p.getId() == initialOrder.getId())
                        .findFirst()
                        .ifPresent(placedOrder -> initialOrder.setPlacement(placedOrder.placedX, placedOrder.placedY, placedOrder.isRotated));
            }
        }

        return new PlacementResult(List.copyOf(globallyPlacedOrders),
                finalAbsoluteDockingPoints, finalMaxY, utilization);
    }

    private void recursivePlace(final List<CustomerOrder> ordersToPlace,   // Immutable List<OrderCopy>
                                final List<CustomerOrder> currentlyPlaced, // Immutable List<OrderCopy>
                                final Set<Point> availableDockingPoints   // Immutable Set<Point>
    ) {
        long currentCallCount = this.recursiveCallCounter.incrementAndGet();
        PlacementResult currentBest = bestBatchResultRef.get(); // Read once per invocation (or when needed)

        // Progress Printing (less frequent potentially)
        // Check against a running total, not just modulo, for less frequent batch prints
        // This part seems less critical now, focus on pruning logic.
        if (currentCallCount > 0 && currentCallCount % PRINT_PROGRESS_INTERVAL == 0) {
            System.out.printf("...recursive calls: %,d (Current best height: %d)%n",
                    currentCallCount, currentBest != null ? currentBest.totalHeight() : -1);
        }

        // --- Base Case ---
        if (ordersToPlace.isEmpty()) {
            final int currentRelativeMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
            PlacementResult potentialResult = new PlacementResult(
                    currentlyPlaced.stream().map(CustomerOrder::copy).toList(), // Result needs own copies
                    Set.copyOf(availableDockingPoints),
                    currentRelativeMaxY,
                    0.0
            );
            bestBatchResultRef.accumulateAndGet(potentialResult,
                    (existingBest, potentialNew) ->
                            (existingBest == null || potentialNew.totalHeight() < existingBest.totalHeight())
                                    ? potentialNew : existingBest
            );
            return;
        }

        // --- Pruning 1: Intermediate Height ---
        if (currentBest != null && !currentlyPlaced.isEmpty()) {
            final int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().getAsInt();
            if (intermediateMaxY >= currentBest.totalHeight()) {
                return; // Prune this branch
            }
        }

        // --- Recursive Step ---

        if (currentlyPlaced.isEmpty()) {
            // --- INITIAL PLACEMENT (Parallel) ---
            // (Logic remains largely the same, still parallelizing the first choice)
            final Point initialDockPoint = new Point(0, 0);
            if (!availableDockingPoints.contains(initialDockPoint)) {
                System.err.println("Error: Initial dock point (0,0) missing."); return;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < ordersToPlace.size(); i++) { // Still iterate through all possibilities for the *first* item
                final CustomerOrder orderToConsider = ordersToPlace.get(i);

                final List<CustomerOrder> remainingForNextCall = new ArrayList<>(ordersToPlace.size() - 1);
                if (i > 0) remainingForNextCall.addAll(ordersToPlace.subList(0, i));
                if (i < ordersToPlace.size() - 1) remainingForNextCall.addAll(ordersToPlace.subList(i + 1, ordersToPlace.size()));
                final List<CustomerOrder> immutableRemaining = List.copyOf(remainingForNextCall); // Pass immutable list

                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToConsider.originalHeight : orderToConsider.originalWidth;
                    final int height = rotate ? orderToConsider.originalWidth : orderToConsider.originalHeight;

                    if (initialDockPoint.x() + width > this.rollWidth) continue;

                    PlacementResult currentBestForPruning = bestBatchResultRef.get(); // Read fresh best value
                    if (currentBestForPruning != null && initialDockPoint.y() + height >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    final CustomerOrder placedOrderCopy = orderToConsider.copy();
                    placedOrderCopy.setPlacement(initialDockPoint.x(), initialDockPoint.y(), rotate);
                    final List<CustomerOrder> nextPlaced = List.of(placedOrderCopy); // Immutable list

                    final Set<Point> nextDockingPoints = new HashSet<>(2);
                    final Point newTopLeft = new Point(placedOrderCopy.getXLU(), placedOrderCopy.getYRO());
                    final Point newBottomRight = new Point(placedOrderCopy.getXRO(), placedOrderCopy.getYLU());

                    if (newTopLeft.x() <= this.rollWidth && !isPointCovered(newTopLeft, nextPlaced)) nextDockingPoints.add(newTopLeft);
                    if (newBottomRight.x() <= this.rollWidth && !isPointCovered(newBottomRight, nextPlaced)) nextDockingPoints.add(newBottomRight);
                    final Set<Point> immutableNextDocking = Set.copyOf(nextDockingPoints);

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        recursivePlace(immutableRemaining, nextPlaced, immutableNextDocking);
                    }, executor);
                    futures.add(future);

                    if (orderToConsider.originalWidth == orderToConsider.originalHeight) break; // Opt: Skip rotation if square
                }
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } else {
            // --- SUBSEQUENT PLACEMENTS (Sequential within this branch) ---
            final CustomerOrder orderToTry = ordersToPlace.getFirst(); // Get next order (because list was sorted)
            final List<CustomerOrder> remainingForNextCall = ordersToPlace.subList(1, ordersToPlace.size()); // Immutable view

            // --- OPTIMIZATION 2a: Calculate min height of any remaining order ONCE ---
            final int minRemainingHeight = ordersToPlace.stream() // Includes orderToTry and others
                    .mapToInt(o -> Math.min(o.originalWidth, o.originalHeight))
                    .min()
                    .orElse(Integer.MAX_VALUE); // Use MAX_VALUE if list is somehow empty
            // --- End Optimization 2a ---


            final List<Point> sortedDockingPoints = availableDockingPoints.stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .toList();

            for (final Point dockPoint : sortedDockingPoints) {
                PlacementResult currentBestForPruning = bestBatchResultRef.get(); // Re-read best result

                // --- Pruning 2: Docking point Y coord itself too high ---
                if (currentBestForPruning != null && dockPoint.y() >= currentBestForPruning.totalHeight()) {
                    break; // Points sorted by Y, no further point can be better
                }

                // --- OPTIMIZATION 2b: Stronger Docking Point Pruning ---
                // If placing ANY remaining item (even the shortest) at this dock point
                // would exceed the best height, then skip this dock point entirely.
                if (currentBestForPruning != null && minRemainingHeight != Integer.MAX_VALUE &&
                        dockPoint.y() + minRemainingHeight >= currentBestForPruning.totalHeight()) {
                    // Because points are sorted by Y, all subsequent points will also fail this check.
                    break;
                }
                // --- End Optimization 2b ---


                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    final int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    // Pruning 3 (Lower bound check specific to *this* order/rotation)
                    // Note: This is slightly redundant now with Opt 2b, but still useful
                    // as the specific item might be taller than minRemainingHeight.
                    final int currentItemMinDim = Math.min(width, height); // Use dimensions for *this* rotation
                    currentBestForPruning = bestBatchResultRef.get(); // Re-read needed? Maybe not critical here.
                    if (currentBestForPruning != null && dockPoint.y() + currentItemMinDim >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    // Check width bounds
                    if (dockPoint.x() + width > this.rollWidth) continue;

                    // Check height bounds (specific to this order/rotation)
                    currentBestForPruning = bestBatchResultRef.get(); // Re-read just in case
                    if (currentBestForPruning != null && dockPoint.y() + height >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    // --- Create Candidate Copy ---
                    final CustomerOrder candidateOrder = orderToTry.copy();
                    candidateOrder.setPlacement(dockPoint.x(), dockPoint.y(), rotate);

                    // --- Check Overlap ---
                    boolean overlaps = false;
                    for (final CustomerOrder placed : currentlyPlaced) {
                        if (candidateOrder.overlaps(placed)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
                        // --- Prepare state for recursive call (using immutable copies) ---
                        List<CustomerOrder> nextPlaced = new ArrayList<>(currentlyPlaced.size() + 1);
                        nextPlaced.addAll(currentlyPlaced);
                        nextPlaced.add(candidateOrder);
                        List<CustomerOrder> immutableNextPlaced = List.copyOf(nextPlaced);

                        Set<Point> nextDockingPoints = new HashSet<>(availableDockingPoints);
                        nextDockingPoints.remove(dockPoint);

                        Point newTopLeft = new Point(candidateOrder.getXLU(), candidateOrder.getYRO());
                        Point newBottomRight = new Point(candidateOrder.getXRO(), candidateOrder.getYLU());

                        if (newTopLeft.x() <= this.rollWidth && !isPointCovered(newTopLeft, immutableNextPlaced)) {
                            nextDockingPoints.add(newTopLeft);
                        }
                        if (newBottomRight.x() <= this.rollWidth && !isPointCovered(newBottomRight, immutableNextPlaced)) {
                            nextDockingPoints.add(newBottomRight);
                        }
                        Set<Point> immutableNextDocking = Set.copyOf(nextDockingPoints);

                        // --- Make the recursive call ---
                        recursivePlace(remainingForNextCall, immutableNextPlaced, immutableNextDocking);
                    }

                    // Opt: Skip rotation if square
                    if (orderToTry.originalWidth == orderToTry.originalHeight) break;

                } // End rotation loop
            } // End docking point loop
        } // End else (subsequent placements)
    } // End recursivePlace


    // --- Static Helpers (isPointCovered, calculateDockingPoints) remain the same ---
    /** Static helper, checks coverage against a list of placed orders */
    private static boolean isPointCovered(final Point p, final List<CustomerOrder> orders) {
        // ... (no changes needed)
        for (final CustomerOrder order : orders) {
            if (p.x() >= order.getXLU() && p.x() < order.getXRO() &&
                    p.y() >= order.getYLU() && p.y() < order.getYRO()) {
                return true;
            }
        }
        return false;
    }

    /** Static helper, calculates valid docking points */
    private static Set<Point> calculateDockingPoints(final List<CustomerOrder> placedOrders, final int rollWidth) {
        // ... (no changes needed, the existing logic seems okay)
        if (placedOrders.isEmpty()) {
            return Set.of(new Point(0, 0));
        }

        final Set<Point> potentialPoints = new HashSet<>();
        potentialPoints.add(new Point(0,0));

        for (final CustomerOrder order : placedOrders) {
            if (order.isPlaced) { // Ensure it's actually placed
                potentialPoints.add(new Point(order.getXLU(), order.getYRO()));
                potentialPoints.add(new Point(order.getXRO(), order.getYLU()));
            }
        }

        final Set<Point> validPoints = new HashSet<>();
        for (final Point p : potentialPoints) {
            if (p.x() < 0 || p.x() >= rollWidth || p.y() < 0) continue; // X must be strictly < rollWidth to start placement

            if (!isPointCovered(p, placedOrders)) {
                validPoints.add(p);
            }
        }

        if (validPoints.isEmpty() && !placedOrders.isEmpty()) {
            Point origin = new Point(0,0);
            if (origin.x() < rollWidth && !isPointCovered(origin, placedOrders)) {
                validPoints.add(origin);
            } else {
                int maxY = placedOrders.stream().filter(o -> o.isPlaced).mapToInt(CustomerOrder::getYRO).max().orElse(0);
                Point topStart = new Point(0, maxY);
                if (topStart.x() < rollWidth && topStart.y() >= 0 && !isPointCovered(topStart, placedOrders)) {
                    validPoints.add(topStart);
                }
            }
        } else if (validPoints.isEmpty()){
            validPoints.add(new Point(0,0));
        }

        return Set.copyOf(validPoints);
    }
}