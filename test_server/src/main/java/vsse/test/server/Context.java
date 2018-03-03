package vsse.test.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import vsse.client.ClientContext;
import vsse.proto.TestOuterClass;
import vsse.proto.TestOuterClass.Test;
import vsse.server.ServerContext;
import vsse.test.Device;
import vsse.test.TestCaseDTO;
import vsse.test.TimedAspect;
import vsse.util.Counter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class Context {
    public static final Param<ClientContext> P_CLIENT_CONTEXT = new Param<>();
    public static final Param<ServerContext> P_SERVER_CONTEXT = new Param<>();
    public static final Param<String> P_CREDENTIAL_PATH = new Param<>();
    public static final Param<String> P_TESTFILE_DIR = new Param<>();
    public static final Param<String> P_DBCONF = new Param<>();

    private static final Logger logger = Logger.getLogger(Context.class);
    private final BlockingQueue<TestCaseDTO> closedTestCase = new LinkedBlockingQueue<>();
    private final Object cTCLock = new Object();
    private final List<Channel> ch = new ArrayList<>();
    private final Map<ChannelId, Device> map = new ConcurrentHashMap<>();
    private final BlockingQueue<TestCaseDTO> queries = new LinkedBlockingQueue<>();
    private final BlockingQueue<TestCaseDTO> searchResult = new LinkedBlockingQueue<>();
    private final Counter cDevID = new Counter();
    private TestCaseDTO currentTestCase = null;
    private volatile boolean running = true;
    private Future<?> fQueryGen;
    private Future<?> fSearch;
    private Future<?> fResultLog;
    private Future<?> fTCDistribute;
    private int count = 0;


    public void start() {
        Context.setParameter(P_CLIENT_CONTEXT, new ClientContext(Context.getParameter(P_CREDENTIAL_PATH)));
        Context.setParameter(P_SERVER_CONTEXT, new ServerContext());

        SearchThread searchThread = new SearchThread(queries, searchResult);
        TimedAspect.setGetter(searchThread::getTc);

        fQueryGen = startThread(new QueryGenerator(queries));
        logger.info("QueryGenerator up.");
        try {
            fQueryGen.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        logger.info("QueryGenerator finish.");

        new Thread(() -> {
            fSearch = startThread(searchThread);
            try {
                fResultLog = startThread(new ResultStorage(closedTestCase));
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            fTCDistribute = startThread(() -> {
                while (running) {
                    synchronized (cTCLock) {
                        if (currentTestCase == null) {
                            logger.debug(1);
                            try {
                                currentTestCase = searchResult.take();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            logger.info("Running tc " + currentTestCase.getCaseId());
                            synchronized (ch) {
                                ch.forEach(
                                        c ->
                                                c.writeAndFlush(
                                                        Test.newBuilder()
                                                                .setTestCase(currentTestCase.serialize())));
                            }
                            logger.debug(3);
                        }
                        try {
                            cTCLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }).start();
    }

    public void onRequest(ChannelHandlerContext ctx, Test msg) {
        ChannelId channelId = ctx.channel().id();
        switch (msg.getMsgCase()) {
            case REG_REQ:
                Test.Builder resp = Test.newBuilder();
                Device dev = new Device(msg.getRegReq(), cDevID.inc());
                logger.info("New device " + dev);
                ch.add(ctx.channel());

                resp.setRegResp(
                        TestOuterClass.TestRegResponse.newBuilder()
                                .setDeviceId(dev.getDeviceId() + "")
                                .setCredential(Context.getParameter(P_CLIENT_CONTEXT).getCredential()));
                map.put(channelId, dev);
                ctx.writeAndFlush(resp);
                synchronized (cTCLock) {
                    if (currentTestCase != null) {
                        ctx.writeAndFlush(Test.newBuilder().setTestCase(currentTestCase.serialize()));
                    }
                }
                break;
            case TEST_RESULT:
                int testcase = msg.getTestResult().getTestCaseId();
                long verifyTime = msg.getTestResult().getVerifyTime();
                Device d = map.getOrDefault(channelId, null);
                logger.info("result|" + d + "," + testcase + "|" + verifyTime);

                synchronized (cTCLock) {
                    if (currentTestCase != null && currentTestCase.getCaseId() == testcase) {
                        currentTestCase.setVerifyTime(d, verifyTime);
                        count++;
                        //count == ch.size() &&
                        if (count > 1) {
                            count = 0;
                            synchronized (closedTestCase) {
                                closedTestCase.add(currentTestCase);
                                closedTestCase.notifyAll();
                            }
                            currentTestCase = null;
                            cTCLock.notifyAll();
                        }
                    }
                }

                break;
            default:
                break;
        }
    }

    public void shutdown() {
        fQueryGen.cancel(true);
        fSearch.cancel(true);
        fResultLog.cancel(true);
        fTCDistribute.cancel(true);
        logger.info("OK.");
    }

    private Future<?> startThread(Runnable runnable) {
        return Executors.newSingleThreadExecutor()
                .submit(runnable);
    }

    public TestCaseDTO getCurrentTestCase() {
        return currentTestCase;
    }

    private static class Param<T> {
    }

    private static final Map<Param<?>, Object> param = new HashMap<>();

    public static synchronized <T> void setParameter(Param<T> parameter, T value) {
        param.put(parameter, value);
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> T getParameter(Param<T> parameter) {
        return (T) param.get(parameter);
    }
}
