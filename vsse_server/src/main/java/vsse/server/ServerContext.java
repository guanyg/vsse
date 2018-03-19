package vsse.server;

import org.apache.log4j.Logger;
import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.ResponseOuterClass.Response.State;
import vsse.proto.ResponseOuterClass.SearchResponse;

import java.util.function.Consumer;

public class ServerContext {
    private static final Logger logger = Logger.getLogger(ServerContext.class);
    // private RadixTree tree;
    private SearchEngine searchEngine;

    public void setTree(RadixTree tree) {
        //this.tree = tree;
        this.searchEngine = new SearchEngine(tree);
    }

    protected State getState() {
        if (this.searchEngine != null) {
            return State.READY;
        }
        return State.UP;
    }

    public SearchResponse doSearch(SearchRequest request) throws Exception {
        return searchEngine.search(request);
    }

    public void doSearchAsync(SearchRequest request, Consumer<SearchResponse> consumer) {
        new Thread(() -> {
            try {
                consumer.accept(this.doSearch(request));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
