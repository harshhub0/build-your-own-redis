em.currentTimeMillis();
        try {
            RespParserTest.run();
            SkipListTest.run();
            StorageCommandsTest.run();
            TransactionTest.run();
            EndToEndServerTest.run();

            long totalTime = System.currentTimeMillis() - start;
            System.out.println("==========================================================");
            System.out.println("   ALL 5 TEST SUITES PASSED CLEANLY in " + totalTime + " ms!");
            System.out.println("==========================================================");
        } catch (Throwable t) {
            System.err.println("\n[FAIL] Test suite failed with exception:");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
