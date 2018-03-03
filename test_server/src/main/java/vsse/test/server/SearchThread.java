package vsse.test.server;

import org.apache.log4j.Logger;
import vsse.server.ServerContext;
import vsse.test.TestCaseDTO;

import java.util.concurrent.BlockingQueue;

public class SearchThread implements Runnable {
    private static final Logger logger = Logger.getLogger(SearchThread.class);

    private final BlockingQueue<TestCaseDTO> queries;
    private final BlockingQueue<TestCaseDTO> searchResult;

    private ServerContext serverContext = Context.getParameter(Context.P_SERVER_CONTEXT);
    private TestCaseDTO tc;

    public SearchThread(BlockingQueue<TestCaseDTO> queries, BlockingQueue<TestCaseDTO> searchResult) {
        this.queries = queries;
        this.searchResult = searchResult;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(this.getClass().getSimpleName());

        while (true) {
            try {
                tc = queries.take();
                tc.setResp(serverContext.doSearch(tc.getQuery()));
                searchResult.add(tc);
                logger.info(String.format("append tc.%d searchTime=%d", tc.getCaseId(), tc.getSearchTime()));
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }

    }

    public TestCaseDTO getTc() {
        return tc;
    }
}
