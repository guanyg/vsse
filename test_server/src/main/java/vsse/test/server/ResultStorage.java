package vsse.test.server;

import vsse.proto.RequestOuterClass.SearchRequest.MsgCase;
import vsse.test.TestCaseDTO;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResultStorage implements Runnable {
    private static final String RUN =
            new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss_z").format(new Date());
    private static final int BATCH_SIZE = 10;
    private BlockingQueue<TestCaseDTO> closedTestCase;
    //  private Statement stat = null;
    private PreparedStatement insertPS;
    private boolean saveTree = true;

    public ResultStorage(BlockingQueue<TestCaseDTO> closedTestCase2) throws Exception {
        this.closedTestCase = closedTestCase2;

        Properties prop = new Properties();
        prop.load(new FileInputStream(Context.getParameter(Context.P_DBCONF)));

        Class.forName(prop.getProperty("driverClass"));
        Connection conn = DriverManager.getConnection(prop.getProperty("url"),
                                                      prop.getProperty("username"),
                                                      prop.getProperty("password"));
        insertPS =
                conn.prepareStatement(
                        "INSERT INTO result"
                                + " (tcid, t_keyword_cnt, t_document_cnt, radix_tree," +
                                " keyword, keyword_cnt, prefix_len, tail_len," +
                                " type, query, response, search_time," +
                                " verify_time_pc, verify_time_and, run, node_cnt)"
                                + " VALUES(?,?,?,?, ?,?,?,?, ?,?,?,?, ?,?,?,?)");
    }

    private static void putKeywordData(PreparedStatement insertPS2, TestCaseDTO tc)
            throws SQLException {
        String keyword = null;
        int count = 0;
        int headLen = 0;
        int tailLen = 0;
        switch (tc.getType()) {
            case AND:
            case OR:
                keyword = tc.getKeywords().stream().collect(Collectors.joining(","));
                count = tc.getKeywords().size();
                break;
            case Q:
                tailLen = tc.getKeywordTail().length();
                headLen = tc.getKeywordHead().length();
                keyword = tc.getKeywordHead() + "?" + tc.getKeywordTail();
                break;
            case STAR:
                keyword = tc.getKeywordHead() + "*";
                headLen = tc.getKeywordHead().length();
                break;
            default:
        }
        insertPS2.setInt(8, tailLen);
        insertPS2.setInt(7, headLen);
        insertPS2.setInt(6, count);
        insertPS2.setString(5, keyword);
    }

    private void flush() {
        try {
            insertPS.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName(this.getClass().getSimpleName());
        TestCaseDTO tc = null;
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(this::flush, 30, 30, TimeUnit.SECONDS);
        int i = 1;
        while (true) {
            try {
                tc = closedTestCase.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                int idx = 1;
                insertPS.setInt(1, tc.getCaseId());
                insertPS.setInt(2, tc.getKeywordCount());
                insertPS.setInt(3, tc.getDocumentCount());
                insertPS.setBinaryStream(4, new ByteArrayInputStream(new byte[0]));
                if (saveTree) {
                    TestCaseDTO _tc = tc;
                    new Thread(() -> {
                        try (FileOutputStream fos = new FileOutputStream(RUN + "_keywords.txt")) {
                            for (String keyword : _tc.getT().getKeywords()) {
                                fos.write(keyword.getBytes());
                                fos.write('\n');
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    saveTree = false;
                }

                putKeywordData(insertPS, tc);

                insertPS.setString(9, tc.getType().name());
                insertPS.setBinaryStream(10, new ByteArrayInputStream(tc.getQuery().toByteArray()));
                //        insertPS.setBinaryStream(11, new ByteArrayInputStream(tc.getResp().toByteArray()));
                insertPS.setBinaryStream(11, new ByteArrayInputStream(new byte[0]));
                insertPS.setLong(12, tc.getSearchTime());
                insertPS.setLong(13, tc.getVerifyTimePC());
                insertPS.setLong(14, tc.getVerifyTimeAND());
                insertPS.setString(15, RUN);
                int nodeCount = 0;
                if (tc.getType() == MsgCase.STAR) {
                    nodeCount = tc.getResp().getStarResponse().getSuccess().getTree().getNodesCount();
                }
                insertPS.setInt(16, nodeCount);
                insertPS.addBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            if (i++ % BATCH_SIZE == 0) {
                this.flush();
            }
        }
    }
}
