package com.redis.datastructures;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SkipList implementation modeled after Redis's server-side zskiplist.
 * Supports O(log N) insert, delete, rank lookup, index by rank, and range queries.
 */
public class SkipList {
    public static final int ZSKIPLIST_MAXLEVEL = 32;
    public static final double ZSKIPLIST_P = 0.25;

    public static class Level {
        public Node forward;
        public int span;
    }

    public static class Node {
        public final double score;
        public final String member;
        public Node backward;
        public final Level[] level;

        public Node(int levelCount, double score, String member) {
            this.score = score;
            this.member = member;
            this.level = new Level[levelCount];
            for (int i = 0; i < levelCount; i++) {
                this.level[i] = new Level();
            }
        }
    }

    private final Node header;
    private Node tail;
    private long length;
    private int level;
    private final Random random = new Random();

    public SkipList() {
        this.level = 1;
        this.length = 0;
        this.header = new Node(ZSKIPLIST_MAXLEVEL, 0, null);
        for (int i = 0; i < ZSKIPLIST_MAXLEVEL; i++) {
            this.header.level[i].forward = null;
            this.header.level[i].span = 0;
        }
        this.header.backward = null;
        this.tail = null;
    }

    private int randomLevel() {
        int lvl = 1;
        while ((random.nextDouble() < ZSKIPLIST_P) && lvl < ZSKIPLIST_MAXLEVEL) {
            lvl++;
        }
        return lvl;
    }

    private int compare(double score1, String member1, double score2, String member2) {
        if (score1 < score2) return -1;
        if (score1 > score2) return 1;
        if (member1 == null && member2 == null) return 0;
        if (member1 == null) return -1;
        if (member2 == null) return 1;
        return member1.compareTo(member2);
    }

    public Node insert(double score, String member) {
        Node[] update = new Node[ZSKIPLIST_MAXLEVEL];
        int[] rank = new int[ZSKIPLIST_MAXLEVEL];
        Node x = this.header;

        for (int i = this.level - 1; i >= 0; i--) {
            rank[i] = (i == (this.level - 1)) ? 0 : rank[i + 1];
            while (x.level[i].forward != null &&
                    compare(x.level[i].forward.score, x.level[i].forward.member, score, member) < 0) {
                rank[i] += x.level[i].span;
                x = x.level[i].forward;
            }
            update[i] = x;
        }

        int lvl = randomLevel();
        if (lvl > this.level) {
            for (int i = this.level; i < lvl; i++) {
                rank[i] = 0;
                update[i] = this.header;
                update[i].level[i].span = (int) this.length;
            }
            this.level = lvl;
        }

        x = new Node(lvl, score, member);
        for (int i = 0; i < lvl; i++) {
            x.level[i].forward = update[i].level[i].forward;
            update[i].level[i].forward = x;

            x.level[i].span = update[i].level[i].span - (rank[0] - rank[i]);
            update[i].level[i].span = (rank[0] - rank[i]) + 1;
        }

        for (int i = lvl; i < this.level; i++) {
            update[i].level[i].span++;
        }

        x.backward = (update[0] == this.header) ? null : update[0];
        if (x.level[0].forward != null) {
            x.level[0].forward.backward = x;
        } else {
            this.tail = x;
        }
        this.length++;
        return x;
    }

    private void deleteNode(Node x, Node[] update) {
        for (int i = 0; i < this.level; i++) {
            if (update[i].level[i].forward == x) {
                update[i].level[i].span += x.level[i].span - 1;
                update[i].level[i].forward = x.level[i].forward;
            } else {
                update[i].level[i].span -= 1;
            }
        }
        if (x.level[0].forward != null) {
            x.level[0].forward.backward = x.backward;
        } else {
            this.tail = x.backward;
        }
        while (this.level > 1 && this.header.level[this.level - 1].forward == null) {
            this.level--;
        }
        this.length--;
    }

    public boolean delete(double score, String member) {
        Node[] update = new Node[ZSKIPLIST_MAXLEVEL];
        Node x = this.header;
        for (int i = this.level - 1; i >= 0; i--) {
            while (x.level[i].forward != null &&
                    compare(x.level[i].forward.score, x.level[i].forward.member, score, member) < 0) {
                x = x.level[i].forward;
            }
            update[i] = x;
        }
        x = x.level[0].forward;
        if (x != null && x.score == score && x.member.equals(member)) {
            deleteNode(x, update);
            return true;
        }
        return false;
    }

