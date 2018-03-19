package vsse.client;

import com.google.protobuf.ByteString;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import vsse.annotation.Timeit;
import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.RequestOuterClass.SearchRequest.MsgCase;
import vsse.proto.ResponseOuterClass.*;
import vsse.security.SecurityUtil;
import vsse.util.Counter;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;


public class Verifiers {
    private static final Map<MsgCase, Class<? extends Verifier>> map = new TreeMap<>();

    static {
        map.put(MsgCase.AND, AndVerifier.class);
        map.put(MsgCase.OR, OrVerifier.class);
        map.put(MsgCase.STAR, StarVerifier.class);
        map.put(MsgCase.Q, QVerifier.class);
    }

    private final SecurityUtil su;

    public Verifiers(SecurityUtil su) {
        this.su = su;
    }

    private static boolean failedTupleVerify1(SecurityUtil su, FailedTuple t) {
        String keyword = t.getKeyword();
        String prefix = "$" + keyword.substring(0, t.getLStar());
        StringBuilder childrenSet = new StringBuilder();
        t.getLClList().forEach(i -> childrenSet.append(Character.toLowerCase((char) i.intValue())));

        byte[] hcp = su.HMAC(prefix, childrenSet.toString());
        byte[] hcp2 = t.getHCp().toByteArray();
        if (!Arrays.equals(hcp, hcp2)) return false;

        if (t.getLStar() < keyword.length()) {
            return !t.getLClList().contains(keyword.charAt(t.getLStar()));
        }
        return !t.getLClList().contains('#');
    }

    private static boolean failedTupleVerify2(SecurityUtil su, FailedTuple t) {
        String keyword = t.getKeyword();
        String prefix = "$" + keyword.substring(0, t.getLStar());
        StringBuilder childrenSet = new StringBuilder();
        t.getLClList().forEach(i -> childrenSet.append(Character.toLowerCase((char) i.intValue())));

        byte[] hcp = su.HMAC(prefix, childrenSet.toString());
        byte[] hcp2 = t.getHCp().toByteArray();
        return Arrays.equals(hcp, hcp2)
                && t.getLStar() < keyword.length()
                && !t.getLClList().contains(keyword.charAt(t.getLStar()));
    }

    private Verifier getVerifier(MsgCase type) throws IllegalAccessException, InstantiationException {
        return map.get(type).newInstance();
    }

    public boolean verify(SearchRequest request, SearchResponse response) {
        try {
            Verifier verifier = getVerifier(request.getMsgCase());
            verifier.setParameters(su, request, response);
            return verifier.verify();
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
        return false;
    }

    protected interface Verifier {
        boolean verify();

        void setParameters(SecurityUtil su, SearchRequest request, SearchResponse response);
    }

    public static class OrVerifier implements Verifier {

        private OrResponse response;
        private SecurityUtil su;

        @Override
        @Timeit(Timeit.TYPE.VERIFY)
        public boolean verify() {
            Counter passed = new Counter();
            response.getSuccessList().forEach(i -> {
                List<byte[]> sb = i.getFilesList().stream()
                        .map(response.getFilesMap()::get)
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toList());

                byte[] Hfw = i.getHFw().toByteArray();
                byte[] Hfw2 = su.HMAC(sb, i.getKeyword().getBytes());
                if (Arrays.equals(Hfw, Hfw2)) {
                    passed.inc();
                }
            });

            response.getFailedList()
                    .forEach(i -> {
                        if (failedTupleVerify1(su, i)) {
                            passed.inc();
                        }
                    });

            return passed.peek() == (response.getSuccessCount() + response.getFailedCount());
        }

        @Override
        public void setParameters(SecurityUtil su, SearchRequest request, SearchResponse response) {
            this.su = su;
            this.response = response.getOrResponse();
        }
    }

    public static class AndVerifier implements Verifier {

        private SecurityUtil su;
        private AndResponse response;

        @Override
        @Timeit(Timeit.TYPE.VERIFY)
        public boolean verify() {
            switch (response.getMsgCase()) {
                case FAILED:
                    return failedTupleVerify1(su, response.getFailed());

                case SUCCESS:
                    AndSuccess sResponse = response.getSuccess();

                    boolean failed = sResponse.getSuccessList().stream()
                            .anyMatch(i -> {
                                byte[] hbitmap2 = su.HMAC(i.getBitmap().toByteArray(), i.getKeyword().getBytes());
                                return !Arrays.equals(i.getHbitmap().toByteArray(), hbitmap2);
                            });
                    if (failed)
                        return false;
                    int fileCnt1 = sResponse.getSuccessList().parallelStream()
                            .map(AndSuccess.SuccessTuple::getBitmap)
                            .map(ByteString::toByteArray)
                            .map(ByteBuffer::wrap)
                            .map(ImmutableRoaringBitmap::new)
                            .reduce(ImmutableRoaringBitmap::and)
                            .get()
                            .getCardinality();

                    if (fileCnt1 != sResponse.getFilesCount())
                        return false;

                    List<AndVerifyTuple> lst = sResponse.getSuccessList().stream()
                            .map(AndVerifyTuple::new)
                            .collect(Collectors.toList());
                    failed = sResponse.getFilesList().stream()
                            .map(ByteString::toByteArray)
                            .anyMatch(i -> lst.stream()
                                    .anyMatch(kw -> !kw.hashset.contains(su.HMAC(i, kw.keyword.getBytes()))
                                    )
                            );
                    if (failed)
                        return false;
                default:
            }
            return true;
        }

