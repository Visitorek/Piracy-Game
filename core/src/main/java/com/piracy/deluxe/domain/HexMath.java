package com.piracy.deluxe.domain;

import com.badlogic.gdx.math.Vector2;

public final class HexMath {
    private static final double SQRT_3 = Math.sqrt(3.0);

    private HexMath() {
    }

    public static Vector2 hexToPixel(HexCoordinate coord, float size) {
        float x = (float) (size * (SQRT_3 * coord.getQ() + (SQRT_3 / 2.0) * coord.getR()));
        float y = (float) (size * (1.5 * coord.getR()));
        return new Vector2(x, y);
    }

    public static HexCoordinate pixelToHex(float x, float y, float size) {
        double q = (Math.sqrt(3.0) / 3.0 * x / size) - (1.0 / 3.0 * y / size);
        double r = (2.0 / 3.0 * y / size);

        double rx = q;
        double rz = r;
        double ry = -rx - rz;

        long roundedX = Math.round(rx);
        long roundedY = Math.round(ry);
        long roundedZ = Math.round(rz);

        double xDiff = Math.abs(roundedX - rx);
        double yDiff = Math.abs(roundedY - ry);
        double zDiff = Math.abs(roundedZ - rz);

        if (xDiff > yDiff && xDiff > zDiff) {
            roundedX = -roundedY - roundedZ;
        } else if (yDiff > zDiff) {
            roundedY = -roundedX - roundedZ;
        } else {
            roundedZ = -roundedX - roundedY;
        }

        return new HexCoordinate((int) roundedX, (int) roundedZ);
    }
}
