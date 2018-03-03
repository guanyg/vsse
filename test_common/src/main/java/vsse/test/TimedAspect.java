package vsse.test;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import vsse.annotation.Timeit;

import java.lang.reflect.Method;
import java.util.function.Supplier;

@Aspect
public class TimedAspect {
    private static final Logger logger = Logger.getLogger(TimedAspect.class);

    private static final String POINTCUT_METHOD =
            "execution(@vsse.annotation.Timeit * *(..))";

    @Pointcut(POINTCUT_METHOD)
    public void defineEntryPoint() {
    }

    @Around("defineEntryPoint()")
    public Object timing(ProceedingJoinPoint joinPoint) throws Throwable {
        TestCaseDTO tc = getCurrentTC();
        if (tc == null) return joinPoint.proceed();
        long a = System.nanoTime();
        Object ret = null;
        for (int i = 0; i < 100; i++)
            ret = joinPoint.proceed();
        long b = System.nanoTime();

        long i = (b - a) / 100;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Timeit.TYPE type = method.getAnnotation(Timeit.class).value();
        logger.info(String.format("tc.%d time:%d", tc.getCaseId(), i));
        if (type == Timeit.TYPE.SEARCH) {
            tc.setSearchTime(i);

        } else if (type == Timeit.TYPE.VERIFY) {
            tc.setVerifyTime(i);

        }
        return ret;
    }


    private static Supplier<TestCaseDTO> getter;

    public static void setGetter(Supplier<TestCaseDTO> getter) {
        TimedAspect.getter = getter;
    }

    public static TestCaseDTO getCurrentTC() {
        if (getter != null)
            return getter.get();
        return null;
    }
}
