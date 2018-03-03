package vsse.test.server;

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
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import vsse.proto.TestOuterClass.Test;
import vsse.util.Util;


public class Server {
    private static final Logger logger = Logger.getLogger(Server.class);
    private final Context context = new Context();
    private EventLoopGroup workerGroup;
    private NioEventLoopGroup bossGroup;
    private ChannelFuture f;

    public static void main(String[] args) {

        Options options = new Options();
        options.addRequiredOption("p", "port", false, "test server port");
        options.addRequiredOption("c", "credential", true, "credential path");
        options.addRequiredOption("f", "filedir", true, "file directory");
        options.addRequiredOption("d", "dbconf", true, "database configuration");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar " + Util.getJarName(Server.class) + " [args]", options);
            return;
        }
        int port = 5678;
        try {
            if (cmd.hasOption('p'))
                port = Integer.valueOf(cmd.getOptionValue('p'));
        } catch (Throwable ex) {
            logger.info("Use default port:" + port);
        }

        Context.setParameter(Context.P_CREDENTIAL_PATH, cmd.getOptionValue('c'));
        Context.setParameter(Context.P_TESTFILE_DIR, cmd.getOptionValue('f'));
        Context.setParameter(Context.P_DBCONF, cmd.getOptionValue('d'));

        new Server().start(port);
    }

    private void shutdown() {
        f.channel().close();
        context.shutdown();
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
                                ch.pipeline().addLast(new ProtobufDecoder(Test.getDefaultInstance()));
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
        this.context.start();
    }

    private class MSGHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            context.onRequest(ctx, (Test) msg);
        }
    }

}
