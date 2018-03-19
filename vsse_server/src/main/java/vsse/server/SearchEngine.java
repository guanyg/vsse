package vsse.server;

import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.roaringbitmap.RoaringBitmap;
import vsse.annotation.Timeit;
import vsse.annotation.Timeit.TYPE;
import vsse.model.DocumentDTO;
import vsse.model.RadixTree;
import vsse.model.RadixTree.GetNodeResp;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.RequestOuterClass.SearchRequest.MsgCase;
import vsse.proto.ResponseOuterClass.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SearchEngine {
    private static final Map<MsgCase, Class<? extends Searcher>> map = new TreeMap<>();
    private static final Logger logger = Logger.getLogger(SearchEngine.class);

    static {
        map.put(MsgCase.AND, AndSearcher.class);
        map.put(MsgCase.OR, OrSearcher.class);
        map.put(MsgCase.STAR, StarSearcher.class);
        map.put(MsgCase.Q, QSearcher.class);
    }

    private final RadixTree tree;

    public SearchEngine(RadixTree tree) {
        this.tree = tree;
    }

    private static FailedTuple buildFailedTuple(GetNodeResp getResp) {
        FailedTuple.Builder fResp =
                FailedTuple.newBuilder()
                        .setLStar(getResp.getL_star())
                        .setHCp(ByteString.copyFrom(getResp.getNode().getHcp()))
                        .setKeyword(getResp.getKeyword());

        List<Integer> intArr =
                Arrays.stream(getResp.getNode().getChildren())
                        .sequential()
                        .filter(Objects::nonNull)
                        .map(i -> (int) i.getLabel())
                        .collect(Collectors.toList());
        fResp.addAllLCl(intArr);
        return fResp.build();
    }

    private static int[] constructDes(List<RadixTree.LeafNode> nodes) {
        return nodes
                .parallelStream()
                .map(RadixTree.LeafNode::getBitmapObject)
                .reduce((l, r) -> RoaringBitmap.and(l, r))
                .get()
                .toArray();
    }

    static List<ByteString> getDocumentByDes(RadixTree tree, IntStream des) {
        return des.mapToObj(tree.getDocmap()::get).map(DocumentDTO::getCipher).map(ByteString::copyFrom).collect(Collectors.toList());
    }

    public SearchResponse search(SearchRequest request) {
//        Searcher s = injector.getInstance(Key.get(Searcher.class, named(request.getMsgCase().name())));
        try {
            Searcher s = map.get(request.getMsgCase()).newInstance();
            s.setParameters(tree, request);
            s.doSearch();
            return s.buildResponse();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        throw new UnsupportedOperationException();
    }

    public interface Searcher {
        void doSearch();

        SearchResponse buildResponse();

        void setParameters(RadixTree tree, SearchRequest request);
    }

    public static class OrSearcher implements Searcher {

        private List<RadixTree.LeafNode> successLst;
        private List<GetNodeResp> failedLst;
        private RadixTree tree;
        private DesBuilder fileCollection;
        private List<String> keywords;

        @Override
        @Timeit(TYPE.SEARCH)
        public void doSearch() {
            successLst = new ArrayList<>();
            failedLst = new ArrayList<>();
            keywords//.parallelStream()
                    .forEach(
                            s -> {
                                try {
                                    GetNodeResp getResp = tree.getNodeByKeyword(s);
                                    if (getResp.isSuccess()) {
                                        RadixTree.LeafNode lNode = (RadixTree.LeafNode) getResp.getNode();
                                        synchronized (successLst) {
                                            successLst.add(lNode);
                                        }
                                    } else {
                                        synchronized (failedLst) {
                                            failedLst.add(getResp);
                                        }
                                    }
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            });
        }

        @Override
        public SearchResponse buildResponse() {
            return SearchResponse.newBuilder()
                    .setOrResponse(
                            OrResponse.newBuilder()
                                    .addAllSuccess(successLst.stream().map(lNode -> OrResponse.SuccessTuple.newBuilder()
                                            .addAllFiles(fileCollection.putDes(lNode.getDes()))
                                            .setHFw(ByteString.copyFrom(lNode.getHcw()))
                                            .setKeyword(lNode.getW())
                                            .build()).collect(Collectors.toList()))
                                    .addAllFailed(failedLst.stream()
                                            .map(SearchEngine::buildFailedTuple)
                                            .collect(Collectors.toList()))
                                    .putAllFiles(fileCollection.getFiles()))
                    .build();
        }

        @Override
        public void setParameters(RadixTree tree, SearchRequest request) {
            this.keywords = request.getOr().getKeywordsList();
            this.tree = tree;
            fileCollection = new DesBuilder(tree);
        }
    }

    public static class AndSearcher implements Searcher {

        private List<String> keywords;
        private RadixTree tree;
        private GetNodeResp failedResponse;
        private int[] des;
        private List<RadixTree.LeafNode> nodes;

        @Override
        @Timeit(TYPE.SEARCH)
        public void doSearch() {
            List<GetNodeResp> getResp = keywords.stream()//.parallelStream()
                    .map(tree::getNodeByKeyword)
                    .collect(Collectors.toList());

            getResp.stream()
                    .filter(i -> !i.isSuccess())
                    .findAny()
                    .ifPresent(this::setFailedResponse);

            if (failedResponse != null) return;

            this.nodes = getResp.parallelStream()
                    .map(i -> (RadixTree.LeafNode) i.getNode())
                    .collect(Collectors.toList());

            des = constructDes(nodes);
        }

        @Override
        public SearchResponse buildResponse() {
            AndResponse.Builder response = AndResponse.newBuilder();
            if (failedResponse != null) {
                response.setFailed(buildFailedTuple(failedResponse));
            } else {
                List<AndSuccess.SuccessTuple> successTuple = nodes.stream()
                        .map(node -> AndSuccess.SuccessTuple.newBuilder()
                                .addAllHashset(
                                        node.getHashset()
                                                .stream()
                                                .map(ByteString::copyFrom)
                                                .collect(Collectors.toList()))
                                .setBitmap(ByteString.copyFrom(node.getBitmap()))
                                .setHbitmap(ByteString.copyFrom(node.getHbw()))
                                .setKeyword(node.getW())
                                .build())
                        .collect(Collectors.toList());
                response.setSuccess(AndSuccess.newBuilder()
                        .addAllSuccess(successTuple)
                        .addAllFiles(getDocumentByDes(tree, Arrays.stream(des))));
            }
            return SearchResponse.newBuilder().setAndResponse(response).build();
        }

        @Override
        public void setParameters(RadixTree tree, SearchRequest request) {
            this.tree = tree;
            this.keywords = request.getAnd().getKeywordsList();
        }

        private void setFailedResponse(GetNodeResp failedResponse) {
            this.failedResponse = failedResponse;
        }
    }

    public static class StarSearcher implements Searcher {

        private String prefix;
        private RadixTree tree;
        private GetNodeResp failedResponse;
        private RadixTree subTree;

        @Override
        @Timeit(TYPE.SEARCH)
        public void doSearch() {
            GetNodeResp getResp = tree.getNodesByPrefix(prefix);
            if (!getResp.isSuccess()) {
                this.failedResponse = getResp;
                return;
            }
            this.subTree = getResp.getNode().getSubtree();
        }

        @Override
        public SearchResponse buildResponse() {
            StarResponse.Builder response = StarResponse.newBuilder();
            if (failedResponse != null) {
                response.setFailed(buildFailedTuple(failedResponse));
            } else {
                DesBuilder fileCollection = new DesBuilder(tree);
                Map<String, ListOfFile> fileIds = subTree.getLeafNodes()
                        .parallelStream()
                        .collect(Collectors.toMap(
                                RadixTree.LeafNode::getW,
                                n -> ListOfFile.newBuilder()
                                        .addAllFiles(fileCollection.putDes(n.getDes()))
                                        .build()));
                response.setSuccess(StarSuccess.newBuilder()
                        .setTree(subTree.serialize(true))
                        .putAllFileIds(fileIds)
                        .putAllFiles(fileCollection.getFiles()));
            }
            return SearchResponse.newBuilder().setStarResponse(response).build();
        }

        @Override
        public void setParameters(RadixTree tree, SearchRequest request) {
            this.prefix = request.getStar().getHead();
            this.tree = tree;
        }
    }

    public static class QSearcher implements Searcher {
        private Map<Character, RadixTree.LeafNode> successLst;
        private Map<Character, GetNodeResp> failedLst;
        private String prefix;
        private String suffix;
        private RadixTree tree;
        private GetNodeResp getNodeResp;

        @Override
        @Timeit(TYPE.SEARCH)
        public void doSearch() {
            successLst = new TreeMap<>();
            failedLst = new TreeMap<>();
            getNodeResp = tree.getNodesByPrefix(prefix);
            if (!getNodeResp.isSuccess()) {
                return;
            }
            RadixTree.Node n = getNodeResp.getNode();

            Arrays.asList(n.getChildren())
                    .stream()//.parallelStream()
                    .filter(Objects::nonNull)
                    .forEach(
                            i -> {
                                RadixTree subTree = i.getSubtree();
                                GetNodeResp getResp2 = subTree.getNodeByKeyword(suffix);
                                if (getResp2.isSuccess()) {
                                    RadixTree.LeafNode lNode = (RadixTree.LeafNode) getResp2.getNode();
                                    synchronized (successLst) {
                                        successLst.put(i.getLabel(), lNode);
                                    }
                                } else {
                                    synchronized (failedLst) {
                                        failedLst.put(i.getLabel(), getResp2);
                                    }
                                }
                            });

        }

        @Override
        public SearchResponse buildResponse() {
            QResponse.Builder response = QResponse.newBuilder();
            if (!getNodeResp.isSuccess()) {
                response.setFailed(buildFailedTuple(getNodeResp));
            } else {
                RadixTree.Node n = getNodeResp.getNode();
                DesBuilder fileCollection = new DesBuilder(tree);
                QSuccess.Builder sResponse = QSuccess.newBuilder()
                        .setLC(n.getLc())
                        .setHCp(ByteString.copyFrom(n.getHcp()))
                        .addAllSuccess(successLst.entrySet()
                                .stream()
                                .map(entry -> QSuccess.QSuccessTuple.newBuilder()
                                        .setHFw(ByteString.copyFrom(entry.getValue().getHcw()))
                                        .addAllFiles(fileCollection.putDes(entry.getValue().getDes()))
                                        .setSubtreeLabel(entry.getKey())
                                        .build())
                                .collect(Collectors.toList()))
                        .addAllFailed(failedLst.entrySet()
                                .stream()
                                .map(entry -> QSuccess.QFailedTuple.newBuilder()
                                        .setSubtreeLabel(entry.getKey())
                                        .setLStar(getNodeResp.getL_star() + entry.getValue().getL_star() + 1)
                                        .setHCp(ByteString.copyFrom(entry.getValue().getNode().getHcp()))
                                        .addAllLCl(
                                                entry.getValue()
                                                        .getNode()
                                                        .getLc()
                                                        .chars()
                                                        .boxed()
                                                        .collect(Collectors.toList()))
                                        .build()
                                ).collect(Collectors.toList()))
                        .putAllFiles(fileCollection.getFiles());
                response.setSuccess(sResponse);
            }
            return SearchResponse.newBuilder().setQResponse(response).build();
        }

        @Override
        public void setParameters(RadixTree tree, SearchRequest request) {
            this.tree = tree;
            this.prefix = request.getQ().getPart1();
            this.suffix = request.getQ().getPart2();
        }
    }

    private static class DesBuilder {
        private final Set<Integer> files = new TreeSet<>();
        private RadixTree tree;

        DesBuilder(RadixTree tree) {
            this.tree = tree;
        }

        public List<Integer> putDes(int[] des) {
            List<Integer> ret =
                    Arrays.stream(des).boxed().collect(Collectors.toList());
            synchronized (files) {
                files.addAll(ret);
            }
            return ret;
        }

        public List<Integer> putDes(String des) {
            List<Integer> ret =
                    Arrays.stream(des.split(","))
                            .sequential()
                            .map(i -> "".equals(i) ? Integer.valueOf(-1) : Integer.valueOf(i))
                            .filter(i -> i != -1)
                            .collect(Collectors.toList());
            synchronized (files) {
                files.addAll(ret);
            }
            return ret;
        }

        public Map<Integer, ByteString> getFiles() {
            return files
                    .parallelStream()
                    .collect(Collectors.toMap(i -> i,
                            i -> ByteString.copyFrom(tree.getDocmap().get(i).getCipher())));
        }
    }
}
