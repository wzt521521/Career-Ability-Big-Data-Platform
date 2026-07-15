package com.career.platform.auth.audit;

import com.career.platform.auth.entity.SysOperationLog;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.security.CurrentUser;
import com.career.platform.common.security.CurrentUserProvider;
import com.career.platform.common.security.SensitiveDataRedactor;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final OperationLogWriter logWriter;
    private final CurrentUserProvider currentUserProvider;

    public OperationLogAspect(
            OperationLogWriter logWriter,
            CurrentUserProvider currentUserProvider) {
        this.logWriter = logWriter;
        this.currentUserProvider = currentUserProvider;
    }

    @Around("@annotation(operationLog)")
    public Object record(ProceedingJoinPoint joinPoint, Log operationLog) throws Throwable {
        long startedAt = System.currentTimeMillis();
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            try {
                writeLog(joinPoint, operationLog, startedAt, failure);
            } catch (RuntimeException logFailure) {
                logger.error("Unable to persist operation log", logFailure);
            }
        }
    }

    private void writeLog(
            ProceedingJoinPoint joinPoint,
            Log annotation,
            long startedAt,
            Throwable failure) {
        SysOperationLog operationLog = new SysOperationLog();
        populateCurrentUser(operationLog);
        operationLog.setModule(annotation.module());
        operationLog.setOperation(annotation.operation());
        operationLog.setDescription(annotation.description());
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        operationLog.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        operationLog.setParams(safeParameterSummary(joinPoint.getArgs()));
        operationLog.setIp(resolveClientIp());
        operationLog.setDuration(System.currentTimeMillis() - startedAt);
        operationLog.setStatus(failure == null ? 1 : 0);
        if (failure != null) {
            operationLog.setErrorMessage(truncate(SensitiveDataRedactor.redact(failure.getMessage())));
        }
        logWriter.write(operationLog);
    }

    private void populateCurrentUser(SysOperationLog operationLog) {
        try {
            CurrentUser currentUser = currentUserProvider.requireCurrentUser();
            operationLog.setUserId(currentUser.getId());
            operationLog.setUsername(currentUser.getUsername());
        } catch (RuntimeException ignored) {
            operationLog.setUsername("anonymous");
        }
    }

    private String safeParameterSummary(Object[] arguments) {
        return Arrays.stream(arguments)
                .map(argument -> argument == null ? "null" : argument.getClass().getSimpleName())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getRemoteAddr();
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
