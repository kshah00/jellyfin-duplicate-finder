package com.jellydupes.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pairwise precision / recall of predicted duplicate groups against ground-truth labels
 * (dup_group / dup_kind columns written by the synthetic generator).
 *
 * <p>A "pair" is two records that belong together. Precision = correct predicted pairs / predicted
 * pairs; recall = correct predicted pairs / true pairs. Per-kind recall counts planted variants
 * that landed in the same predicted group as their original.
 */
public final class AccuracyEvaluator {

    public record Accuracy(double precision, double recall, double f1, long truePairs, long predictedPairs,
                           long correctPairs, int exactVariants, int exactFound, double exactRecall,
                           int nearVariants, int nearFound, double nearRecall) {
    }

    /** Returns null when any record lacks a ground-truth label. */
    public static Accuracy evaluate(DuplicateDetector.Result result) {
        List<MediaItem> items = result.items();
        for (MediaItem m : items) {
            if (m.truthGroup() == null) {
                return null;
            }
        }
        Map<Integer, Integer> predicted = new HashMap<>();
        for (DuplicateDetector.DuplicateGroup g : result.groups()) {
            for (MediaItem m : g.items()) {
                predicted.put(m.id(), g.id());
            }
        }
        Map<String, Long> truthSizes = new HashMap<>();
        Map<Integer, Long> predSizes = new HashMap<>();
        Map<String, Long> cells = new HashMap<>();
        Map<String, Integer> originalPrediction = new HashMap<>();
        for (MediaItem m : items) {
            int p = predictedGroup(predicted, m);
            truthSizes.merge(m.truthGroup(), 1L, Long::sum);
            predSizes.merge(p, 1L, Long::sum);
            cells.merge(p + "#" + m.truthGroup(), 1L, Long::sum);
            if ("original".equals(m.truthKind())) {
                originalPrediction.put(m.truthGroup(), p);
            }
        }
        long truePairs = truthSizes.values().stream().mapToLong(AccuracyEvaluator::pairs).sum();
        long predictedPairs = predSizes.values().stream().mapToLong(AccuracyEvaluator::pairs).sum();
        long correctPairs = cells.values().stream().mapToLong(AccuracyEvaluator::pairs).sum();

        int exact = 0;
        int exactFound = 0;
        int near = 0;
        int nearFound = 0;
        for (MediaItem m : items) {
            Integer orig = originalPrediction.get(m.truthGroup());
            boolean found = orig != null && orig == predictedGroup(predicted, m);
            if ("exact".equals(m.truthKind())) {
                exact++;
                exactFound += found ? 1 : 0;
            } else if ("near".equals(m.truthKind())) {
                near++;
                nearFound += found ? 1 : 0;
            }
        }
        double precision = predictedPairs == 0 ? 1.0 : (double) correctPairs / predictedPairs;
        double recall = truePairs == 0 ? 1.0 : (double) correctPairs / truePairs;
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Accuracy(precision, recall, f1, truePairs, predictedPairs, correctPairs,
                exact, exactFound, exact == 0 ? 1.0 : (double) exactFound / exact,
                near, nearFound, near == 0 ? 1.0 : (double) nearFound / near);
    }

    private static int predictedGroup(Map<Integer, Integer> predicted, MediaItem m) {
        Integer p = predicted.get(m.id());
        return p != null ? p : -1 - m.id(); // ungrouped records are singletons
    }

    private static long pairs(long n) {
        return n * (n - 1) / 2;
    }
}
