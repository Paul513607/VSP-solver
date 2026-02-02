package org.vspsolver;

import org.graph4j.Graph;
import org.graph4j.util.Pair;
import org.graph4j.vsp.BacktrackVertexSeparator;
import org.graph4j.vsp.GreedyVertexSeparator;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.vsp.VertexSeparatorBase;
import org.vspsolver.lsvsp.BlsVertexSeparatorAlgorithm;
import org.vspsolver.lsvsp.IlsVertexSeparatorAlgorithm;
import org.vspsolver.prvsp.PrVertexSeparatorAlgorithm;
import org.vspsolver.prvsp.EprVertexSeparatorAlgorithm;
import org.vspsolver.util.InstanceParser;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public final class Main {

    private enum AlgName { GREEDY, BKT, BLS, ILS, PR, EPR }

    public static void main(String[] args) throws IOException {
        String instancesDir = "/home/paul/vsp_instances";
        String outDir = "benchmark_out2";
        String startFromFile = "jinkao.hao_BLSVSP_G32.gz";

        int runsPerInstance = 20;
        long timeLimitMillis = 10_000;

        List<Path> files = listInstanceFiles(instancesDir);

        int instanceIndex = 0;
        for (Path file : files) {
            String fileName = normalizeFileName(file.getFileName().toString());
            String target = normalizeFileName(startFromFile);

            if (target != null && !target.isEmpty()) {
                if (!fileName.equals(target)) {
                    System.out.println("Skipping until '" + target + "': saw '" + fileName + "'");
                    instanceIndex++;
                    continue;
                } else {
                    System.out.println("Matched startFromFile '" + target + "'. Starting here.");
                    startFromFile = null;
                }
            }

            overwriteCurrentRunFile(fileName);
            instanceIndex++;
            String instanceName = file.getFileName().toString();

            Pair<Graph, Integer> graphMaxShoreSizePair;
            try {
                graphMaxShoreSizePair = InstanceParser.loadGraphFromGZ(file.toString());
            } catch (Exception e) {
                System.out.println("Skipping (load error): " + instanceName + " -> " + e.getMessage());
                continue;
            }

            Graph graph = graphMaxShoreSizePair.first();
            int maxShoreSize = graphMaxShoreSizePair.second();
            int n = graph.numVertices();

            System.out.println();
            System.out.println("============================================================");
            System.out.println("[" + instanceIndex + "/" + files.size() + "] " +
                    instanceName + " | n=" + n + " | maxShoreSize=" + maxShoreSize);
            System.out.println("============================================================");
            System.out.println();

            List<AlgName> algorithmsToRun = List.of(
                    // AlgName.GREEDY,
                    // AlgName.BKT,
                    AlgName.BLS,
                    AlgName.ILS,
                    AlgName.PR,
                    AlgName.EPR
            );

            String csvPath = buildInstanceCsvPath(outDir, instanceName);

            try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath))) {
                writeCsvHeader(pw);

                // for (AlgName alg : AlgName.values()) {
                for (AlgName alg : algorithmsToRun) {
                    System.out.println("---- Algorithm: " + alg.name() + " ----");

                    for (int run = 1; run <= runsPerInstance; run++) {
                        long seed = System.nanoTime();

                        System.out.println("  Run " + run + "/" + runsPerInstance +
                                " | seed=" + seed +
                                " | limit=" + timeLimitMillis + "ms" +
                                " | starting...");

                        RunResult rr = runOnce(alg, graph, maxShoreSize, timeLimitMillis, seed);

                        System.out.println("  Done  | time=" + rr.timeMs + "ms" +
                                " | sep=" + rr.sepSize +
                                " | A=" + rr.leftSize +
                                " | B=" + rr.rightSize +
                                " | valid=" + rr.valid +
                                " | bestTime=" + rr.bestTimeMs + "ms" +
                                (rr.error == null ? "" : " | error=" + rr.error));

                        pw.println(toCsvRow(instanceName, n, maxShoreSize, alg.name(), run, seed, rr));
                        pw.flush();

                        System.out.println();
                    }

                    System.out.println();
                }
            }

            System.out.println("Wrote per-instance CSV: " + csvPath);
        }

        System.out.println("Done. Output folder: " + outDir);
    }

    private static List<Path> listInstanceFiles(String dir) throws IOException {
        try (var stream = Files.list(Paths.get(dir))) {
            return stream
                    .filter(p -> Files.isRegularFile(p))
                    .filter(p -> p.getFileName().toString().endsWith(".gz"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private static void writeCsvHeader(PrintWriter printWriter) {
        printWriter.println(String.join(",",
                "instance",
                "n",
                "maxShoreSize",
                "algorithm",
                "run",
                "seed",
                "time_ms",
                "sep_size",
                "left_size",
                "right_size",
                "valid",
                "best_time_ms",
                "error"
        ));
    }

    private static String toCsvRow(String instance, int n, int maxShoreSize, String alg, int run, long seed, RunResult runResult) {
        String err = runResult.error == null ? "" : runResult.error.replace(",", ";").replace("\n", " ").replace("\r", " ");
        return String.join(",",
                instance,
                Integer.toString(n),
                Integer.toString(maxShoreSize),
                alg,
                Integer.toString(run),
                Long.toString(seed),
                Long.toString(runResult.timeMs),
                Integer.toString(runResult.sepSize),
                Integer.toString(runResult.leftSize),
                Integer.toString(runResult.rightSize),
                Boolean.toString(runResult.valid),
                Long.toString(runResult.bestTimeMs),
                err
        );
    }

    private static RunResult runOnce(AlgName alg, Graph graph, int maxShoreSize, long timeLimitMillis, long seed) {
        long startTime = System.nanoTime();
        try {
            VertexSeparator sep;
            VertexSeparatorBase vspAlg;
            long bestFoundAtMs = -1;

            switch (alg) {
                case GREEDY -> {
                    vspAlg = new GreedyVertexSeparator(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                }
                case BKT -> {
                    vspAlg = new BacktrackVertexSeparator(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                }
                case BLS -> {
                    vspAlg = new BlsVertexSeparatorAlgorithm(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                    bestFoundAtMs = ((BlsVertexSeparatorAlgorithm) vspAlg).getBestFoundAtMs();
                }
                case ILS -> {
                    vspAlg = new IlsVertexSeparatorAlgorithm(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                    bestFoundAtMs = ((IlsVertexSeparatorAlgorithm) vspAlg).getBestFoundAtMs();
                }
                case PR -> {
                    vspAlg = new PrVertexSeparatorAlgorithm(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                    bestFoundAtMs = ((PrVertexSeparatorAlgorithm) vspAlg).getBestFoundAtMs();
                }
                case EPR -> {
                    vspAlg = new EprVertexSeparatorAlgorithm(graph.copy(), maxShoreSize);
                    sep = vspAlg.getSeparator();
                    bestFoundAtMs = ((EprVertexSeparatorAlgorithm) vspAlg).getBestFoundAtMs();
                }
                default -> throw new IllegalStateException("Unknown algorithm: " + alg);
            }

            long endTime = System.nanoTime();
            long elapsedMs = (endTime - startTime) / 1_000_000L;

            boolean valid = sep.isValid();
            return RunResult.ok(elapsedMs, sep.separator().size(), sep.leftShore().size(), sep.rightShore().size(),
                    valid, bestFoundAtMs);

        } catch (Throwable e) {
            long t1 = System.nanoTime();
            long ms = (t1 - startTime) / 1_000_000L;
            return RunResult.fail(ms, e.toString());
        }
    }

    private static String buildInstanceCsvPath(String outDir, String instanceName) {
        String safe = instanceName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Paths.get(outDir, "benchmark_results__" + safe + ".csv").toString();
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void overwriteCurrentRunFile(String fileName) {
        String pathToCurrentRunFile = "current_run.txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(pathToCurrentRunFile))) {
            pw.println(fileName);
        } catch (IOException e) {
            System.out.println("Warning: could not write current run file: " + e.getMessage());
        }
    }

    public static void runAndPrintSeparator(long timeLimitMillis, long seed) {
        String instance = "mat.L125.ash608.gz";
        Pair<Graph, Integer> graphMaxShoreSizePair;
        try {
            graphMaxShoreSizePair = InstanceParser.loadGraphFromGZ("/home/paul/vsp_instances/" + instance);
        } catch (Exception e) {
            throw new RuntimeException("Could not load instance: " + instance, e);
        }

        Graph graph = graphMaxShoreSizePair.first();
        int maxShoreSize = graphMaxShoreSizePair.second();

        BlsVertexSeparatorAlgorithm alg = new BlsVertexSeparatorAlgorithm(graph, maxShoreSize);
        alg.timeLimitMillis = timeLimitMillis;
        alg.seed = seed;

        long startTime = System.nanoTime();
        VertexSeparator sep = alg.getSeparator();
        long endTime = System.nanoTime();
        long elapsedMs = (endTime - startTime) / 1_000_000L;
        System.out.println("Instance: " + instance);
        System.out.println("Time: " + elapsedMs + " ms");
        System.out.println("Left shore:" + sep.leftShore());
        System.out.println("Right shore:" + sep.rightShore());
        System.out.println("Separator:" + sep.separator());
        System.out.println("Graph size" + graph.numVertices());
        System.out.println("Edges:");
        for (int v : graph.vertices()) {
            for (int w : graph.neighbors(v)) {
                if (v < w) {
                    System.out.println("" + v + " " + w);
                }
            }
        }

    }
}
