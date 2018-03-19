package vsse.server;

import com.google.protobuf.GeneratedMessageV3;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LoggingHandler;
import org.apache.log4j.Logger;
import vsse.model.DocumentDTO;
import vsse.model.RadixTree;
import vsse.proto.RequestOuterClass;
import vsse.proto.RequestOuterClass.Request;
import vsse.proto.ResponseOuterClass.Response;
import vsse.proto.ResponseOuterClass.SearchResponse;
import vsse.proto.ResponseOuterClass.UploadResponse;

import java.util.stream.Collectors;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class);
    private ServerContext context = new ServerContext();
    private EventLoopGroup workerGroup;
    private NioEventLoopGroup bossGroup;
    private ChannelFuture f;

    public static void main(String[] args) {
        int port = -1;
        if (args.length == 1) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (Throwable ex) {
                // ignore
            }
        }
        if (port < 0) {
            port = 5678;
        }
        new Server().start(port);
    }


    private void shutdown() {
        f.channel().close();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        System.out.println("Shutdown!");
    }

    private void start(int port) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Shutdown-Hook"));
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(this.bossGroup, this.workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 100)
                .handler(new LoggingHandler())
                .childHandler(
                        new ChannelInitializer<SocketChannel>() {

                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                                ch.pipeline().addLast(new ProtobufDecoder(Request.getDefaultInstance()));
                                ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                                ch.pipeline().addLast(new ProtobufEncoder());
                                ch.pipeline().addLast(new MSGHandler());
                            }
                        });

        try {
            this.f = b.bind(port).sync();
            logger.info("Server listening at " + port);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Request request, GeneratedMessageV3 responseObj) {
        Response.Builder response = Response.newBuilder();
        response.setState(context.getState());
        response.setReqSequence(request.getSequence());

        if (responseObj instanceof SearchResponse) {
            response.setSearchResponse((SearchResponse) responseObj);
        } else if (responseObj instanceof UploadResponse) {
            response.setUploadResponse((UploadResponse) responseObj);
        }
        ctx.writeAndFlush(response);
    }

    private class MSGHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Request request = (Request) msg;
            switch ((request.getMsgCase())) {
                case UPLOAD_REQUEST:
                    RequestOuterClass.UploadRequest upload = request.getUploadRequest();

                    RadixTree tree = new RadixTree();
                    tree.load(upload.getTree());
                    tree.setDocuments(upload.getFilesList()
                            .stream()
                            .map(DocumentDTO::parse)
                            .collect(Collectors.toList()));
                    context.setTree(tree);

                    sendResponse(ctx, request, UploadResponse
                            .newBuilder()
                            .setMsg("Upload Success")
                            .build());

                    break;
                case SEARCH_REQUEST:
                    if (context.getState() == Response.State.READY) {
                        context.doSearchAsync(request.getSearchRequest(),
                                searchResponse -> {
                                    sendResponse(ctx, request, searchResponse);
                                });
                    }
                    break;
                default:
                    sendResponse(ctx, request, null);
                    break;
            }
        }
    }
}
