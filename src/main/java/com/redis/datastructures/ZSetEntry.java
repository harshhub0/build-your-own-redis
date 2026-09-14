package com.redis.datastructures;

import java.util.Objects;

/**
 * Represents a member and score pair in a Sorted Set (ZSet).
 */
public class ZSetEntry {
    private final String member;
    private final double score;

    public ZSetEntry(String member, double score) {
        this.member = member;
        this.score = score;
    }

    public String getMember() {
        return member;
    }

    public double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZSetEntry zSetEntry = (ZSetEntry) o;
        return Double.compare(zSetEntry.score, score) == 0 &&
               Objects.equals(member, zSetEntry.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(member, score);
    }

    @Override
    public String toString() {
        return "ZSetEntry{" +
               "member='" + member + '\'' +
               ", score=" + score +
               '}';
    }
}
