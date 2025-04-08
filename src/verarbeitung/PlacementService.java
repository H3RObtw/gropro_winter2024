package verarbeitung; // Adjust package name if necessary

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
import java.util.stream.IntStream;

/**
 * Placement Service that processes batches of orders in parallel and then
 * stacks the results.
 * WARNING: This approach prioritizes parallel computation speed over placement
 * quality. Stacking batches based only on their maximum height can lead to
 * significant empty space and suboptimal results compared to sequential batch processing.
 */
public class PlacementService implements AutoCloseable {

    private final int rollWidth;
    private final ExecutorService executor;
    private final boolean manageExecutorLifecycle;
    private final boolean useAreaSortHeuristic;
    private final int optimizationDepth; // Batch size

    private static final long PRINT_PROGRESS_INTERVAL = 50_000_000; // For internal batch logging

    /**
     * Constructor using default WorkStealingPool and enabling area sort heuristic.
     *
     * @param rollWidth         The width of the roll.
     * @param optimizationDepth The number of orders per batch.
     */
    public PlacementService(int rollWidth, int optimizationDepth) {
        this(rollWidth, optimizationDepth, true, Executors.newWorkStealingPool(), true);
    }

    /**
     * Constructor allowing control over the area sort heuristic. Uses default WorkStealingPool.
     *
     * @param rollWidth            The width of the roll.
     * @param optimizationDepth    The number of orders per batch.
     * @param useAreaSortHeuristic Whether to sort orders by area within each batch before placement.
     */
    public PlacementService(int rollWidth, int optimizationDepth, boolean useAreaSortHeuristic) {
        this(rollWidth, optimizationDepth, useAreaSortHeuristic, Executors.newWorkStealingPool(), true);
    }

