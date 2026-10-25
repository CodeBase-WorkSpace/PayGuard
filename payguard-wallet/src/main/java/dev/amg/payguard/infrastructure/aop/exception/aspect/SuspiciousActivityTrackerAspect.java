package dev.amg.payguard.infrastructure.aop.exception.aspect;

import dev.amg.payguard.domain.exception.InsufficientFundException;
import dev.amg.payguard.domain.exception.WalletFrozenException;
import dev.amg.payguard.infrastructure.aop.exception.annotation.SecureAuditOnFailure;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SuspiciousActivityTrackerAspect {

  private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

  @AfterThrowing(pointcut = "@annotation(secureAudit)", throwing = "ex")
  public void auditSuspiciousFailure(
      JoinPoint joinPoint, SecureAuditOnFailure secureAudit, Exception ex) {
    String action = secureAudit.action();

    if (ex instanceof InsufficientFundException || ex instanceof WalletFrozenException) {
      if (securityLog.isErrorEnabled()) {
        securityLog.error(
            "Fraud alert: suspicious failure during action [{}]. Method: [{}]. Reason: {}",
            action,
            joinPoint.getSignature(),
            ex.getMessage());
      }

      triggerSecurityCountermeasures();
    }
  }

  private void triggerSecurityCountermeasures() {
    // Hook for a future IP blocklist or SOC notification integration.
  }
}
