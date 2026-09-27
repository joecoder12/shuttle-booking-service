package io.github.joecoder12.shuttle.routing;

import java.util.ArrayList;
import java.util.List;

import io.github.joecoder12.shuttle.domain.GeoPoint;

/**
 * Orders a trip's pickups: a route that starts at the origin, visits every pickup exactly once and
 * ends at the destination. This is a fixed-endpoint variant of the travelling salesman problem, so an
 * exact answer is exponential; instead this uses two classic heuristics:
 *
 * <ol>
 *   <li><b>Nearest neighbour</b> (O(n²)): from the current stop, always drive to the closest unvisited pickup.</li>
 *   <li><b>2-opt</b> (O(n²) per pass): reverse any segment of the route whose reversal makes it shorter,
 *       until no such segment is left. This removes the crossing legs that nearest neighbour tends to leave.</li>
 * </ol>
 *
 * <p>Internally a route is an array of node indices: 0 is the origin, 1..n are the pickups and n+1 is the
 * destination.
 */
public final class RoutePlanner {

    private static final double EPSILON = 1e-9;

    private RoutePlanner() {
    }

    /** Returns indices into {@code pickups}, in the order they should be visited. */
    public static int[] plan(GeoPoint origin, List<GeoPoint> pickups, GeoPoint destination) {
        double[][] dist = distanceMatrix(origin, pickups, destination);
        int[] route = nearestNeighbour(dist);
        improveWithTwoOpt(route, dist);

        int[] order = new int[pickups.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = route[i + 1] - 1;
        }
        return order;
    }

    static double[][] distanceMatrix(GeoPoint origin, List<GeoPoint> pickups, GeoPoint destination) {
        List<GeoPoint> nodes = new ArrayList<>(pickups.size() + 2);
        nodes.add(origin);
        nodes.addAll(pickups);
        nodes.add(destination);

        int size = nodes.size();
        double[][] dist = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                dist[i][j] = dist[j][i] = GeoDistance.haversineKm(nodes.get(i), nodes.get(j));
            }
        }
        return dist;
    }

    static int[] nearestNeighbour(double[][] dist) {
        int pickups = dist.length - 2;
        int[] route = new int[dist.length];
        boolean[] visited = new boolean[dist.length];

        int current = 0;
        for (int step = 1; step <= pickups; step++) {
            int next = -1;
            for (int candidate = 1; candidate <= pickups; candidate++) {
                if (!visited[candidate] && (next == -1 || dist[current][candidate] < dist[current][next])) {
                    next = candidate;
                }
            }
            visited[next] = true;
            route[step] = next;
            current = next;
        }
        route[route.length - 1] = route.length - 1;
        return route;
    }

    /** Applies improving 2-opt moves until none is left. The origin and destination never move. */
    static void improveWithTwoOpt(int[] route, double[][] dist) {
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < route.length - 2; i++) {
                for (int k = i + 1; k < route.length - 1; k++) {
                    if (twoOptGain(route, dist, i, k) > EPSILON) {
                        reverse(route, i, k);
                        improved = true;
                    }
                }
            }
        }
    }

    /**
     * How much shorter the route gets if {@code route[i..k]} is reversed. Only the two edges at the
     * ends of the segment change: (a,b) and (c,d) become (a,c) and (b,d).
     */
    static double twoOptGain(int[] route, double[][] dist, int i, int k) {
        int a = route[i - 1];
        int b = route[i];
        int c = route[k];
        int d = route[k + 1];
        return dist[a][b] + dist[c][d] - dist[a][c] - dist[b][d];
    }

    static double routeLength(int[] route, double[][] dist) {
        double total = 0;
        for (int i = 1; i < route.length; i++) {
            total += dist[route[i - 1]][route[i]];
        }
        return total;
    }

    private static void reverse(int[] route, int from, int to) {
        while (from < to) {
            int tmp = route[from];
            route[from++] = route[to];
            route[to--] = tmp;
        }
    }
}
