package config.time;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateTimeManager {
  // Standard ISO-8601 Formatter for Year-Month-Day
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // For audit trails / ticks
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public static String formatBusinessDate(LocalDate date) {
    if (date == null) return null;
    return date.format(DATE_FORMATTER); // Always outputs e.g., 2026-10-15
  }

  public static String formatAuditTimestamp(OffsetDateTime timestamp) {
    if (timestamp == null) return null;
    // Convert to UTC for institutional consistency
    return timestamp.withOffsetSameInstant(ZoneOffset.UTC).format(TIMESTAMP_FORMATTER);
  }
}
