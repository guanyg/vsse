package vsse.client.pc;

import com.google.protobuf.GeneratedMessageV3;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vsse.client.ClientContext;
import vsse.model.DocumentDTO;
import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass;
import vsse.proto.RequestOuterClass.SearchRequest;
import vsse.proto.RequestOuterClass.UploadRequest;
import vsse.proto.ResponseOuterClass.Response;
import vsse.proto.ResponseOuterClass.SearchResponse;
import vsse.proto.ResponseOuterClass.UploadResponse;
import vsse.security.SecurityUtil;
import vsse.util.Counter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


public class ConnectionController implements Initializable {

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private TextField credentialField;

    @FXML
    private Button selectBtn;

    @FXML
    private Button createBtn;

    @FXML
    private Button connectBtn;

    private CompletableFuture<Connection> future;

    private ConnectionController(CompletableFuture<Connection> future) {
        this.future = future;
    }

    public static CompletableFuture<Connection> openConnection() {
        CompletableFuture<Connection> ret = new CompletableFuture<>();
        FXMLLoader loader = new FXMLLoader();

        loader.setController(new ConnectionController(ret));
        Stage stage = new Stage();
        try {
            InputStream is = ConnectionController.class.getResourceAsStream("/ConnectPane.fxml");
            stage.setScene(new Scene(loader.load(is)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        ret.whenComplete((a, ex) -> {
            stage.close();
        });
        stage.setTitle("Connect to ... - VSSE Client");
        stage.setOnCloseRequest(ev -> System.exit(0));
        stage.show();
        return ret;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hostField.setText("localhost");
        portField.setText("5678");

        Connection connection = new MSGHandler();

        EventHandler<ActionEvent> cFileSelector = ev -> {
            FileChooser fileChooser = new FileChooser();
            String initialFP = credentialField.getText();
            if (initialFP.length() != 0) {
                File file = new File(initialFP);
                if (!file.isDirectory())
                    file = file.getParentFile();
                fileChooser.setInitialDirectory(file);
            }
            File credentialFile;
            if (ev.getTarget() == selectBtn) {
                credentialFile = fileChooser.showOpenDialog(null);
            } else {
                fileChooser.setInitialFileName("credential.bin");
                credentialFile = fileChooser.showSaveDialog(null);
            }
            try {
                connection.setCredential(credentialFile);
                credentialField.setText(credentialFile.getAbsolutePath());
            } catch (Exception ignored) {

            }
        };
        selectBtn.setOnAction(cFileSelector);
        createBtn.setOnAction(cFileSelector);

        connectBtn.setOnAction(ev -> {
            int port;
            try {
                port = Integer.valueOf(portField.getText());
            } catch (Throwable ex) {
                new Alert(Alert.AlertType.ERROR, "Invalid Port").showAndWait();
                portField.requestFocus();
                return;
            }
            if (credentialField.getLength() == 0) {
                credentialField.requestFocus();
                return;
            }
            int finalPort = port;
            Platform.runLater(() -> {
                try {
                    connection.connectTo(hostField.getText(), finalPort);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
                future.complete(connection);
            });
            connectBtn.setDisable(true);
        });
    }

    public interface Connection {
        void close();

        void shutdown();

        void onInactive(Runnable runnable);

        CompletableFuture<List<String>> search(SearchRequest.MsgCase type, String... args);

        CompletableFuture<UploadResponse> upload(List<DocumentDTO> files);

        Response.State getState();

        void setCredential(File credential);

        void connectTo(String text, int finalPort) throws Throwable;

        String getUrl();
    }

    private static class MSGHandler implements Connection {

        private static NioEventLoopGroup workerGroup = new NioEventLoopGroup();
        private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();
        private Map<Long, CompletableFuture> waitingMsg = new TreeMap<>();
        private SocketChannel channel;
        private ClientContext clientContext;


        @Override
        public void close() {
            channel.shutdown();
            Platform.runLater(() -> {
                shutdownFuture.complete(null);
            });
        }

        @Override
        public void shutdown() {
            channel.shutdown();
            workerGroup.shutdownGracefully();
        }

        @Override
        public void onInactive(Runnable runnable) {
            shutdownFuture.whenComplete((a, ex) -> Platform.runLater(() -> {
                if (ex != null)
                    new Alert(Alert.AlertType.ERROR, ex.getLocalizedMessage()).showAndWait();
                runnable.run();
            }));
        }

        private CompletableFuture sendRequest(GeneratedMessageV3 request) {
            RequestOuterClass.Request.Builder req = RequestOuterClass.Request.newBuilder();
            req.setSequence(System.nanoTime());
            CompletableFuture ret;
            if (request != null) {
                if (request instanceof SearchRequest) {
                    req.setSearchRequest((SearchRequest) request);
                    ret = new CompletableFuture<SearchResponse>();
                } else if (request instanceof UploadRequest) {
                    req.setUploadRequest((UploadRequest) request);
                    ret = new CompletableFuture<UploadResponse>();
                } else {
                    throw new UnsupportedOperationException();
                }
            } else {
                ret = new CompletableFuture<Response>();
            }
            channel.writeAndFlush(req);
            waitingMsg.put(req.getSequence(), ret);
            return ret;
        }

        @Override
        public CompletableFuture<List<String>> search(SearchRequest.MsgCase type, String... args) {
            CompletableFuture<List<String>> ret = new CompletableFuture<>();
            SearchRequest request = clientContext.createQuery(type, args);
            sendRequest(request).whenComplete((response, exception) -> {
                try {
                    clientContext.verify(request, (SearchResponse) response);
                    SecurityUtil securityUtil = clientContext.getSecurityUtil();
                    ret.complete(clientContext
                            .extractFiles((SearchResponse) response)
                            .stream()
                            .map(securityUtil::decrypt)
                            .map(b -> {
                                try {
                                    return new String(b, "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                } catch (Exception e) {
                    ret.completeExceptionally(e);
                }
            });
            return ret;
        }

        @Override
        public CompletableFuture<UploadResponse> upload(List<DocumentDTO> files) {
            Counter id = new Counter();
            SecurityUtil securityUtil = clientContext.getSecurityUtil();

            List<DocumentDTO> doc = files.stream().map(d -> {
                try {
                    DocumentDTO ret = new DocumentDTO();
                    ret.setId(id.inc())
                            .setCipher(securityUtil.encrypt(d.getContent().getBytes("UTF-8")))
                            .setKeywords(Arrays.stream(d.getKeywords())
                                    .map(String::toLowerCase)
                                    .toArray(String[]::new));
                    return ret;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());


            RadixTree radixTree = clientContext
                    .buildRadixTree(doc);
            UploadRequest request = UploadRequest
                    .newBuilder()
                    .setTree(radixTree.serialize())
                    .addAllFiles(doc.stream().map(DocumentDTO::build).collect(Collectors.toList()))
                    .build();

            return sendRequest(request);
        }

        @Override
        public Response.State getState() {
            try {
                return ((Response) sendRequest(null).get()).getState();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return Response.State.ERROR;
        }

        @Override
        public void setCredential(File credential) {
            this.clientContext = new ClientContext(credential);
        }

        @Override
        public void connectTo(String host, int port) throws Throwable {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(
                    new ChannelInitializer<SocketChannel>() {

                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                            ch.pipeline().addLast(new ProtobufDecoder(Response.getDefaultInstance()));
                            ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                            ch.pipeline().addLast(new ProtobufEncoder());
                            ch.pipeline().addLast(new ChannelInboundHelper());
                        }
                    });
            ChannelFuture f = b.connect(host, port);

            try {
                f.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!f.isSuccess()) {
                throw f.cause();
            }
        }

        @Override
        public String getUrl() {
            StringBuilder sb = new StringBuilder("vsse://");
            InetSocketAddress add = channel.remoteAddress();
            sb.append(add.getHostString());
            sb.append(":");
            sb.append(add.getPort());
            sb.append("/?");
            sb.append("k=").append(this.clientContext.getCredential().getK());
            sb.append("&k0=").append(this.clientContext.getCredential().getK0());
            sb.append("&k1=").append(this.clientContext.getCredential().getK1());
            return sb.toString();
        }

        private class ChannelInboundHelper extends ChannelInboundHandlerAdapter {

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                MSGHandler.this.channel = (SocketChannel) ctx.channel();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                shutdownFuture.completeExceptionally(cause);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Response response = (Response) msg;
                long sequence = response.getReqSequence();
                if (waitingMsg.containsKey(sequence)) {
                    Object ret = null;
                    switch (response.getMsgCase()) {

                        case UPLOAD_RESPONSE:
                            ret = response.getUploadResponse();
                            break;
                        case SEARCH_RESPONSE:
                            ret = response.getSearchResponse();
                            break;
                        case MSG_NOT_SET:
                            ret = response;
                            break;
                    }
                    waitingMsg.get(sequence).complete(ret);
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                super.channelInactive(ctx);
                shutdownFuture.complete(null);
            }
        }
    }
}
