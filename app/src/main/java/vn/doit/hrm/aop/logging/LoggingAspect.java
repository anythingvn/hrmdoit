package vn.doit.hrm.aop.logging;

import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tech.jhipster.config.JHipsterConstants;

/**
 * Aspect for logging execution of service and repository Spring components.
 *
 * By default, it only runs with the "dev" profile.
 */
@Aspect
public class LoggingAspect {

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    /**
     * Pointcut that matches all repositories, services and Web REST endpoints.
     */
    @Pointcut(
        """
        within(@org.springframework.stereotype.Repository *)
        || within(@org.springframework.stereotype.Service *)
        || within(@org.springframework.web.bind.annotation.RestController *)
        """
    )
    public void springBeanPointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advice methods.
    }

    /**
     * Pointcut that matches all Spring beans in the application's main packages.
     */
    @Pointcut(
        """
        within(vn.doit.hrm.repository..*)
        || within(vn.doit.hrm.service..*)
        || within(vn.doit.hrm.web.rest..*)
        """
    )
    public void applicationPackagePointcut() {
        // Method is empty as this is just a Pointcut, the implementations are in the advice methods.
    }

    /**
     * Retrieves the {@link Logger} associated to the given {@link JoinPoint}.
     *
     * @param joinPoint join point we want the logger for.
     * @return {@link Logger} associated to the given {@link JoinPoint}.
     */
    private Logger logger(JoinPoint joinPoint) {
        return LoggerFactory.getLogger(joinPoint.getSignature().getDeclaringTypeName());
    }

    /**
     * Advice that logs methods throwing exceptions.
     *
     * @param joinPoint join point for advice.
     * @param e exception.
     */
    @AfterThrowing(pointcut = "applicationPackagePointcut() && springBeanPointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        logException(joinPoint, e);
    }

    /**
     * Advice that logs when a method is entered and exited.
     *
     * @param joinPoint join point for advice.
     * @return result.
     * @throws Throwable rethrows the exception thrown by the advised method.
     */
    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        var log = logger(joinPoint);
        if (log.isDebugEnabled()) {
            log.debug("Enter: {}() with argument[s] = {}", joinPoint.getSignature().getName(), arguments(joinPoint));
        }
        try {
            Object result = joinPoint.proceed();
            logExit(log, joinPoint, result);
            return result;
        } catch (IllegalArgumentException e) {
            logIllegalArgument(log, joinPoint);
            throw e;
        }
    }

    /** Lớp/hàm có {@link NoArgumentLogging}: không ghi giá trị tham số và kết quả. */
    private static boolean hidden(JoinPoint joinPoint) {
        Class<?> type = joinPoint.getSignature().getDeclaringType();
        boolean hidden = type != null && type.isAnnotationPresent(NoArgumentLogging.class);
        if (!hidden && joinPoint.getSignature() instanceof MethodSignature m) {
            hidden = m.getMethod().isAnnotationPresent(NoArgumentLogging.class);
        }
        return hidden;
    }

    /** Giá trị tham số để ghi log; lớp/hàm có {@link NoArgumentLogging} thì chỉ ghi số tham số. */
    private static String arguments(JoinPoint joinPoint) {
        return hidden(joinPoint) ? "[" + joinPoint.getArgs().length + " hidden]" : Arrays.toString(joinPoint.getArgs());
    }

    private void logExit(Logger log, JoinPoint joinPoint, Object result) {
        if (log.isDebugEnabled()) {
            log.debug("Exit: {}() with result = {}", joinPoint.getSignature().getName(), hidden(joinPoint) ? "[đã ẩn]" : result);
        }
    }

    private void logIllegalArgument(Logger log, JoinPoint joinPoint) {
        if (log.isErrorEnabled()) {
            log.error("Illegal argument: {} in {}()", arguments(joinPoint), joinPoint.getSignature().getName());
        }
    }

    private void logException(JoinPoint joinPoint, Throwable e) {
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            logger(joinPoint).error(
                "Exception in {}() with cause = '{}' and exception = '{}'",
                joinPoint.getSignature().getName(),
                e.getCause() != null ? e.getCause() : "NULL",
                e.getMessage(),
                e
            );
        } else {
            logger(joinPoint).error(
                "Exception in {}() with cause = {}",
                joinPoint.getSignature().getName(),
                e.getCause() != null ? String.valueOf(e.getCause()) : "NULL"
            );
        }
    }
}
