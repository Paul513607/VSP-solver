package org.vspsolver.util;

import org.graph4j.util.VertexSet;

import java.util.Random;

public class Utils {
    public static void shuffle(int[] arr, Random random) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
        }
    }

    public static int pickAny(VertexSet vertexSet, Random random) {
        int[] vertices = vertexSet.vertices();
        return vertices[random.nextInt(vertices.length)];
    }

    public static int pickRandomInPart(byte[] part, byte targetPart, Random random) {
        int chosen = -1;
        int seen = 0;
        for (int v = 0; v < part.length; v++) {
            if (part[v] == targetPart) {
                seen++;
                if (random.nextInt(seen) == 0) {
                    chosen = v;
                }
            }
        }
        return chosen;
    }
}
