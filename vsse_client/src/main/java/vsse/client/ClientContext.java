package vsse.client;

import org.apache.log4j.Logger;
import vsse.model.RadixTree;
import vsse.proto.Filedesc;
import vsse.proto.Filedesc.Credential;
import vsse.proto.RequestOuterClass;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.ResponseOuterClass.SearchResponse;
import vsse.security.SecurityUtil;
import vsse.util.Util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class ClientContext {
    private static final Logger logger = Logger.getLogger(ClientContext.class);
    private Credential credential;
    private Verifiers verifiers;

    public ClientContext() {
        this("credential.bin");
    }

    public ClientContext(String credentialPath) {
        this.loadCredential(credentialPath);
    }

    public ClientContext(Credential credential) {
        this.setCredential(credential);
    }


    public RadixTree buildRadixTree(List<Filedesc.Document> documents, List<String> keywords) {
        RadixTree tree = new RadixTree(new SecurityUtil(credential));

        tree.putKeywords(keywords);
        tree.putDocuments(documents);

        return tree;
    }

    public SearchRequest createQuery(SearchRequest.MsgCase type, String... args) {
        SearchRequest.Builder request = SearchRequest.newBuilder();
        switch (type) {
            case AND:
                request.setAnd(RequestOuterClass.SearchAnd.newBuilder().addAllKeywords(Arrays.asList(args)));
                break;
            case OR:
                request.setOr(RequestOuterClass.SearchOr.newBuilder().addAllKeywords(Arrays.asList(args)));
                break;
            case STAR:
                request.setStar(RequestOuterClass.SearchStar.newBuilder().setHead(args[0]));
                break;
            case Q:
                request.setQ(RequestOuterClass.SearchQ.newBuilder().setPart1(args[0]).setPart2(args[1]));
                break;
            case MSG_NOT_SET:
        }
        return request.build();
    }

    public void verify(SearchRequest request, SearchResponse response) throws Exception {
        if (!verifiers.verify(request, response)) {
            throw new Exception("Verification failed!");
        }
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
        this.verifiers = new Verifiers(new SecurityUtil(credential));
    }

    public void loadCredential(String credentialPath) {
        try (FileInputStream fis = new FileInputStream(credentialPath)) {
            this.setCredential(Filedesc.Credential.parseFrom(fis));
            return;
        } catch (IOException e) {
            logger.info("Load credential failed.", e);
        }

        Filedesc.Credential c = Util.createCredential();
        try (FileOutputStream fos = new FileOutputStream(credentialPath)) {
            c.writeTo(fos);
        } catch (IOException e) {
            logger.info("Write new credential file failed.", e);
        }
        this.setCredential(c);
    }
}
