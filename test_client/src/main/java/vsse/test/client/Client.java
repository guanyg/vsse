package vsse.test.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import vsse.test.Device;
import vsse.util.Util;

import static vsse.proto.TestOuterClass.Test;
import static vsse.proto.TestOuterClass.TestRegRequest;
import static vsse.proto.TestOuterClass.TestRegRequest.DeviceType.PC;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class);
    private final Device device;
    private final Context context;

    private Client(Device d, String host, int port) {
        context = new Context();
        connectTo(host, port);
        this.device = d;
    }

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("h", "host", true, "test server IP");
        options.addRequiredOption("p", "port", false, "test server port");
        options.addRequiredOption("n", "name", true, "test client name");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar " + Util.getJarName(Client.class) + " [args]", options);
            return;
        }
        int port = 5678;
        try {
            if (cmd.hasOption('p'))
                port = Integer.valueOf(cmd.getOptionValue('p'));
        } catch (Throwable ex) {
            logger.info("Use default port:" + port);
        }

        new Client(new Device(PC, cmd.getOptionValue('n')), cmd.getOptionValue('h'), port);
    }

    private void connectTo(String host, int port) {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.handler(
                new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProtobufVarint32FrameDecoder());
                        ch.pipeline().addLast(new ProtobufDecoder(Test.getDefaultInstance()));
                        ch.pipeline().addLast(new ProtobufVarint32LengthFieldPrepender());
                        ch.pipeline().addLast(new ProtobufEncoder());
                        ch.pipeline().addLast(new MSGHandler(Client.this));
                    }
                });
        ChannelFuture f = b.connect(host, port);
        f.addListener(
                (GenericFutureListener<ChannelFuture>) ch -> context.setChannel((SocketChannel) ch.channel()));
    }


    private class MSGHandler extends ChannelInboundHandlerAdapter {
        private final Client client;

        //    public MSGHandler() {}
        MSGHandler(Client c) {
            this.client = c;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Test.Builder t = Test.newBuilder();
            TestRegRequest.Builder req =
                    TestRegRequest.newBuilder()
                            .setDeviceName(client.device.getDeviceName())
                            .setDeviceType(client.device.getType());
            t.setRegReq(req);
            ctx.channel().writeAndFlush(t);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Test r = (Test) msg;
            context.onMessage(r);
        }
    }
}
