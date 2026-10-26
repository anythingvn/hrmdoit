package vn.doit.hrm.aop.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/** Lớp/hàm có {@link NoArgumentLogging} không để lộ giá trị tham số và kết quả trong log DEBUG. */
class LoggingAspectTest {

    private static final String SECRET_ARG = "MatKhauThamSo@1";
    private static final String SECRET_RESULT = "MatKhauKetQua@2";

    @NoArgumentLogging
    static class HiddenType {

        String secret(String in) {
            return SECRET_RESULT;
        }
    }

    static class PlainType {

        @NoArgumentLogging
        String hiddenMethod(String in) {
            return SECRET_RESULT;
        }

        String visibleMethod(String in) {
            return "KetQuaCongKhai";
        }
    }

    private final LoggingAspect aspect = new LoggingAspect(new MockEnvironment());
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attach() {
        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(LoggingAspectTest.class.getName());
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private String run(Class<?> type, String methodName, String arg, String result) throws Throwable {
        Method method = type.getDeclaredMethod(methodName, String.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringType()).thenReturn(type);
        when(signature.getDeclaringTypeName()).thenReturn(LoggingAspectTest.class.getName());
        when(signature.getName()).thenReturn(methodName);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { arg });
        when(joinPoint.proceed()).thenReturn(result);
        aspect.logAround(joinPoint);
        List<ILoggingEvent> events = appender.list;
        return String.join("\n", events.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void hiddenTypeLogsNeitherArgumentsNorResult() throws Throwable {
        String logged = run(HiddenType.class, "secret", SECRET_ARG, SECRET_RESULT);
        assertThat(logged).contains("Enter: secret()").contains("Exit: secret()");
        assertThat(logged).doesNotContain(SECRET_ARG).doesNotContain(SECRET_RESULT);
    }

    @Test
    void hiddenMethodLogsNeitherArgumentsNorResult() throws Throwable {
        String logged = run(PlainType.class, "hiddenMethod", SECRET_ARG, SECRET_RESULT);
        assertThat(logged).contains("Exit: hiddenMethod()");
        assertThat(logged).doesNotContain(SECRET_ARG).doesNotContain(SECRET_RESULT);
    }

    @Test
    void otherMethodsStillLogArgumentsAndResult() throws Throwable {
        String logged = run(PlainType.class, "visibleMethod", "ThamSoCongKhai", "KetQuaCongKhai");
        assertThat(logged).contains("ThamSoCongKhai").contains("KetQuaCongKhai");
    }
}
