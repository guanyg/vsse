package vsse.server;

import org.apache.log4j.Logger;
import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.ResponseOuterClass.SearchResponse;

public class ServerContext {
    private static final Logger logger = Logger.getLogger(ServerContext.class);
    // private RadixTree tree;
    private SearchEngine searchEngine;

    public void setTree(RadixTree tree) {
        //this.tree = tree;
        this.searchEngine = new SearchEngine(tree);
    }

    public SearchResponse doSearch(SearchRequest request) throws Exception {
        return searchEngine.search(request);
    }
}
