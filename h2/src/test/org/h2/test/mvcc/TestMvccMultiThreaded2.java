/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.mvcc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.test.TestBase;

/**
 * Additional MVCC (multi version concurrency) test cases.
 */
public class TestMvccMultiThreaded2 extends TestBase {
    
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static final String url = "jdbc:h2:mem:qed;DB_CLOSE_DELAY=-1;MVCC=TRUE;LOCK_TIMEOUT=120000;MULTI_THREADED=TRUE";

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase test = TestBase.createCaller().init();
        test.config.mvcc = true;
        test.config.lockTimeout = 120000;
        test.config.memory = true;
        test.config.multiThreaded = true;
        test.test();
    }

    @Override
    public void test() throws SQLException {
        if (config.networked) {
            return;
        }
        testSelectForUpdateConcurrency();
    }

    private void testSelectForUpdateConcurrency() throws SQLException {
        Connection conn = getConnection(url);
        conn.setAutoCommit(false);

        String sql = "CREATE TABLE test ("
            + "entity_id INTEGER NOT NULL PRIMARY KEY, "
            + "lastUpdated INTEGER NOT NULL)";

        Statement smtm = conn.createStatement();
        smtm.executeUpdate(sql);

        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO test (entity_id, lastUpdated) VALUES (?, ?)");
        ps.setInt(1,  1);
        ps.setInt(2, 100);
        ps.executeUpdate();
        conn.commit();

        for (int i = 0; i < 100; i++)
            new SelectForUpdate().start();

    }

    private class SelectForUpdate extends Thread {

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            boolean done = false;
            while(running.get() && !done) {
                try {
                    Connection conn = getConnection(url);
                    conn.setAutoCommit(false);

                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT * FROM test WHERE entity_id = ? FOR UPDATE");
                    ps.setString(1, "1");
                    ResultSet rs = ps.executeQuery();
                    
                    assertTrue(rs.next());
                    assertTrue(rs.getInt(2) == 100);

                    conn.commit();

                    long now = System.currentTimeMillis();
                    if (now - start  > 1000*60) done = true;
                } catch (SQLException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}