        @Override
        public void setParameters(SecurityUtil su, SearchRequest request, SearchResponse response) {
            this.su = su;
            this.response = response.getAndResponse();
        }

        private class AndVerifyTuple {
            String keyword;
            TreeSet<byte[]> hashset = new TreeSet<>((left, right) -> {
                for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
                    int a = (left[i] & 0xff);
                    int b = (right[j] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return left.length - right.length;
            });

            AndVerifyTuple(AndSuccess.SuccessTuple tuple) {
                this.keyword = tuple.getKeyword();
                this.hashset.addAll(tuple.getHashsetList().stream()
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toSet()));
            }
        }
    }

    public static class StarVerifier implements Verifier {

        private SecurityUtil su;
        private RadixTree subtree;
        private StarResponse response;

        @Override
        @Timeit(Timeit.TYPE.VERIFY)
        public boolean verify() {
            switch (response.getMsgCase()) {
                case FAILED:
                    return failedTupleVerify2(su, response.getFailed());

                case SUCCESS:
                    StarSuccess sResponse = response.getSuccess();
                    boolean failed = subtree.getMiddleNodes().stream()
                            .anyMatch(i -> !Arrays.equals(i.getHcp(),
                                    su.HMAC(i.getAncestors() + i.getLabel(), i.getLc())));
                    if (failed)
                        return false;

                    failed = subtree
                            .getLeafNodes()
                            .stream()
                            .anyMatch(i -> {
                                List<ByteString> lst =
                                        sResponse
                                                .getFileIdsOrThrow(i.getW())
                                                .getFilesList()
                                                .stream()
                                                .map(sResponse.getFilesMap()::get)
                                                .collect(Collectors.toList());
                                List<byte[]> sb = lst.stream()
                                        .sequential()
                                        .map(ByteString::toByteArray)
                                        .collect(Collectors.toList());
                                return !Arrays.equals(i.getHcw(), su.HMAC(sb, i.getW().getBytes()));
                            });
                    if (failed)
                        return false;
                default:
            }
            return true;
        }

        @Override
        public void setParameters(SecurityUtil su, SearchRequest request, SearchResponse response) {
            this.su = su;
            this.response = response.getStarResponse();
            if (this.response.hasSuccess()) {
                String prefix = request.getStar().getHead();
                this.subtree = new RadixTree(su).load(this.response.getSuccess().getTree(),
                        prefix.substring(0, prefix.length() - 1));
            }
        }
    }

    public static class QVerifier implements Verifier {

        private SecurityUtil su;
        private RequestOuterClass.SearchQ request;

        private QResponse response;

        @Override
        @Timeit(Timeit.TYPE.VERIFY)
        public boolean verify() {
            switch (response.getMsgCase()) {
                case FAILED:
                    return failedTupleVerify2(su, response.getFailed());

                case SUCCESS:
                    QSuccess sResponse = response.getSuccess();

                    boolean failed = Arrays.equals(sResponse.getHCp().toByteArray(),
                            su.HMAC(request.getPart1(), sResponse.getLC()));

                    if (failed)
                        return false;

                    failed = sResponse.getSuccessList().stream().anyMatch(i -> {
                        if (!sResponse.getLC().contains("" + (char) i.getSubtreeLabel()))
                            return true;

                        List<byte[]> sb = i.getFilesList().stream().sequential()
                                .map(sResponse.getFilesMap()::get)
                                .map(ByteString::toByteArray)
                                .collect(Collectors.toList());

                        return !Arrays.equals(
                                i.getHFw().toByteArray(),
                                su.HMAC(
                                        sb,
                                        (request.getPart1() + ((char) i.getSubtreeLabel()) + request.getPart2())
                                                .getBytes()));
                    });
                    if (failed)
                        return false;

                    failed = sResponse
                            .getFailedList()
                            .stream()
                            .anyMatch(i -> {
                                if (!sResponse.getLC().contains(((char) i.getSubtreeLabel() + "")))
                                    return true;

                                String keyword = request.getPart1() + ((char) i.getSubtreeLabel()) + request.getPart2();
                                if (i.getLClList().contains(keyword.charAt(i.getLStar() - 1)))
                                    return true;

                                String prefix = "$" + keyword.substring(0, i.getLStar());
                                StringBuilder childrenSet = new StringBuilder();
                                i.getLClList().forEach(cLabel -> childrenSet.append((char) cLabel.intValue()));
                                byte[] hcp = su.HMAC(prefix, childrenSet.toString());
                                byte[] hcp2 = i.getHCp().toByteArray();
                                return !Arrays.equals(hcp, hcp2);
                            });

                    if (failed)
                        return false;
                    break;
                default:
            }
            return true;
        }

        @Override
        public void setParameters(SecurityUtil su, SearchRequest request, SearchResponse response) {
            this.su = su;
            this.request = request.getQ();
            this.response = response.getQResponse();
        }
    }
}
