package model;

import java.util.Objects;

// Customer Order representation
public class CustomerOrder {
    public final int originalWidth;
    public final int originalHeight;
    public final int id;
    public final String description;

    // Placement details
    boolean isPlaced = false;
    public int placedX = -1;
    public int placedY = -1;
    public int currentWidth = -1; // Width after potential rotation
    public int currentHeight = -1; // Height after potential rotation
    public boolean isRotated = false;

    public CustomerOrder(int width, int height, int id, String description) {
        this.originalWidth = width;
        this.originalHeight = height;
        this.id = id;
        this.description = description;
        this.currentWidth = width; // Start with original orientation
        this.currentHeight = height;
    }

    public int getXLU() {
        return placedX;
    }

    public int getYLU() {
        return placedY;
    }

    public int getXRO() {
        return placedX + currentWidth;
    }

    public int getYRO() {
        return placedY + currentHeight;
    }

    public void setPlacement(int x, int y, boolean rotated) {
        this.isPlaced = true;
        this.placedX = x;
        this.placedY = y;
        this.isRotated = rotated;
        if (rotated) {
            this.currentWidth = originalHeight;
            this.currentHeight = originalWidth;
        } else {
            this.currentWidth = originalWidth;
            this.currentHeight = originalHeight;
        }
    }

    public void unsetPlacement() {
        this.isPlaced = false;
        this.placedX = -1;
        this.placedY = -1;
        this.isRotated = false;
        this.currentWidth = originalWidth;
        this.currentHeight = originalHeight;
    }

    // Check for overlap with another placed order
    public boolean overlaps(CustomerOrder other) {
        if (!this.isPlaced || !other.isPlaced || this == other) {
            return false;
        }
        // Standard rectangle overlap check
        return this.getXLU() < other.getXRO() && this.getXRO() > other.getXLU() &&
                this.getYLU() < other.getYRO() && this.getYRO() > other.getYLU();
    }

    @Override
    public String toString() {
        return String.format("%d, %d, %d, %s", originalWidth, originalHeight, id, description);
    }

    // Need equals and hashCode based on ID if used in Sets/Maps where uniqueness matters
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerOrder that = (CustomerOrder) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
