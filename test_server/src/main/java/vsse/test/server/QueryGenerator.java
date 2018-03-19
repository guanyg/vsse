package vsse.test.server;

import org.apache.log4j.Logger;
import vsse.client.ClientContext;
import vsse.model.DocumentDTO;
import vsse.model.RadixTree;
import vsse.proto.Filedesc;
import vsse.proto.Filedesc.Document;
import vsse.server.ServerContext;
import vsse.test.TestCaseDTO;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static vsse.proto.RequestOuterClass.SearchRequest;

public class QueryGenerator implements Runnable {
    private static final Logger logger = Logger.getLogger(QueryGenerator.class);
    private final BlockingQueue<TestCaseDTO> queue;

    private ClientContext clientContext = Context.CLIENT_CONTEXT;
    private ServerContext serverContext = Context.SERVER_CONTEXT;

    private List<String> selectedKeywords;
    private List<String> keywords;

    public QueryGenerator(BlockingQueue<TestCaseDTO> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(this.getClass().getSimpleName());

        // Load documents
        List<Document> documents =
                randomlySelect(
                        Arrays.stream(Objects.requireNonNull(new File(Context.TESTFILE_DIR)
                                .listFiles((dir, name) -> name.endsWith(".bin"))))
                                .map(f -> {
                                    Document ret = null;
                                    try (FileInputStream is = new FileInputStream(f)) {
                                        ret = Document.parseDelimitedFrom(is);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    return ret;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()),
                        15000)
                        .collect(Collectors.toList());
        logger.info("set parameters");

        // Collect keywords
        List<String> keywords = documents
                .stream()
                .flatMap(d -> d.getKeywordsList().stream()
                        .map(Filedesc.Keyword::getKeyword))
                .distinct()
                .collect(Collectors.toList());
        logger.info("total keyword count -> " + keywords.size());

        List<String> selectedKeywords = randomlySelect(keywords, 8000).collect(Collectors.toList());
        keywords.removeAll(selectedKeywords);
        this.keywords = keywords;
        this.selectedKeywords = selectedKeywords;

        // Build RadixTree
        RadixTree tree = clientContext.buildRadixTree(
                documents.stream()
                        .map(DocumentDTO::parse)
                        .collect(Collectors.toList()),
                selectedKeywords);
        serverContext.setTree(tree);

        KeywordGenerator defaultKG = null;
        try {
            defaultKG = new KeywordGenerator(1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create Queries
        KeywordGenerator finalDefaultKG = defaultKG;
        createQueries(
                "[and] test cases",
                tree,
                IntStream.range(2, 11)
                        .mapToObj(i -> IntStream.range(0, 100)
                                .parallel()
                                .mapToObj(j -> clientContext.createQuery(
                                        SearchRequest.MsgCase.AND,
                                        Stream.generate(finalDefaultKG)
                                                .limit(i)
                                                .collect(Collectors.toList()).toArray(new String[0]))
                                ))
                        .flatMap(i -> i)
                        .collect(Collectors.toList()));

        createQueries(
                "[or] test cases",
                tree,
                IntStream.range(1, 11)
                        .mapToObj(i -> IntStream.range(0, 100)
                                .parallel()
                                .mapToObj(j -> clientContext.createQuery(
                                        SearchRequest.MsgCase.OR,
                                        Stream.generate(finalDefaultKG)
                                                .limit(i)
                                                .collect(Collectors.toList()).toArray(new String[0]))
                                ))
                        .flatMap(i -> i)
                        .collect(Collectors.toList()));


        createQueries(
                "[*] test cases",
                tree,
                IntStream.range(1, 11)
                        .mapToObj(i -> {
                            KeywordGenerator _kg;
                            try {
                                _kg = new KeywordGenerator(1, i, true);
                            } catch (Exception e) {
                                return Stream.<SearchRequest>empty();
                            }
                            final KeywordGenerator kg = _kg;
                            return IntStream.range(0, 100)
                                    .parallel()
                                    .mapToObj(j -> clientContext.createQuery(
                                            SearchRequest.MsgCase.STAR,
                                            Stream.generate(kg)
                                                    .filter(k -> k.length() > i)
                                                    .findFirst()
                                                    .get()
                                                    .substring(0, i))
                                    );
                        })
                        .flatMap(i -> i)
                        .collect(Collectors.toList()));

        createQueries(
                "[?] test cases",
                tree,
                IntStream.range(1, 22)
                        .mapToObj(i -> {
                            KeywordGenerator _kg;
                            try {
                                _kg = new KeywordGenerator(1, i, false);
                            } catch (Exception e) {
                                return Stream.<SearchRequest>empty();
                            }
                            final KeywordGenerator kg = _kg;

                            return Stream.generate(kg)
                                    .map(kw -> IntStream.range(
                                            Math.max(0, kw.length() - 11), Math.min(11, kw.length()))
                                            .mapToObj(
                                                    prefixLen -> clientContext.createQuery(
                                                            SearchRequest.MsgCase.Q,
                                                            kw.substring(0, prefixLen),
                                                            kw.substring(prefixLen + 1, kw.length()))))
                                    .limit(100)
                                    .filter(Objects::nonNull)
                                    .flatMap(kw -> kw);
                        })
                        .flatMap(i -> i)
                        .collect(Collectors.toList()));
    }

    private void createQueries(String msg, RadixTree tree, List<SearchRequest> localQueries) {
        logger.info(String.format("%s -> %d", msg, localQueries.size()));
        queue.addAll(localQueries.stream().map(i -> {
            TestCaseDTO ret = new TestCaseDTO();
            ret.setQuery(i);
            ret.setT(tree);
            switch (i.getMsgCase()) {
                case OR:
                    ret.setKeywords(i.getOr().getKeywordsList());
                    ret.setType(SearchRequest.MsgCase.OR);
                    break;
                case AND:
                    ret.setKeywords(i.getAnd().getKeywordsList());
                    ret.setType(SearchRequest.MsgCase.AND);
                    break;
                case STAR:
                    ret.setKeywordHead(i.getStar().getHead());
                    ret.setType(SearchRequest.MsgCase.STAR);
                    break;
                case Q:
                    ret.setKeywordHead(i.getQ().getPart1());
                    ret.setKeywordTail(i.getQ().getPart2());
                    ret.setType(SearchRequest.MsgCase.Q);
                    break;
                case MSG_NOT_SET:
                    break;
            }
            return ret;
        }).collect(Collectors.toList()));
    }

    private <T> Stream<T> randomlySelect(List<T> t, int count) {
        Random rand = new Random();
        return rand.ints(0, t.size())
                .distinct()
                .limit(count)
                .mapToObj(t::get);
    }


    private class KeywordGenerator implements Supplier<String> {

        private final Random r = new Random();
        private final double probability;
        private List<String> lst2;
        private List<String> lst1;

        KeywordGenerator(double d) throws Exception {
            this(d, -1, false);
        }

        KeywordGenerator(double d, int len, boolean range) throws Exception {
            this.probability = d;
            this.lst1 = selectedKeywords;
            this.lst2 = keywords;
            if (len != -1) {
                this.lst1 =
                        lst1.parallelStream()
                                .filter(i -> range ? (i.length() > len) : (i.length() == len))
                                .collect(Collectors.toList());
                this.lst2 =
                        lst2.parallelStream()
                                .filter(i -> range ? (i.length() > len) : (i.length() == len))
                                .collect(Collectors.toList());
                if (lst1.isEmpty() || lst2.isEmpty()) {
                    throw new Exception();
                }
            }
        }

        @Override
        public String get() {
            List<String> lst = Math.random() < probability ? lst1 : lst2;
            return lst.get(r.nextInt(lst.size()));
        }
    }
}
