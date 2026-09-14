package com.redis.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorted Set implementation combining a Hash Table (for O(1) score lookup)
 * and a SkipList (for O(log N) element ranking and range queries).
 */
public class ZSet {
    private final Map<String, Double> dict;
    private final SkipList skiplist;

    public ZSet() {
        this.dict = new HashMap<>();
        this.skiplist = new SkipList();
    }

    public synchronized boolean add(double score, String member) {
        Double oldScore = dict.get(member);
        if (oldScore != null) {
            if (oldScore != score) {
                skiplist.delete(oldScore, member);
                skiplist.insert(score, member);
                dict.put(member, score);
            }
            return false; // Existing element updated
        } else {
            dict.put(member, score);
            skiplist.insert(score, member);
            return true; // New element added
        }
    }

    public synchronized boolean remove(String member) {
        Double score = dict.remove(member);
        if (score != null) {
            skiplist.delete(score, member);
            return true;
        }
        return false;
    }

    public synchronized Double getScore(String member) {
        return dict.get(member);
    }

    public synchronized double incrBy(double delta, String member) {
        Double oldScore = dict.get(member);
        double newScore = (oldScore != null ? oldScore : 0.0) + delta;
        if (oldScore != null) {
            skiplist.delete(oldScore, member);
        }
        dict.put(member, newScore);
        skiplist.insert(newScore, member);
        return newScore;
    }

    public synchronized long getRank(String member, boolean reverse) {
        Double score = dict.get(member);
        if (score == null) {
            return -1;
        }
        long rank = skiplist.getRank(score, member);
        if (rank == 0) {
            return -1;
        }
        if (!reverse) {
            return rank - 1; // 0-based
        } else {
            return skiplist.size() - rank; // 0-based reverse rank
        }
    }

    public synchronized List<ZSetEntry> getRange(long start, long stop, boolean reverse) {
        long size = skiplist.size();
        if (size == 0) return new ArrayList<>();

        if (start < 0) start = size + start;
        if (stop < 0) stop = size + stop;

        if (start < 0) start = 0;
        if (stop >= size) stop = size - 1;

        if (start > stop || start >= size) {
            return new ArrayList<>();
        }

        // Convert 0-based to 1-based ranks
        return skiplist.getRangeByRank(start + 1, stop + 1, reverse);
    }

    public synchronized List<ZSetEntry> getRangeByScore(double min, double max, boolean minInc, boolean maxInc,
                                                        long offset, long count, boolean reverse) {
        return skiplist.getRangeByScore(min, max, minInc, maxInc, offset, count, reverse);
    }

    public synchronized long countRange(double min, double max, boolean minInc, boolean maxInc) {
        return skiplist.countRange(min, max, minInc, maxInc);
    }

    public synchronized List<ZSetEntry> popMin(long count) {
        List<ZSetEntry> result = new ArrayList<>();
        while (count > 0 && skiplist.size() > 0) {
            SkipList.Node first = skiplist.getHeader().level[0].forward;
            if (first == null) break;
            result.add(new ZSetEntry(first.member, first.score));
            remove(first.member);
            count--;
        }
        return result;
    }

    public synchronized List<ZSetEntry> popMax(long count) {
        List<ZSetEntry> result = new ArrayList<>();
        while (count > 0 && skiplist.size() > 0) {
            SkipList.Node last = skiplist.getTail();
            if (last == null) break;
            result.add(new ZSetEntry(last.member, last.score));
            remove(last.member);
            count--;
        }
        return result;
    }

    public synchronized int size() {
        return dict.size();
    }

    public synchronized Map<String, Double> getEntries() {
        return new HashMap<>(dict);
    }
}
