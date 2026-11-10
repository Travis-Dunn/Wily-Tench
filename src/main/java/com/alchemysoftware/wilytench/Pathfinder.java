package com.alchemysoftware.wilytench;

/**
 * Finds the shortest walk between two tiles of a map.
 *
 * Steps go to any of the eight neighboring tiles. A diagonal step is
 * allowed only when both tiles it squeezes between are open, so nothing
 * cuts a corner. When the goal cannot be reached, because it is solid
 * or walled off, the walk ends on the reachable tile closest to it.
 *
 * All working memory is allocated once, in the constructor.
 */
final class Pathfinder {
    /* West, east, south, north, then the diagonals. Trying the straight
       steps first makes a route prefer them when two are equally short. */
    private static final int[] DX = { -1, 1, 0, 0, -1, 1, -1, 1 };
    private static final int[] DY = { 0, 0, 1, -1, 1, 1, -1, -1 };

    private static final int UNSEEN = -1;

    private final World world;
    private final int[] cameFrom, queue;

    Pathfinder(World world) {
        this.world = world;
        cameFrom = new int[world.w * world.h];
        queue = new int[world.w * world.h];
    }

    /**
     * @param out receives the tiles to step onto, in order, each as
     *            y * world.w + x; must hold world.w * world.h entries
     * @return the number of steps written, zero if there is nowhere
     *         better to be than the start
     */
    int Find(int fromX, int fromY, int toX, int toY, int[] out) {
        int w = world.w, start = fromY * w + fromX;
        int head = 0, tail = 0, i;
        int best = start, bestDist = DistSq(fromX, fromY, toX, toY);

        for (i = 0; i < cameFrom.length; i++) cameFrom[i] = UNSEEN;
        cameFrom[start] = start;
        queue[tail++] = start;

        while (head < tail && bestDist > 0) {
            int cur = queue[head++], x = cur % w, y = cur / w;

            for (i = 0; i < DX.length; i++) {
                int nx = x + DX[i], ny = y + DY[i];

                if (world.Solid(nx, ny)) continue;
                if (DX[i] != 0 && DY[i] != 0 &&
                        (world.Solid(nx, y) || world.Solid(x, ny)))
                    continue;

                int next = ny * w + nx;
                if (cameFrom[next] != UNSEEN) continue;
                cameFrom[next] = cur;
                queue[tail++] = next;

                /* Tiles are found nearest-first, so of two equally
                   close to the goal the earlier needs the shorter walk. */
                int dist = DistSq(nx, ny, toX, toY);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = next;
                }
            }
        }

        int n = 0;
        for (i = best; i != start; i = cameFrom[i]) out[n++] = i;
        for (int a = 0, b = n - 1; a < b; a++, b--) {
            int t = out[a];
            out[a] = out[b];
            out[b] = t;
        }
        return n;
    }

    private static int DistSq(int x0, int y0, int x1, int y1) {
        int dx = x1 - x0, dy = y1 - y0;
        return dx * dx + dy * dy;
    }
}
