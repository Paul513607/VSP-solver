package org.vspsolver;

import org.graph4j.Graph;
import org.graph4j.GraphTests;
import org.graph4j.generators.GraphGenerator;
import org.graph4j.util.VertexSet;
import org.graph4j.vsp.BacktrackVertexSeparator;
import org.graph4j.vsp.GreedyVertexSeparator;
import org.graph4j.vsp.VertexSeparator;
import org.vspsolver.bls.*;

import java.util.Arrays;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        Graph graph = GraphGenerator.randomGnp(100, Math.random());
        // int maxShoreSize = 2 * graph.numVertices() / 3; // More restrictive than 2n/3 for better balance
        int maxShoreSize = 1 + new Random().nextInt(graph.numVertices() - 3);;

        BreakoutLocalSearchVSP alg = new BreakoutLocalSearchVSP(
                graph, maxShoreSize);
        /*BacktrackVertexSeparator alg = new BacktrackVertexSeparator(
                graph, maxShoreSize);*/
        VertexSeparator sep = alg.getSeparator();

        System.out.println("Left shore size: " + sep.leftShore().size());
        System.out.println("Right shore size: " + sep.rightShore().size());
        System.out.println("Separator size: " + sep.separator().size());
        System.out.println("Left shore: " + Arrays.toString(sep.leftShore().vertices()));
        System.out.println("Right shore: " + Arrays.toString(sep.rightShore().vertices()));
        if (!sep.rightShore().isEmpty()) {
            System.out.println("Graph is not connected");
        }
        System.out.println("Left shore size: " + sep.leftShore().size());
        System.out.println("Right shore size: " + sep.rightShore().size());

        System.out.println("Separator size: " + sep.separator().size());
        System.out.println("Separator: " + Arrays.toString(sep.separator().vertices()));

        System.out.println("Max shore size: " + maxShoreSize);
        System.out.println("Graph size: " + graph.numVertices());
        System.out.println("Graph is connected: " + GraphTests.isConnected(graph));
    }
}