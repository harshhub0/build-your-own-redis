package com.redis.test;

import com.redis.datastructures.SkipList;
import com.redis.datastructures.ZSet;
import com.redis.datastructures.ZSetEntry;

import java.util.List;

public class SkipListTest {

    public static void run() {
        System.out.println("[Test] Running SkipList & ZSet tests...");
        testBasicInsertAndRank();
        testDuplicateScores();
        testZSetOperations();
        testRangeByScore();
        System.out.println("[Test] SkipList & ZSet tests passed successfully!\n");
    }

    private static void testBasicInsertAndRank() {
        SkipList sl = new SkipList();
        sl.insert(10.0, "Alice");
        sl.insert(30.0, "Charlie");
        sl.insert(20.0, "Bob");

        assert sl.size() == 3 : "Size should be 3";
        assert sl.getRank(10.0, "Alice") == 1 : "Alice rank should be 1";
        assert sl.getRank(20.0, "Bob") == 2 : "Bob rank should be 2";
        assert sl.getRank(30.0, "Charlie") == 3 : "Charlie rank should be 3";

        // Test range by rank
        List<ZSetEntry> range = sl.getRangeByRank(1, 2, false);
        assert range.size() == 2 : "Range size should be 2";
        assert range.get(0).getMember().equals("Alice") : "First should be Alice";
        assert range.get(1).getMember().equals("Bob") : "Second should be Bob";

        // Reverse range
        List<ZSetEntry> revRange = sl.getRangeByRank(1, 3, true);
        assert revRange.get(0).getMember().equals("Charlie") : "First rev should be Charlie";
        assert revRange.get(2).getMember().equals("Alice") : "Last rev should be Alice";

        // Test deletion
        boolean deleted = sl.delete(20.0, "Bob");
        assert deleted : "Bob should be deleted";
        assert sl.size() == 2 : "Size should now be 2";
        assert sl.getRank(30.0, "Charlie") == 2 : "Charlie rank should now be 2";
    }

    private static void testDuplicateScores() {
        SkipList sl = new SkipList();
        sl.insert(100.0, "Zeta");
        sl.insert(100.0, "Alpha");
        sl.insert(100.0, "Beta");

        // Lexicographical ordering for equal scores: Alpha (1), Beta (2), Zeta (3)
        assert sl.getRank(100.0, "Alpha") == 1 : "Alpha should be rank 1";
        assert sl.getRank(100.0, "Beta") == 2 : "Beta should be rank 2";
        assert sl.getRank(100.0, "Zeta") == 3 : "Zeta should be rank 3";
    }

    private static void testZSetOperations() {
        ZSet zset = new ZSet();
        zset.add(10.0, "one");
        zset.add(20.0, "two");
        zset.add(30.0, "three");

        assert zset.size() == 3;
        assert Double.compare(zset.getScore("two"), 20.0) == 0;
        assert zset.getRank("two", false) == 1; // 0-based rank

        // Update score
        zset.add(5.0, "two");
        assert zset.getRank("two", false) == 0; // Now lowest score

        // IncrBy
        double newScore = zset.incrBy(50.0, "two");
        assert Double.compare(newScore, 55.0) == 0;
        assert zset.getRank("two", false) == 2; // Now highest score
    }

    private static void testRangeByScore() {
        ZSet zset = new ZSet();
        zset.add(10.0, "m1");
        zset.add(20.0, "m2");
        zset.add(30.0, "m3");
        zset.add(40.0, "m4");

        List<ZSetEntry> entries = zset.getRangeByScore(15.0, 35.0, true, true, 0, 10, false);
        assert entries.size() == 2;
        assert entries.get(0).getMember().equals("m2");
        assert entries.get(1).getMember().equals("m3");

        long count = zset.countRange(15.0, 35.0, true, true);
        assert count == 2;
    }
}
