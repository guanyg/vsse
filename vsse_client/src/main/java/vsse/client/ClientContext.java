package vsse.client;

import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import vsse.model.DocumentDTO;
import vsse.model.RadixTree;
import vsse.proto.Filedesc;
import vsse.proto.Filedesc.Credential;
import vsse.proto.RequestOuterClass;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.ResponseOuterClass.SearchResponse;
import vsse.security.SecurityUtil;
import vsse.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class ClientContext {
    private static final Logger logger = Logger.getLogger(ClientContext.class);
    private Credential credential;
    private Verifiers verifiers;
    private SecurityUtil su;

    public ClientContext() {
        this("credential.bin");
    }

    public ClientContext(String credentialPath) {
        this.loadCredential(credentialPath);
    }

    public ClientContext(File credential) {
        this.loadCredential(credential.getAbsolutePath());
    }

    public ClientContext(Credential credential) {
        this.setCredential(credential);
    }

    public RadixTree buildRadixTree(List<DocumentDTO> documents) {
        return buildRadixTree(documents,
                documents.stream()
                        .flatMap(d -> Arrays.stream(d.getKeywords()))
                        .distinct()
                        .collect(Collectors.toList()));
    }

    public RadixTree buildRadixTree(List<DocumentDTO> documents, List<String> keywords) {
        RadixTree tree = new RadixTree(su);

        tree.putKeywords(keywords);
        tree.putDocuments(documents);

        return tree;
    }

    public SearchRequest createQuery(SearchRequest.MsgCase type, String... args) {
        SearchRequest.Builder request = SearchRequest.newBuilder();
        args = Arrays.stream(args).map(su::FPE).toArray(String[]::new);
        switch (type) {
            case AND:
                request.setAnd(RequestOuterClass.SearchAnd.newBuilder().addAllKeywords(asList(args)));
                break;
            case OR:
                request.setOr(RequestOuterClass.SearchOr.newBuilder().addAllKeywords(asList(args)));
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

    public List<byte[]> extractFiles(SearchResponse response) {
        switch (response.getMsgCase()) {

            case AND_RESPONSE:
                return response.getAndResponse()
                        .getSuccess()
                        .getFilesList()
                        .stream()
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toList());

            case OR_RESPONSE:
                return response.getOrResponse()
                        .getFilesMap()
                        .values()
                        .stream()
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toList());

            case STAR_RESPONSE:
                return response.getStarResponse()
                        .getSuccess()
                        .getFilesMap()
                        .values()
                        .stream()
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toList());

            case Q_RESPONSE:
                return response.getQResponse()
                        .getSuccess()
                        .getFilesMap()
                        .values()
                        .stream()
                        .map(ByteString::toByteArray)
                        .collect(Collectors.toList());
            case MSG_NOT_SET:
        }
        return Collections.emptyList();
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
        this.su = new SecurityUtil(credential);
        this.verifiers = new Verifiers(su);
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

    public SecurityUtil getSecurityUtil() {
        return su;
    }
}
