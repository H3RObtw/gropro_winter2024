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
    private final boolean useAreaSortHeuristic; // Flag for sorting heuristic

    private static final long PRINT_PROGRESS_INTERVAL = 50_000_000;

    // Constructor to explicitly control heuristic
    public PlacementService(final int rollWidth, boolean useAreaSortHeuristic) {
        this(rollWidth, Executors.newWorkStealingPool(), true, useAreaSortHeuristic);
    }

    // Full constructor allowing external executor and heuristic control
    public PlacementService(final int rollWidth, ExecutorService executorService, boolean manageLifecycle, boolean useAreaSortHeuristic) {
        if (rollWidth <= 0) {
            throw new IllegalArgumentException("Roll width must be positive.");
        }
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null.");
        }
        this.rollWidth = rollWidth;
        this.executor = executorService;
        this.manageExecutorLifecycle = manageLifecycle;
        this.useAreaSortHeuristic = useAreaSortHeuristic; // Store the flag
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

            List<CustomerOrder> batchToOptimize = batchToOptimizeInput
                    .stream()
                    .map(CustomerOrder::copy)
                    .collect(Collectors.toList());

            // --- OPTIONAL OPTIMIZATION 1: Sort batch by area descending ---
            String sortInfo = "";
            if (this.useAreaSortHeuristic) {
                batchToOptimize.sort(Comparator.comparingDouble((CustomerOrder o) ->
                        (double)o.originalWidth * o.originalHeight).reversed());
                sortInfo = " (sorted by area desc)";
            }
            // --- End Optional Optimization 1 ---

            final Set<Point> batchStartDockingPoints = Set.of(new Point(0, 0));

            System.out.printf("Optimizing batch of %d orders%s starting at global Y=%d.%n",
                    batchToOptimize.size(), sortInfo, globalYOffset); // Include sort info in log

            final long batchStartCounterValue = this.recursiveCallCounter.get();
            bestBatchResultRef.set(null);

            recursivePlace(batchToOptimize, Collections.emptyList(), batchStartDockingPoints);

            final long batchCalls = this.recursiveCallCounter.get() - batchStartCounterValue;
            System.out.printf("Batch recursion calls: %,d%n", batchCalls);

            PlacementResult currentBestBatch = bestBatchResultRef.get();

            if (currentBestBatch == null) {
                System.err.printf("Warning: No valid placement found for batch%s starting at Y=%d. Adding originals back and stopping.%n", sortInfo, globalYOffset);
                remainingOrders = new ArrayList<>(batchToOptimizeInput);
                remainingOrders.addAll(nextRemainingOrders);
                break; // Stop optimization
            }

            System.out.printf("Batch finished. Best relative height: %d. Orders placed: %d%n",
                    currentBestBatch.totalHeight(), currentBestBatch.placedOrders().size());

            // --- Add results to global list (remains the same) ---
            Set<Integer> placedInBatchIds = new HashSet<>();
            for (final CustomerOrder batchPlacedOrder : currentBestBatch.placedOrders()) {
                CustomerOrder globalOrder = batchPlacedOrder.copy();
                globalOrder.placedY += globalYOffset;
                globalOrder.updateDerivedPlacementFields();
                globallyPlacedOrders.add(globalOrder);
                placedInBatchIds.add(globalOrder.getId());
            }

            // --- Update global Y offset (remains the same) ---
            globalYOffset = globallyPlacedOrders.stream()
                    .mapToInt(CustomerOrder::getYRO)
                    .max()
                    .orElse(globalYOffset);

            // --- Prepare remaining orders (remains the same) ---
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

    // --- recursivePlace Method ---
    // The internal logic of recursivePlace, including the stronger docking point
    // pruning (Opt 2b using minRemainingHeight), remains UNCHANGED from the
    // previous version. We assume that pruning optimization is safe.
    private void recursivePlace(final List<CustomerOrder> ordersToPlace,
                                final List<CustomerOrder> currentlyPlaced,
                                final Set<Point> availableDockingPoints
    ) {
        long currentCallCount = this.recursiveCallCounter.incrementAndGet();
        PlacementResult currentBest = bestBatchResultRef.get();

        if (currentCallCount > 0 && currentCallCount % PRINT_PROGRESS_INTERVAL == 0) {
            System.out.printf("...recursive calls: %,d (Current best height: %d)%n",
                    currentCallCount, currentBest != null ? currentBest.totalHeight() : -1);
        }

        // --- Base Case --- (No change)
        if (ordersToPlace.isEmpty()) {
            // ... (same as before) ...
            final int currentRelativeMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().orElse(0);
            PlacementResult potentialResult = new PlacementResult(
                    currentlyPlaced.stream().map(CustomerOrder::copy).toList(),
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

        // --- Pruning 1: Intermediate Height --- (No change)
        if (currentBest != null && !currentlyPlaced.isEmpty()) {
            // ... (same as before) ...
            final int intermediateMaxY = currentlyPlaced.stream().mapToInt(CustomerOrder::getYRO).max().getAsInt();
            if (intermediateMaxY >= currentBest.totalHeight()) {
                return;
            }
        }


        // --- Recursive Step ---

        if (currentlyPlaced.isEmpty()) {
            // --- INITIAL PLACEMENT (Parallel) --- (No change)
            // ... (same logic for parallel initial placement as before) ...
            final Point initialDockPoint = new Point(0, 0);
            if (!availableDockingPoints.contains(initialDockPoint)) {
                System.err.println("Error: Initial dock point (0,0) missing."); return;
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

                    PlacementResult currentBestForPruning = bestBatchResultRef.get();
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

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                        recursivePlace(immutableRemaining, nextPlaced, immutableNextDocking), executor);
                    futures.add(future);

                    if (orderToConsider.originalWidth == orderToConsider.originalHeight) break;
                }
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();


        } else {
            // --- SUBSEQUENT PLACEMENTS (Sequential within this branch) ---
            // Includes stronger pruning (Opt 2b), but no sorting here (list order is fixed)
            // ... (logic remains exactly the same as previous version, including minRemainingHeight calc and pruning) ...

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
                PlacementResult currentBestForPruning = bestBatchResultRef.get();

                if (currentBestForPruning != null && dockPoint.y() >= currentBestForPruning.totalHeight()) {
                    break;
                }

                if (currentBestForPruning != null && minRemainingHeight != Integer.MAX_VALUE &&
                        dockPoint.y() + minRemainingHeight >= currentBestForPruning.totalHeight()) {
                    break; // Stronger prune Opt 2b
                }


                for (final boolean rotate : new boolean[]{false, true}) {
                    final int width = rotate ? orderToTry.originalHeight : orderToTry.originalWidth;
                    final int height = rotate ? orderToTry.originalWidth : orderToTry.originalHeight;

                    final int currentItemMinDim = Math.min(width, height);
                    currentBestForPruning = bestBatchResultRef.get();
                    if (currentBestForPruning != null && dockPoint.y() + currentItemMinDim >= currentBestForPruning.totalHeight()) {
                        continue;
                    }

                    if (dockPoint.x() + width > this.rollWidth) continue;

                    currentBestForPruning = bestBatchResultRef.get();
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

                        recursivePlace(remainingForNextCall, immutableNextPlaced, immutableNextDocking);
                    }

                    if (orderToTry.originalWidth == orderToTry.originalHeight) break;
                }
            }
        }
    }


    // --- Static Helpers (isPointCovered, calculateDockingPoints) remain the same ---
    /** Static helper, checks coverage against a list of placed orders */
    private static boolean isPointCovered(final Point p, final List<CustomerOrder> orders) {
        // ... (no change) ...
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
        // ... (no change) ...
        if (placedOrders.isEmpty()) {
            return Set.of(new Point(0, 0));
        }

        final Set<Point> potentialPoints = new HashSet<>();
        potentialPoints.add(new Point(0,0));

        for (final CustomerOrder order : placedOrders) {
            if (order.isPlaced) {
                potentialPoints.add(new Point(order.getXLU(), order.getYRO()));
                potentialPoints.add(new Point(order.getXRO(), order.getYLU()));
            }
        }

        final Set<Point> validPoints = new HashSet<>();
        for (final Point p : potentialPoints) {
            if (p.x() < 0 || p.x() >= rollWidth || p.y() < 0) continue;

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