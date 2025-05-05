package org.vspsolver.bls;

import org.graph4j.Graph;
import org.graph4j.util.VertexSet;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.vsp.VertexSeparatorBase;

import java.util.Random;

public class BreakoutLocalSearchVSP extends VertexSeparatorBase {
    // Parameters from the paper
    private static final double L_MIN_FACTOR = 0.05;
    private static final double L_MAX_FACTOR = 0.25;
    private static final double TABU_TENURE_MIN = 0.2;
    private static final double TABU_TENURE_MAX = 0.7;
    private static final double ALPHA_NC = 0.6;
    private static final double ALPHA_C = 0.2;
    private static final int BETA = 4;
    private static final int MAX_HS = 100000;
    private static final int MAX_SLO = 500;

    private final Random random;
    private HashTable hashTable;
    private int lastCycleIter;
    private int currentIter;
    private int averageCycles;
    private int[] z; // Random numbers for hashing
    private int[][] tabuMatrix; // [vertex][0=A,1=B] = iteration when tabu expires
    private VertexSeparator bestSolution;

    public BreakoutLocalSearchVSP(Graph graph) {
        this(graph, 2 * graph.numVertices() / 3);
    }

    public BreakoutLocalSearchVSP(Graph graph, int maxShoreSize) {
        super(graph, maxShoreSize);
        this.random = new Random();
        initialize();
    }

    private void initialize() {
        // Initialize random numbers for hashing
        z = new int[graph.numVertices()];
        for (int i = 0; i < z.length; i++) {
            z[i] = random.nextInt(131072) + 1;
        }

        hashTable = new HashTable(MAX_HS);
        lastCycleIter = 0;
        currentIter = 0;
        averageCycles = 0;
        tabuMatrix = new int[graph.numVertices()][2];
    }

    @Override
    public VertexSeparator getSeparator() {
        VertexSeparator current = generateInitialSolution();
        bestSolution = new VertexSeparator(current);
        int L = (int)(L_MIN_FACTOR * current.separator().size());

        while (!stoppingCondition()) {
            current = descentBasedSearch(current);
            currentIter++;

            L = determineJumpMagnitude(L, current);
            PerturbationType T = determinePerturbationType(current);
            current = perturb(current, L, T);

            // Update best solution
            if (current.separator().size() < bestSolution.separator().size() ||
                    (current.separator().size() == bestSolution.separator().size() &&
                            isMoreBalanced(current, bestSolution))) {
                bestSolution = new VertexSeparator(current);
            }
        }
        return bestSolution;
    }

    private VertexSeparator generateInitialSolution() {
        VertexSeparator solution = new VertexSeparator(graph, maxShoreSize);
        int[] vertices = graph.vertices();
        shuffle(vertices);

        // Assign vertices randomly to shores while respecting max size
        for (int v : vertices) {
            if (random.nextBoolean() && solution.leftShore().size() < maxShoreSize) {
                solution.leftShore().add(v);
            } else if (solution.rightShore().size() < maxShoreSize) {
                solution.rightShore().add(v);
            } else {
                solution.separator().add(v);
            }
        }

        // Repair to ensure no edges between shores
        repairSolution(solution);
        return solution;
    }