    public long getRank(double score, String member) {
        long rank = 0;
        Node x = this.header;
        for (int i = this.level - 1; i >= 0; i--) {
            while (x.level[i].forward != null &&
                    compare(x.level[i].forward.score, x.level[i].forward.member, score, member) <= 0) {
                rank += x.level[i].span;
                x = x.level[i].forward;
            }
            if (x != null && x.member != null && x.member.equals(member)) {
                return rank;
            }
        }
        return 0; // Not found
    }

    public Node getNodeByRank(long rank) {
        if (rank < 1 || rank > this.length) {
            return null;
        }
        long traversed = 0;
        Node x = this.header;
        for (int i = this.level - 1; i >= 0; i--) {
            while (x.level[i].forward != null && (traversed + x.level[i].span) <= rank) {
                traversed += x.level[i].span;
                x = x.level[i].forward;
            }
            if (traversed == rank) {
                return x;
            }
        }
        return null;
    }

    public List<ZSetEntry> getRangeByRank(long startRank, long stopRank, boolean reverse) {
        List<ZSetEntry> result = new ArrayList<>();
        if (this.length == 0) return result;

        if (startRank < 1) startRank = 1;
        if (stopRank > this.length) stopRank = this.length;
        if (startRank > stopRank) return result;

        if (!reverse) {
            Node node = getNodeByRank(startRank);
            long count = stopRank - startRank + 1;
            while (node != null && count-- > 0) {
                result.add(new ZSetEntry(node.member, node.score));
                node = node.level[0].forward;
            }
        } else {
            Node node = getNodeByRank(stopRank);
            long count = stopRank - startRank + 1;
            while (node != null && count-- > 0) {
                result.add(new ZSetEntry(node.member, node.score));
                node = node.backward;
            }
        }
        return result;
    }

    public List<ZSetEntry> getRangeByScore(double min, double max, boolean minInc, boolean maxInc,
                                          long offset, long count, boolean reverse) {
        List<ZSetEntry> result = new ArrayList<>();
        if (this.length == 0) return result;

        if (!reverse) {
            Node x = this.header;
            for (int i = this.level - 1; i >= 0; i--) {
                while (x.level[i].forward != null &&
                        (minInc ? x.level[i].forward.score < min : x.level[i].forward.score <= min)) {
                    x = x.level[i].forward;
                }
            }
            x = x.level[0].forward;
            while (x != null && offset > 0) {
                if (maxInc ? x.score > max : x.score >= max) break;
                offset--;
                x = x.level[0].forward;
            }
            while (x != null && count > 0) {
                if (maxInc ? x.score > max : x.score >= max) break;
                result.add(new ZSetEntry(x.member, x.score));
                count--;
                x = x.level[0].forward;
            }
        } else {
            Node x = this.header;
            for (int i = this.level - 1; i >= 0; i--) {
                while (x.level[i].forward != null &&
                        (maxInc ? x.level[i].forward.score <= max : x.level[i].forward.score < max)) {
                    x = x.level[i].forward;
                }
            }
            while (x != null && x != this.header && offset > 0) {
                if (minInc ? x.score < min : x.score <= min) break;
                offset--;
                x = x.backward;
            }
            while (x != null && x != this.header && count > 0) {
                if (minInc ? x.score < min : x.score <= min) break;
                result.add(new ZSetEntry(x.member, x.score));
                count--;
                x = x.backward;
            }
        }
        return result;
    }

    public long countRange(double min, double max, boolean minInc, boolean maxInc) {
        long count = 0;
        Node x = this.header;
        for (int i = this.level - 1; i >= 0; i--) {
            while (x.level[i].forward != null &&
                    (minInc ? x.level[i].forward.score < min : x.level[i].forward.score <= min)) {
                x = x.level[i].forward;
            }
        }
        x = x.level[0].forward;
        while (x != null) {
            if (maxInc ? x.score > max : x.score >= max) break;
            count++;
            x = x.level[0].forward;
        }
        return count;
    }

    public long size() {
        return length;
    }

    public Node getHeader() {
        return header;
    }

    public Node getTail() {
        return tail;
    }
}
