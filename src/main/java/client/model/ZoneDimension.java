package client.model;

import java.util.Objects;

public class ZoneDimension {

    private int xMin;
    private int xMax;
    private int yMin;
    private int yMax;

    public ZoneDimension(int xMin, int xMax, int yMin, int yMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public int getXMin() {
        return xMin;
    }

    public int getXMax() {
        return xMax;
    }

    public int getYMin() {
        return yMin;
    }

    public int getYMax() {
        return yMax;
    }
    
    public boolean contains(Point point) {
        return point.x >= xMin && point.x <= xMax && 
               point.y >= yMin && point.y <= yMax;
    }

    @Override
    public int hashCode() {
        return Objects.hash(xMax, xMin, yMax, yMin);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ZoneDimension other = (ZoneDimension) obj;
        return xMax == other.xMax && xMin == other.xMin && 
               yMax == other.yMax && yMin == other.yMin;
    }

    @Override
    public String toString() {
        return "ZoneDimension [" + xMin + "," + xMax + ", " + yMin + ", " + yMax + "]";
    }
} 