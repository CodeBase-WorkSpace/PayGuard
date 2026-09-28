package dev.amg.payguard.infrastructure.aop.exception.aspect;

import dev.amg.payguard.domain.exception.DoubleEntryImbalanceException;
import dev.amg.payguard.domain.exception.HoldExpiredException;
import dev.amg.payguard.domain.exception.InsufficientFundException;
import dev.amg.payguard.domain.exception.InvalidHoldStateException;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.exception.LimitExceededException;
import dev.amg.payguard.domain.exception.TransactionAlreadySettledException;
import dev.amg.payguard.domain.exception.WalletFrozenException;
import dev.amg.payguard.infrastructure.aop.exception.model.UnifiedResponse;
import domain.DomainException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class RestControllerExceptionAspect {
  private static final Logger log = LoggerFactory.getLogger(RestControllerExceptionAspect.class);

  @Pointcut(
      "@annotation(dev.amg.payguard.infrastructure.aop.exception.annotation.HandleWalletExceptions)"
          + " || @within(dev.amg.payguard.infrastructure.aop.exception.annotation.HandleWalletExceptions)"
          + " || within(@org.springframework.web.bind.annotation.RestController *)")
  public void restControllerLayer() {}

  @Around("restControllerLayer()")
  public Object interceptAndHandleExceptions(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      return joinPoint.proceed();
    } catch (DomainException ex) {
      return mapDomainException(ex, joinPoint);
    } catch (IllegalArgumentException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Invalid argument at [{}]: {}", joinPoint.getSignature(), ex.getMessage());
      }
      return build(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error("Unhandled exception at [{}]", joinPoint.getSignature(), ex);
      }
      return build(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "INTERNAL_ERROR",
          "An unexpected system error occurred.");
    }
  }

  private ResponseEntity<UnifiedResponse<?>> mapDomainException(
      DomainException ex, ProceedingJoinPoint joinPoint) {
    if (ex instanceof DoubleEntryImbalanceException) {
      if (log.isErrorEnabled()) {
        log.error("Ledger imbalance at [{}]: {}", joinPoint.getSignature(), ex.getMessage());
      }
      return build(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "LEDGER_CORRUPTED",
          "Transaction could not be balanced.");
    }
    if (ex instanceof WalletFrozenException) {
      return build(HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage());
    }
    if (ex instanceof InsufficientFundException
        || ex instanceof LimitExceededException
        || ex instanceof HoldExpiredException) {
      return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getErrorCode(), ex.getMessage());
    }
    if (ex instanceof TransactionAlreadySettledException
        || ex instanceof InvalidHoldStateException
        || ex instanceof InvalidWalletStateException) {
      return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage());
    }
    return build(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
  }

  private ResponseEntity<UnifiedResponse<?>> build(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(UnifiedResponse.fail(code, message));
  }
}
