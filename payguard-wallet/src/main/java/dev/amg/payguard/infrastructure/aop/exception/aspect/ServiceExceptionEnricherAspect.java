package dev.amg.payguard.infrastructure.aop.exception.aspect;

import domain.DomainException;
import java.util.HashMap;
import java.util.Map;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class ServiceExceptionEnricherAspect {

  private static final Logger log = LoggerFactory.getLogger(ServiceExceptionEnricherAspect.class);

  @AfterThrowing(pointcut = "execution(* dev.amg.payguard.application..*(..))", throwing = "ex")
  public void enrichAndLogServiceException(JoinPoint joinPoint, DomainException ex) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String[] paramNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    Map<String, Object> context = new HashMap<>();
    if (paramNames != null && args != null) {
      int parameterCount = Math.min(paramNames.length, args.length);
      for (int i = 0; i < parameterCount; i++) {
        String parameterName = paramNames[i];
        String normalizedName = parameterName.toLowerCase(java.util.Locale.ROOT);
        if (!normalizedName.contains("pin")
            && !normalizedName.contains("password")
            && !normalizedName.contains("token")
            && !normalizedName.contains("secret")) {
          context.put(parameterName, args[i]);
        }
      }
    }

    MDC.put("failed_method", signature.getName());
    MDC.put("exception_type", ex.getClass().getSimpleName());
    try {
      if (log.isWarnEnabled()) {
        log.warn(
            "Service execution failed: method [{}], error: '{}', context: {}",
            signature,
            ex.getMessage(),
            context);
      }
    } finally {
      MDC.remove("failed_method");
      MDC.remove("exception_type");
    }
  }
}
