package org.vspsolver.util;

import org.graph4j.Edge;
import org.graph4j.Graph;
import org.graph4j.GraphBuilder;
import org.graph4j.util.Pair;

import java.io.*;
import java.util.zip.GZIPInputStream;

public class InstanceParser {
    public static Pair<Graph, Integer> loadGraphFromGZ(String filePath) throws IOException {
        try (InputStream fileStream = new FileInputStream(filePath);
             InputStream gzipStream = new GZIPInputStream(fileStream);
             Reader decoder = new InputStreamReader(gzipStream);
             BufferedReader buffered = new BufferedReader(decoder)) {

            String firstLine = buffered.readLine().trim();
            String[] firstParts = firstLine.split("\\s+");
            int maxShoreSize = Integer.parseInt(firstParts[0]);
            int numNodes = Integer.parseInt(firstParts[1]);
            int numEdges = Integer.parseInt(firstParts[2]);

            int[] vertices = new int[numNodes];
            for (int i = 0; i < numNodes; i++) {
                String line = buffered.readLine().trim();
                while (line.isEmpty()) {
                    line = buffered.readLine().trim();
                }
                String[] parts = line.split("\\s+");
                if (!parts[0].equals("v")) {
                    throw new IOException("Invalid node format: " + line);
                }
                int node = Integer.parseInt(parts[1]);
                double cost = Double.parseDouble(parts[2]);
                vertices[i] = node;
            }

            Edge[] edges = new Edge[numEdges];
            for (int i = 0; i < numEdges; i++) {
                String line = buffered.readLine().trim();
                while (line.isEmpty()) {
                    line = buffered.readLine().trim();
                }
                String[] parts = line.split("\\s+");
                if (!parts[0].equals("e")) {
                    throw new IOException("Invalid edge format: " + line);
                }
                int node1 = Integer.parseInt(parts[1]);
                int node2 = Integer.parseInt(parts[2]);
                double cost = Double.parseDouble(parts[3]);
                edges[i] = new Edge(node1, node2, cost);
            }

            GraphBuilder graphBuilder = GraphBuilder.vertices(vertices);
            for (Edge edge : edges) {
                graphBuilder.addEdge(edge);
            }
            return new Pair<>(graphBuilder.buildGraph(), maxShoreSize);
        }
    }
}
