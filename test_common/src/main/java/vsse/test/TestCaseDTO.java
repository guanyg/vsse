package vsse.test;

import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass;
import vsse.proto.ResponseOuterClass;
import vsse.proto.TestOuterClass.TestCasePush;
import vsse.proto.TestOuterClass.TestRegRequest.DeviceType;
import vsse.util.Counter;

import java.util.List;

public class TestCaseDTO {

    private static final Counter c = new Counter();
    private int caseId;
    private int documentCount;
    private int keywordCount;
    private List<String> keywords;
    private String keywordHead;
    private String keywordTail;
    private RadixTree t;
    private RequestOuterClass.SearchRequest.MsgCase type;
    private RequestOuterClass.SearchRequest query;
    private ResponseOuterClass.SearchResponse resp;
    private int fileCount;
    private long searchTime;
    private long verifyTime;
    private long verifyTimePC;
    private long verifyTimeAND;

    public TestCaseDTO() {
        this.setCaseId(c.inc());
    }

    public static Counter getC() {
        return c;
    }

    public TestCasePush serialize() {
        return TestCasePush.newBuilder()
                .setQuery(query)
                .setResp(resp)
                .setTestCaseId(caseId)
                .build();
    }

    public int getCaseId() {
        return caseId;
    }

    public void setCaseId(int caseId) {
        this.caseId = caseId;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public int getKeywordCount() {
        return keywordCount;
    }

    public void setKeywordCount(int keywordCount) {
        this.keywordCount = keywordCount;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getKeywordHead() {
        return keywordHead;
    }

    public void setKeywordHead(String keywordHead) {
        this.keywordHead = keywordHead;
    }

    public String getKeywordTail() {
        return keywordTail;
    }

    public void setKeywordTail(String keywordTail) {
        this.keywordTail = keywordTail;
    }

    public RadixTree getT() {
        return t;
    }

    public void setT(RadixTree t) {
        this.t = t;
    }

    public RequestOuterClass.SearchRequest.MsgCase getType() {
        return type;
    }

    public void setType(RequestOuterClass.SearchRequest.MsgCase type) {
        this.type = type;
    }

    public RequestOuterClass.SearchRequest getQuery() {
        return query;
    }

    public void setQuery(RequestOuterClass.SearchRequest query) {
        this.query = query;
    }

    public ResponseOuterClass.SearchResponse getResp() {
        return resp;
    }

    public void setResp(ResponseOuterClass.SearchResponse resp) {
        this.resp = resp;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public long getSearchTime() {
        return searchTime;
    }

    public void setSearchTime(long searchTime) {
        this.searchTime = searchTime;
    }

    public long getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(long verifyTime) {
        this.verifyTime = verifyTime;
    }

    public long getVerifyTimePC() {
        return verifyTimePC;
    }

    public void setVerifyTimePC(long verifyTimePC) {
        this.verifyTimePC = verifyTimePC;
    }

    public long getVerifyTimeAND() {
        return verifyTimeAND;
    }

    public void setVerifyTimeAND(long verifyTimeAND) {
        this.verifyTimeAND = verifyTimeAND;
    }

    public void setVerifyTime(Device d, long verifyTime) {
        if (DeviceType.ANDROID == d.getType()) {
            setVerifyTimeAND(verifyTime);
        } else {
            setVerifyTimePC(verifyTime);
        }
    }

}
