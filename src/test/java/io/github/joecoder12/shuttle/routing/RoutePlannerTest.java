package io.github.joecoder12.shuttle.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.joecoder12.shuttle.domain.GeoPoint;

class RoutePlannerTest {

    private static final GeoPoint DEPOT = new GeoPoint(12.90, 77.60);
    private static final GeoPoint OFFICE = new GeoPoint(12.99, 77.60);

    @Test
    void noPickupsMeansAnEmptyOrder() {
        assertThat(RoutePlanner.plan(DEPOT, List.of(), OFFICE)).isEmpty();
    }

    @Test
    void pickupsOnTheWayAreVisitedInOrderOfDistanceFromTheDepot() {
        List<GeoPoint> pickups = List.of(
                new GeoPoint(12.96, 77.60),
                new GeoPoint(12.92, 77.60),
                new GeoPoint(12.94, 77.60));

        assertThat(RoutePlanner.plan(DEPOT, pickups, OFFICE)).containsExactly(1, 2, 0);
    }

    @Test
    void everyPickupIsVisitedExactlyOnce() {
        Random random = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            List<GeoPoint> pickups = randomPickups(random, 1 + random.nextInt(12));

            int[] order = RoutePlanner.plan(DEPOT, pickups, OFFICE);

            assertThat(order).containsExactlyInAnyOrder(IntStream.range(0, pickups.size()).toArray());
        }
    }

    @Test
    void twoOptNeverLengthensTheRouteAndLeavesNoImprovingMove() {
        Random random = new Random(42);
        int strictlyImproved = 0;
        for (int trial = 0; trial < 300; trial++) {
            List<GeoPoint> pickups = randomPickups(random, 2 + random.nextInt(10));
            double[][] dist = RoutePlanner.distanceMatrix(DEPOT, pickups, OFFICE);

            int[] greedy = RoutePlanner.nearestNeighbour(dist);
            int[] improved = Arrays.copyOf(greedy, greedy.length);
            RoutePlanner.improveWithTwoOpt(improved, dist);

            double before = RoutePlanner.routeLength(greedy, dist);
            double after = RoutePlanner.routeLength(improved, dist);
            assertThat(after).isLessThanOrEqualTo(before + 1e-9);
            if (after < before - 1e-9) {
                strictlyImproved++;
            }
            for (int i = 1; i < improved.length - 2; i++) {
                for (int k = i + 1; k < improved.length - 1; k++) {
                    assertThat(RoutePlanner.twoOptGain(improved, dist, i, k)).isLessThanOrEqualTo(1e-9);
                }
            }
        }
        // 2-opt should actually be doing work on top of the greedy route, not just passing it through.
        assertThat(strictlyImproved).isPositive();
    }

    @Test
    void matchesTheBruteForceOptimumOnASmallInstanceWhereGreedyIsWrong() {
        // On this layout the greedy route is ~24.4 km; the best possible route, which 2-opt finds, is ~18.8 km.
        GeoPoint depot = new GeoPoint(0, 0);
        GeoPoint office = new GeoPoint(0, 0.10);
        List<GeoPoint> pickups = List.of(
                new GeoPoint(0.002, 0.070),
                new GeoPoint(-0.026, 0.042),
                new GeoPoint(-0.004, 0.088),
                new GeoPoint(0.026, 0.037));
        double[][] dist = RoutePlanner.distanceMatrix(depot, pickups, office);

        double optimum = bruteForceOptimum(pickups.size(), dist);

        double greedy = RoutePlanner.routeLength(RoutePlanner.nearestNeighbour(dist), dist);
        int[] order = RoutePlanner.plan(depot, pickups, office);

        assertThat(greedy).isGreaterThan(optimum + 1e-6);
        assertThat(lengthOf(order, dist)).isCloseTo(optimum, within(1e-9));
    }

    private static List<GeoPoint> randomPickups(Random random, int count) {
        List<GeoPoint> pickups = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pickups.add(new GeoPoint(12.85 + random.nextDouble() * 0.2, 77.50 + random.nextDouble() * 0.2));
        }
        return pickups;
    }

    private static double lengthOf(int[] pickupOrder, double[][] dist) {
        int[] route = new int[pickupOrder.length + 2];
        for (int i = 0; i < pickupOrder.length; i++) {
            route[i + 1] = pickupOrder[i] + 1;
        }
        route[route.length - 1] = route.length - 1;
        return RoutePlanner.routeLength(route, dist);
    }

    private static double bruteForceOptimum(int pickups, double[][] dist) {
        return permutations(IntStream.range(0, pickups).toArray(), 0)
                .stream()
                .mapToDouble(order -> lengthOf(order, dist))
                .min()
                .orElseThrow();
    }

    private static List<int[]> permutations(int[] items, int start) {
        List<int[]> result = new ArrayList<>();
        if (start == items.length) {
            result.add(items.clone());
            return result;
        }
        for (int i = start; i < items.length; i++) {
            swap(items, start, i);
            result.addAll(permutations(items, start + 1));
            swap(items, start, i);
        }
        return result;
    }

    private static void swap(int[] items, int i, int j) {
        int tmp = items[i];
        items[i] = items[j];
        items[j] = tmp;
    }
}
