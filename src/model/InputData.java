package model;

import java.util.List;

// Input Data container
public record InputData(String description, int rollWidth, int optimizationDepth, List<CustomerOrder> orders) {
}