    private void shuffle(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    private void repairSolution(VertexSeparator solution) {
        boolean changed;
        do {
            changed = false;
            for (int u : solution.leftShore().vertices()) {
                for (int v : solution.rightShore().vertices()) {
                    if (graph.containsEdge(u, v)) {
                        // Move vertex from larger shore to separator
                        if (solution.leftShore().size() >= solution.rightShore().size()) {
                            solution.leftShore().remove(u);
                            solution.separator().add(u);
                        } else {
                            solution.rightShore().remove(v);
                            solution.separator().add(v);
                        }
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        } while (changed);
    }

    private VertexSeparator descentBasedSearch(VertexSeparator solution) {
        boolean improved;
        int nonImprovingMoves = 0;
        final int MAX_NON_IMPROVING = solution.separator().size() * 2;

        do {
            improved = false;
            Move bestMove = findBestMove(solution);

            if (bestMove != null && bestMove.gain >= 0) {
                if (bestMove.gain > 0) {
                    nonImprovingMoves = 0;
                } else if (++nonImprovingMoves >= MAX_NON_IMPROVING) {
                    break; // Prevent infinite loops
                }
                applyMove(solution, bestMove);
                improved = true;
            }
        } while (improved);

        return solution;
    }

    private Move findBestMove(VertexSeparator solution) {
        Move bestMove = null;
        int bestGain = Integer.MIN_VALUE;

        for (int v : solution.separator().vertices()) {
            // Evaluate move to left shore
            if (solution.leftShore().size() < maxShoreSize) {
                int gain = calculateMoveGain(solution, v, true);
                if (gain > bestGain || (gain == bestGain && random.nextBoolean())) {
                    bestGain = gain;
                    bestMove = new Move(v, true, gain);
                }
            }

            // Evaluate move to right shore
            if (solution.rightShore().size() < maxShoreSize) {
                int gain = calculateMoveGain(solution, v, false);
                if (gain > bestGain || (gain == bestGain && random.nextBoolean())) {
                    bestGain = gain;
                    bestMove = new Move(v, false, gain);
                }
            }
        }
        return bestMove;
    }

    private int calculateMoveGain(VertexSeparator solution, int v, boolean toLeftShore) {
        VertexSet oppositeShore = toLeftShore ? solution.rightShore() : solution.leftShore();
        int conflicts = 0;

        for (int u : graph.neighbors(v)) {
            if (oppositeShore.contains(u)) {
                conflicts++;
            }
        }

        // Gain is reduction in separator size minus new conflicts
        return 1 - conflicts;
    }

    private void applyMove(VertexSeparator solution, Move move) {
        solution.separator().remove(move.vertex);

        if (move.toLeftShore) {
            solution.leftShore().add(move.vertex);
            // Move conflicting vertices from right shore to separator
            for (int u : graph.neighbors(move.vertex)) {
                if (solution.rightShore().contains(u)) {
                    solution.rightShore().remove(u);
                    solution.separator().add(u);
                }
            }
        } else {
            solution.rightShore().add(move.vertex);
            // Move conflicting vertices from left shore to separator
            for (int u : graph.neighbors(move.vertex)) {
                if (solution.leftShore().contains(u)) {
                    solution.leftShore().remove(u);
                    solution.separator().add(u);
                }
            }
        }
    }

    private int determineJumpMagnitude(int L, VertexSeparator solution) {
        int prevVisit = hashTable.checkSolution(solution, currentIter);

        if (prevVisit != -1) { // Cycle detected
            lastCycleIter = currentIter;
            averageCycles = (averageCycles + (currentIter - prevVisit)) / 2;
            L = Math.min(L + 1, (int)(L_MAX_FACTOR * solution.separator().size()));
        }
        else if (currentIter - lastCycleIter > averageCycles * BETA) {
            L = Math.max(L - 1, (int)(L_MIN_FACTOR * solution.separator().size()));
        }
        return L;
    }

    private PerturbationType determinePerturbationType(VertexSeparator solution) {
        int prevVisit = hashTable.getPreviousVisit(solution);
        double e;

        if (prevVisit == -1) {
            e = ALPHA_NC + (currentIter - lastCycleIter) / (double)MAX_SLO;
        } else {
            e = 1 - ALPHA_C - (currentIter - prevVisit) / (double)hashTable.size();
        }

        return random.nextDouble() < e ? PerturbationType.DIRECTED : PerturbationType.RANDOM;
    }

    private VertexSeparator perturb(VertexSeparator solution, int L, PerturbationType type) {
        VertexSeparator perturbed = new VertexSeparator(solution);

        for (int i = 0; i < L && !perturbed.separator().isEmpty(); i++) {
            if (type == PerturbationType.DIRECTED) {
                directedPerturbation(perturbed);
            } else {
                randomPerturbation(perturbed);
            }
        }
        return perturbed;
    }

    private void directedPerturbation(VertexSeparator solution) {
        Move bestMove = null;
        int bestGain = Integer.MIN_VALUE;

        for (int v : solution.separator().vertices()) {
            for (boolean toLeft : new boolean[]{true, false}) {
                VertexSet shore = toLeft ? solution.leftShore() : solution.rightShore();
                if (shore.size() >= maxShoreSize) continue;

                if (!isTabu(v, toLeft, currentIter)) {
                    int gain = calculateMoveGain(solution, v, toLeft);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestMove = new Move(v, toLeft, gain);
                    }
                }
            }
        }

        if (bestMove != null) {
            applyMove(solution, bestMove);
            int tenure = (int)(TABU_TENURE_MIN + random.nextDouble() *
                    (TABU_TENURE_MAX - TABU_TENURE_MIN)) * solution.separator().size();
            setTabu(bestMove.vertex, bestMove.toLeftShore, currentIter + tenure);
        }
    }

    private void randomPerturbation(VertexSeparator solution) {
        if (solution.separator().isEmpty()) return;

        int randomPos = random.nextInt(solution.separator().size());
        int v = solution.separator().vertices()[randomPos];
        boolean toLeft = random.nextBoolean() && solution.leftShore().size() < maxShoreSize;

        if (!toLeft && solution.rightShore().size() >= maxShoreSize) {
            toLeft = true;
        }

        if (toLeft || solution.rightShore().size() < maxShoreSize) {
            solution.separator().remove(v);
            if (toLeft) {
                solution.leftShore().add(v);
                for (int u : graph.neighbors(v)) {
                    if (solution.rightShore().contains(u)) {
                        solution.rightShore().remove(u);
                        solution.separator().add(u);
                    }
                }
            } else {
                solution.rightShore().add(v);
                for (int u : graph.neighbors(v)) {
                    if (solution.leftShore().contains(u)) {
                        solution.leftShore().remove(u);
                        solution.separator().add(u);
                    }
                }
            }
        }
    }

    private boolean isTabu(int vertex, boolean toLeftShore, int iteration) {
        int shoreIndex = toLeftShore ? 0 : 1;
        return tabuMatrix[vertex][shoreIndex] > iteration;
    }

    private void setTabu(int vertex, boolean fromLeftShore, int iteration) {
        int shoreIndex = fromLeftShore ? 0 : 1;
        tabuMatrix[vertex][shoreIndex] = iteration;
    }

    private boolean isMoreBalanced(VertexSeparator s1, VertexSeparator s2) {
        int diff1 = Math.abs(s1.leftShore().size() - s1.rightShore().size());
        int diff2 = Math.abs(s2.leftShore().size() - s2.rightShore().size());
        return diff1 < diff2;
    }

    private boolean stoppingCondition() {
        System.out.println("Current iteration: " + currentIter);
        return currentIter >= 1000 || // Max iterations
                (currentIter - lastCycleIter) > 200; // No improvement for long
    }

    private enum PerturbationType {
        DIRECTED, RANDOM
    }

    private static class Move {
        int vertex;
        boolean toLeftShore;
        int gain;

        Move(int vertex, boolean toLeftShore, int gain) {
            this.vertex = vertex;
            this.toLeftShore = toLeftShore;
            this.gain = gain;
        }
    }

    private class HashTable {
        private final int size;
        private final HashEntry[] table;
        private int count;

        HashTable(int size) {
            this.size = size;
            this.table = new HashEntry[size];
        }

        int checkSolution(VertexSeparator solution, int currentIter) {
            int hash = computeHash(solution);
            HashEntry entry = table[hash];

            while (entry != null) {
                if (entry.solution.equals(solution)) {
                    int prevVisit = entry.lastVisit;
                    entry.lastVisit = currentIter;
                    return prevVisit;
                }
                entry = entry.next;
            }

            // Solution not found, add it
            if (count >= MAX_SLO) {
                removeLeastRecentlyUsed();
            }

            HashEntry newEntry = new HashEntry(solution, currentIter);
            newEntry.next = table[hash];
            table[hash] = newEntry;
            count++;

            return -1;
        }

        int getPreviousVisit(VertexSeparator solution) {
            int hash = computeHash(solution);
            HashEntry entry = table[hash];

            while (entry != null) {
                if (entry.solution.equals(solution)) {
                    return entry.lastVisit;
                }
                entry = entry.next;
            }
            return -1;
        }

        int size() {
            return count;
        }

        private int computeHash(VertexSeparator solution) {
            long sum = 0;
            for (int v : solution.separator().vertices()) {
                sum += z[v];
            }
            return (int)(sum % (size + 1));
        }

        private void removeLeastRecentlyUsed() {
            int minVisit = Integer.MAX_VALUE;
            HashEntry lruEntry = null;
            int lruHash = -1;

            for (int i = 0; i < size; i++) {
                HashEntry entry = table[i];
                HashEntry prev = null;

                while (entry != null) {
                    if (entry.lastVisit < minVisit) {
                        minVisit = entry.lastVisit;
                        lruEntry = entry;
                        lruHash = i;
                    }
                    prev = entry;
                    entry = entry.next;
                }
            }

            if (lruEntry != null) {
                if (table[lruHash] == lruEntry) {
                    table[lruHash] = lruEntry.next;
                } else {
                    HashEntry entry = table[lruHash];
                    while (entry.next != lruEntry) {
                        entry = entry.next;
                    }
                    entry.next = lruEntry.next;
                }
                count--;
            }
        }

        private class HashEntry {
            VertexSeparator solution;
            int lastVisit;
            HashEntry next;

            HashEntry(VertexSeparator solution, int lastVisit) {
                this.solution = new VertexSeparator(solution);
                this.lastVisit = lastVisit;
            }
        }
    }
}