    /**
     * Full constructor allowing explicit ExecutorService and control over heuristic.
     *
     * @param rollWidth            The width of the roll.
     * @param optimizationDepth    The number of orders per batch.
     * @param useAreaSortHeuristic Whether to sort orders by area within each batch.
     * @param executorService      The executor service to use for parallel tasks.
     * @param manageLifecycle      True if this service should shut down the executor, false otherwise.
     */
    public PlacementService(int rollWidth, int optimizationDepth, boolean useAreaSortHeuristic, ExecutorService executorService, boolean manageLifecycle) {
        if (rollWidth <= 0) {
            throw new IllegalArgumentException("Roll width must be positive.");
        }
        if (optimizationDepth <= 0) {
            throw new IllegalArgumentException("Optimization depth (batch size) must be positive.");
        }
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null.");
        }
        this.rollWidth = rollWidth;
        this.optimizationDepth = optimizationDepth;
        this.useAreaSortHeuristic = useAreaSortHeuristic;
        this.executor = executorService;
        this.manageExecutorLifecycle = manageLifecycle;
        // Ensure CustomerOrder has required methods
        try {
            CustomerOrder.class.getMethod("copy");
            CustomerOrder.class.getMethod("updateDerivedPlacementFields");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("CustomerOrder class must have public copy() and updateDerivedPlacementFields() methods.", e);
        }
    }

    @Override
    public void close() {
        if (manageExecutorLifecycle) {
            System.out.println("Shutting down ParallelBatchPlacementService ExecutorService...");
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
            System.out.println("ParallelBatchPlacementService closed (external executor not shut down).");
        }
    }

    /**
     * Finds a placement by optimizing batches in parallel and stacking results.
     * Note: This yields potentially faster computation but likely suboptimal placement quality.
     *
     * @param allOrders The list of all customer orders to place.
     * @return A PlacementResult representing the combined placement.
     */
    public PlacementResult findOptimalPlacementParallelBatches(final List<CustomerOrder> allOrders) {
        allOrders.forEach(CustomerOrder::unsetPlacement); // Reset state

        // 1. Split into batches (using copies)
        final List<List<CustomerOrder>> batches = IntStream.range(0, (allOrders.size() + optimizationDepth - 1) / optimizationDepth)
                .mapToObj(i -> {
                    int start = i * optimizationDepth;
                    int end = Math.min(start + optimizationDepth, allOrders.size());
                    // Create copies for each batch to ensure independence
                    return allOrders.subList(start, end).stream()
                            .map(CustomerOrder::copy)
                            .collect(Collectors.toList());
                })
                .toList();

        if (batches.isEmpty()) {
            System.out.println("No orders to place.");
            return new PlacementResult(Collections.emptyList(), Set.of(new Point(0,0)), 0, 0.0);
        }

        System.out.printf("Processing %d batches in parallel (Batch Size: %d, Heuristic Sort: %b)...%n",
                batches.size(), optimizationDepth, useAreaSortHeuristic);

        // 2. Launch parallel calculation for each batch
        List<CompletableFuture<PlacementResult>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() ->
                                optimizeSingleBatch(batch), // optimizeSingleBatch operates on copies
                        executor))
                .toList();

        // 3. Wait for all batch optimizations to complete
        System.out.println("Waiting for parallel batch computations to finish...");
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        System.out.println("All batch computations finished.");

        // 4. Combine results by stacking
        final List<CustomerOrder> globallyPlacedOrders = new ArrayList<>();
        int currentGlobalYOffset = 0;

        System.out.println("Combining parallel batch results by stacking...");
        int batchIndex = 0;
        for (CompletableFuture<PlacementResult> future : futures) {
            batchIndex++;
            PlacementResult batchResult;
            try {
                batchResult = future.join(); // Get completed result (already finished)
            } catch (Exception e) {
                System.err.printf("Error getting result for batch %d: %s%n", batchIndex, e.getMessage());
                // Optionally log stack trace: e.printStackTrace();
                continue; // Skip this batch if fetching result failed
            }


            if (batchResult == null || batchResult.placedOrders().isEmpty()) {
                System.out.printf("  - Batch %d: No orders placed or result was null.%n", batchIndex);
                continue; // Skip empty or failed batches
            }

            int batchRelativeHeight = batchResult.totalHeight();
            System.out.printf("  - Batch %d: Adding %d orders (Rel Height: %d) starting at Global Y: %d%n",
                    batchIndex, batchResult.placedOrders().size(), batchRelativeHeight, currentGlobalYOffset);

            for (CustomerOrder batchPlacedOrder : batchResult.placedOrders()) {
                CustomerOrder globalOrder = batchPlacedOrder.copy(); // Create final copy
                globalOrder.placedY += currentGlobalYOffset;     // Apply offset
                globalOrder.updateDerivedPlacementFields();      // Update calculated coords
                globallyPlacedOrders.add(globalOrder);
            }

            // Update the offset for the *next* batch using the actual max Y achieved
            currentGlobalYOffset = globallyPlacedOrders.stream()
                    .mapToInt(CustomerOrder::getYRO)
                    .max()
                    .orElse(currentGlobalYOffset); // Keep offset if list somehow became empty
        }
        System.out.println("Batch combination finished.");

        // 5. Final Calculations
        final int finalMaxY = globallyPlacedOrders.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
        final double totalOrderArea = globallyPlacedOrders.stream()
                .filter(CustomerOrder::isPlaced) // Ensure only placed orders contribute
                .mapToDouble(o -> (double) o.currentWidth * o.currentHeight)
                .sum();
        final double totalRollAreaUsed = (double) this.rollWidth * finalMaxY;
        final double utilization = (totalRollAreaUsed > 0) ? (totalOrderArea / totalRollAreaUsed) * 100.0 : 0.0;

        final Set<Point> finalAbsoluteDockingPoints = calculateDockingPoints(globallyPlacedOrders, this.rollWidth);

        // 6. Cleanup original input orders state
        final Set<Integer> finalPlacedOrderIds = globallyPlacedOrders.stream().map(CustomerOrder::getId).collect(Collectors.toSet());
        for (final CustomerOrder initialOrder : allOrders) {
            if (!finalPlacedOrderIds.contains(initialOrder.getId())) {
                initialOrder.unsetPlacement();
            } else {
                // Optional: Update original object state to reflect final placement
                globallyPlacedOrders.stream()
                        .filter(p -> p.getId() == initialOrder.getId())
                        .findFirst()
                        .ifPresent(placedOrder -> initialOrder.setPlacement(placedOrder.placedX, placedOrder.placedY, placedOrder.isRotated));
            }
        }

        System.out.printf("Parallel batch placement finished. Total Height: %d, Utilization: %.2f%%%n", finalMaxY, utilization);
        return new PlacementResult(List.copyOf(globallyPlacedOrders), finalAbsoluteDockingPoints, finalMaxY, utilization);
    }

    // --- Helper to optimize a single, independent batch ---
    private PlacementResult optimizeSingleBatch(List<CustomerOrder> batchOrders) {
        // Isolated state for this batch optimization task
        AtomicReference<PlacementResult> batchBestResult = new AtomicReference<>(null);
        AtomicLong batchCallCounter = new AtomicLong(0);
        String batchInfo = String.format("Batch (Size %d, Hash %d)", batchOrders.size(), batchOrders.hashCode()); // Basic identifier

        System.out.printf("Starting optimization for %s%n", batchInfo);

        if (batchOrders.isEmpty()) {
            return null; // Nothing to place
        }

        // --- Optional: Sort batch by area descending ---
        if (this.useAreaSortHeuristic) {
            batchOrders.sort(Comparator.comparingDouble((CustomerOrder o) ->
                    (double) o.originalWidth * o.originalHeight).reversed());
            // System.out.printf("  %s: Sorted by area.%n", batchInfo);
        }

        final Set<Point> batchStartDockingPoints = Set.of(new Point(0, 0));

        // --- Call the recursive function with batch-local state ---
        recursivePlaceForSingleBatch(
                batchOrders,               // The list of orders (copies) for this batch
                Collections.emptyList(),   // Start with no orders placed
                batchStartDockingPoints,   // Start docking at origin (relative)
                batchBestResult,           // Holder for *this batch's* best result
                batchCallCounter           // Counter for *this batch's* calls
        );

        long calls = batchCallCounter.get();
        PlacementResult finalResult = batchBestResult.get();
        System.out.printf("Finished optimization for %s. Calls: %,d. Best Rel Height: %d%n",
                          batchInfo, calls, finalResult != null ? finalResult.totalHeight() : -1);

        return finalResult; // Return the best result found for this batch
    }


    // --- Recursive placement function adapted for isolated batch state ---
    private void recursivePlaceForSingleBatch(
            final List<CustomerOrder> ordersToPlace,   // Immutable List<OrderCopy>
            final List<CustomerOrder> currentlyPlaced, // Immutable List<OrderCopy>
            final Set<Point> availableDockingPoints,   // Immutable Set<Point>
            // --- Batch-local state holders ---
            final AtomicReference<PlacementResult> localBestBatchResultRef,
            final AtomicLong localRecursiveCallCounter
    ) {
        // --- Increment local counter ---
        long currentCallCount = localRecursiveCallCounter.incrementAndGet();
        // --- Read local best result ---
        PlacementResult currentBest = localBestBatchResultRef.get();

        // Optional Progress Printing (uses local counter)
        if (currentCallCount > 0 && currentCallCount % PRINT_PROGRESS_INTERVAL == 0) {
            // Potentially add a batch identifier here if needed for detailed logging
            System.out.printf("...[%d] recursive calls: %,d (Current best height: %d)%n",
                    Thread.currentThread().threadId(), // Simple thread ID as indicator
                    currentCallCount, currentBest != null ? currentBest.totalHeight() : -1);
        }

        // --- Base Case ---
        if (ordersToPlace.isEmpty()) {
            final int currentRelativeMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
            // Create result with copies (essential!)
            PlacementResult potentialResult = new PlacementResult(
                    currentlyPlaced.stream().map(CustomerOrder::copy).toList(),
                    Set.copyOf(availableDockingPoints),
                    currentRelativeMaxY,
                    0.0 // Utilization not relevant here
            );
            // --- Update local best result atomically ---
            localBestBatchResultRef.accumulateAndGet(potentialResult,
                    (existingBest, potentialNew) ->
                            (existingBest == null || potentialNew.totalHeight() < existingBest.totalHeight())
                                    ? potentialNew : existingBest
            );
            return;
        }

        // --- Pruning 1: Intermediate Height ---
        // --- Read local best result ---
        currentBest = localBestBatchResultRef.get();
        if (currentBest != null && !currentlyPlaced.isEmpty()) {
            final int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().getAsInt();
            if (intermediateMaxY >= currentBest.totalHeight()) {
                return; // Prune this branch
            }
        }

        // --- Recursive Step ---
        if (currentlyPlaced.isEmpty()) {
            // --- INITIAL PLACEMENT (Parallel within this batch task) ---
            final Point initialDockPoint = new Point(0, 0);
            if (!availableDockingPoints.contains(initialDockPoint)) {
                System.err.printf("[%d] Error: Initial dock point (0,0) missing!%n", Thread.currentThread().threadId());
                return;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < ordersToPlace.size(); i++) {
                final CustomerOrder orderToConsider = ordersToPlace.get(i);

                final List<CustomerOrder> remainingForNextCall = new ArrayList<>(ordersToPlace.size() - 1);
                if (i > 0) remainingForNextCall.addAll(ordersToPlace.subList(0, i));
                if (i < ordersToPlace.size() - 1) remainingForNextCall.addAll(ordersToPlace.subList(i + 1, ordersToPlace.size()));
                final List<CustomerOrder> immutableRemaining = List.copyOf(remainingForNextCall);

                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToConsider.originalHeight : orderToConsider.originalWidth;
                    final int height = rotate ? orderToConsider.originalWidth : orderToConsider.originalHeight;

                    if (initialDockPoint.x() + width > this.rollWidth) continue;

                    // --- Read local best result for pruning check ---
                    PlacementResult currentBestForPruning = localBestBatchResultRef.get();
                    if (currentBestForPruning != null && initialDockPoint.y() + height >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    final CustomerOrder placedOrderCopy = orderToConsider.copy();
                    placedOrderCopy.setPlacement(initialDockPoint.x(), initialDockPoint.y(), rotate);
                    final List<CustomerOrder> nextPlaced = List.of(placedOrderCopy);

                    final Set<Point> nextDockingPoints = new HashSet<>(2);
                    final Point newTopLeft = new Point(placedOrderCopy.getXLU(), placedOrderCopy.getYRO());
                    final Point newBottomRight = new Point(placedOrderCopy.getXRO(), placedOrderCopy.getYLU());

                    if (newTopLeft.x() <= this.rollWidth && !isPointCovered(newTopLeft, nextPlaced)) nextDockingPoints.add(newTopLeft);
                    if (newBottomRight.x() <= this.rollWidth && !isPointCovered(newBottomRight, nextPlaced)) nextDockingPoints.add(newBottomRight);
                    final Set<Point> immutableNextDocking = Set.copyOf(nextDockingPoints);

                    // --- Launch async task calling THIS function, passing LOCAL state ---
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        recursivePlaceForSingleBatch(immutableRemaining, nextPlaced, immutableNextDocking,
                                localBestBatchResultRef, localRecursiveCallCounter); // Pass local refs!
                    }, executor); // Use the shared executor
                    futures.add(future);

                    if (orderToConsider.originalWidth == orderToConsider.originalHeight) break;
                }
            }
            // Wait for parallel first-placement branches *within this batch* to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } else {
            // --- SUBSEQUENT PLACEMENTS (Sequential within this branch) ---
            final CustomerOrder orderToTry = ordersToPlace.getFirst();
            final List<CustomerOrder> remainingForNextCall = ordersToPlace.subList(1, ordersToPlace.size());

            final int minRemainingHeight = ordersToPlace.stream()
                    .mapToInt(o -> Math.min(o.originalWidth, o.originalHeight))
                    .min()
                    .orElse(Integer.MAX_VALUE);

            final List<Point> sortedDockingPoints = availableDockingPoints.stream()
                    .sorted(Comparator.comparingInt(Point::y).thenComparingInt(Point::x))
                    .toList();

            for (final Point dockPoint : sortedDockingPoints) {
                // --- Read local best result ---
                PlacementResult currentBestForPruning = localBestBatchResultRef.get();

                // --- Pruning 2 ---
                if (currentBestForPruning != null && dockPoint.y() >= currentBestForPruning.totalHeight()) {
                    break;
                }

                // --- Stronger Pruning (Opt 2b) using local best result ---
                if (currentBestForPruning != null && minRemainingHeight != Integer.MAX_VALUE &&
                        dockPoint.y() + minRemainingHeight >= currentBestForPruning.totalHeight()) {
                    break;
                }

                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    final int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    // --- Pruning 3 (using local best result) ---
                    final int currentItemMinDim = Math.min(width, height);
                    currentBestForPruning = localBestBatchResultRef.get(); // Re-read
                    if (currentBestForPruning != null && dockPoint.y() + currentItemMinDim >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    if (dockPoint.x() + width > this.rollWidth) continue;

                    // --- Height Pruning (using local best result) ---
                    currentBestForPruning = localBestBatchResultRef.get(); // Re-read
                    if (currentBestForPruning != null && dockPoint.y() + height >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    final CustomerOrder candidateOrder = orderToTry.copy();
                    candidateOrder.setPlacement(dockPoint.x(), dockPoint.y(), rotate);

                    boolean overlaps = false;
                    for (final CustomerOrder placed : currentlyPlaced) {
                        if (candidateOrder.overlaps(placed)) {
                            overlaps = true;
                            break;
                        }
                    }

                    if (!overlaps) {
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

                        // --- Recursive call passing LOCAL state holders down ---
                        recursivePlaceForSingleBatch(remainingForNextCall, immutableNextPlaced, immutableNextDocking,
                                localBestBatchResultRef, localRecursiveCallCounter); // Pass local refs!
                    }

                    if (orderToTry.originalWidth == orderToTry.originalHeight) break;
                } // End rotation loop
            } // End docking point loop
        } // End else (subsequent placements)
    } // End recursivePlaceForSingleBatch


    // --- Static helper methods (unchanged) ---

    /** Static helper, checks coverage against a list of placed orders */
    private static boolean isPointCovered(final Point p, final List<CustomerOrder> orders) {
        for (final CustomerOrder order : orders) {
            // Check assumes order IS placed and uses its current coordinates
            if (order.isPlaced && // Explicitly check if placed, just to be safe
                    p.x() >= order.getXLU() && p.x() < order.getXRO() &&
                    p.y() >= order.getYLU() && p.y() < order.getYRO()) {
                return true;
            }
        }
        return false;
    }

    /** Static helper, calculates valid docking points */
    private static Set<Point> calculateDockingPoints(final List<CustomerOrder> placedOrders, final int rollWidth) {
        if (placedOrders.isEmpty()) {
            return Set.of(new Point(0, 0));
        }

        final Set<Point> potentialPoints = new HashSet<>();
        potentialPoints.add(new Point(0,0)); // Always consider the origin initially

        // Add corners of placed items as potential docking points
        for (final CustomerOrder order : placedOrders) {
            if (order.isPlaced) { // Ensure it's actually placed
                potentialPoints.add(new Point(order.getXLU(), order.getYRO())); // Top-left corner's Y becomes next potential bottom-left Y
                potentialPoints.add(new Point(order.getXRO(), order.getYLU())); // Bottom-right corner's X becomes next potential bottom-left X
            }
        }

        // Filter points: remove invalid (out of bounds) or covered points
        final Set<Point> validPoints = new HashSet<>();
        for (final Point p : potentialPoints) {
            // Check bounds (X must be strictly less than rollWidth for placement start)
            if (p.x() < 0 || p.x() >= rollWidth || p.y() < 0) {
                continue;
            }

            // Check if the point itself is covered by the INTERIOR of any placed order
            if (!isPointCovered(p, placedOrders)) {
                validPoints.add(p);
            }
        }

        // Fallback logic if all generated points are covered
        if (validPoints.isEmpty() && !placedOrders.isEmpty()) {
            Point origin = new Point(0,0);
            // Check origin validity (bounds and coverage)
            if (origin.x() < rollWidth && origin.y() >= 0 && !isPointCovered(origin, placedOrders)) {
                validPoints.add(origin);
            } else {
                // Try adding the point (0, highestY) as a potential start for the next "row"
                int maxY = placedOrders.stream().filter(o -> o.isPlaced).mapToInt(CustomerOrder::getYRO).max().orElse(0);
                Point topStart = new Point(0, maxY);
                // Check bounds and coverage for this potential point too
                if (topStart.x() < rollWidth && topStart.y() >= 0 && !isPointCovered(topStart, placedOrders)) {
                    validPoints.add(topStart);
                }
                // If still empty, it means no valid starting point could be found based on corners.
            }
        }
        // Ensure origin is present if nothing is placed yet (should be added by initial Set.of, but belt-and-suspenders)
        else if (placedOrders.isEmpty() && validPoints.isEmpty()){
            validPoints.add(new Point(0,0));
        }

        return Set.copyOf(validPoints); // Return immutable set
    }

} // End of class ParallelBatchPlacementService