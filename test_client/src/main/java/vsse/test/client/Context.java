package vsse.test.client;

import io.netty.channel.socket.SocketChannel;
import org.apache.log4j.Logger;
import vsse.client.ClientContext;
import vsse.proto.TestOuterClass;
import vsse.proto.TestOuterClass.Test;
import vsse.proto.TestOuterClass.TestRegResponse;
import vsse.test.TestCaseDTO;
import vsse.test.TimedAspect;

public class Context {
    private static final Logger logger = Logger.getLogger(Context.class);
    private ClientContext clientContext;

    private SocketChannel channel;
    private TestCaseDTO currentTc = new TestCaseDTO();

    public Context() {
        TimedAspect.setGetter(this::getCurrentTC);
    }

    public void setChannel(SocketChannel channel) {
        this.channel = channel;
    }

    public void onMessage(Test msg) {

        switch (msg.getMsgCase()) {
            case REG_RESP:
                TestRegResponse resp = msg.getRegResp();
                clientContext = new ClientContext(resp.getCredential());
                logger.info("Device Id : " + resp.getDeviceId());
                break;
            case TEST_CASE:
                TestOuterClass.TestCasePush tc = msg.getTestCase();
                new Thread(() -> {
                    logger.info("New tc." + tc.getTestCaseId());
                    currentTc.setCaseId(tc.getTestCaseId());
                    try {
                        clientContext.verify(tc.getQuery(), tc.getResp());
                        channel.writeAndFlush(Test.newBuilder()
                                .setTestResult(TestOuterClass.TestResult.newBuilder()
                                        .setTestCaseId(tc.getTestCaseId())
                                        .setVerifyTime(currentTc.getVerifyTime()))
                                .build());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                break;
            default:
                break;
        }
    }

    private TestCaseDTO getCurrentTC() {
        return currentTc;
    }

}
