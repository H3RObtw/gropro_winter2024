package model;

import java.util.List;
import java.util.Set;

// State during recursion
public record PlacementState(List<CustomerOrder> placedOrders, Set<Point> availableDockingPoints, int currentMaxY) {
}
