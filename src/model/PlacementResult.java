package model;

import java.util.List;
import java.util.Set;

// Result container
public record PlacementResult(List<CustomerOrder> placedOrders, Set<Point> finalDockingPoints, int totalHeight,
                       double utilization) {
}
