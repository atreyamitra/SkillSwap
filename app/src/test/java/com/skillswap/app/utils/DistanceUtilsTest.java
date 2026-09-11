package com.skillswap.app.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for {@link DistanceUtils}'s Haversine great-circle distance helper. */
public class DistanceUtilsTest {

    private static final double DELTA_KM = 1.0; // Haversine assumes a spherical Earth.

    @Test
    public void sameCoordinates_zeroDistance() {
        double km = DistanceUtils.haversineKm(12.9716, 77.5946, 12.9716, 77.5946);
        assertEquals(0.0, km, 0.0001);
    }

    @Test
    public void bengaluruToHyderabad_matchesKnownDistance() {
        // Bengaluru (12.9716, 77.5946) to Hyderabad (17.3850, 78.4867) is ~500 km.
        double km = DistanceUtils.haversineKm(12.9716, 77.5946, 17.3850, 78.4867);
        assertEquals(500.0, km, 20.0);
    }

    @Test
    public void distanceIsSymmetric() {
        double forward = DistanceUtils.haversineKm(12.9716, 77.5946, 28.6139, 77.2090);
        double backward = DistanceUtils.haversineKm(28.6139, 77.2090, 12.9716, 77.5946);
        assertEquals(forward, backward, DELTA_KM);
    }

    @Test
    public void antipodalPoints_approximatelyHalfEarthCircumference() {
        double km = DistanceUtils.haversineKm(0, 0, 0, 180);
        // Half the circumference of a sphere with the class's assumed 6371 km radius.
        assertEquals(Math.PI * 6371, km, DELTA_KM);
    }

    @Test
    public void hasValidCoordinates_falseForUnsetZeroZero() {
        assertFalse(DistanceUtils.hasValidCoordinates(0, 0));
    }

    @Test
    public void hasValidCoordinates_trueForRealCoordinates() {
        assertTrue(DistanceUtils.hasValidCoordinates(12.9716, 77.5946));
    }

    @Test
    public void hasValidCoordinates_trueWhenOnlyOneAxisIsZero() {
        // Equator or prime-meridian points are real locations, only (0,0) is treated as unset.
        assertTrue(DistanceUtils.hasValidCoordinates(0, 77.5946));
        assertTrue(DistanceUtils.hasValidCoordinates(12.9716, 0));
    }
}
