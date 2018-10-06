package org.apache.logging.log4j.message;

import java.util.Map;
import java.util.regex.Pattern;

public class FormattedDataMessage extends StructuredDataMessage {
  private static final Pattern RE = Pattern.compile(
      "\\\\(.)" +         // Treat any character after a backslash literally
          "|" +
          "(%\\(([^)]+)\\))"  // Look for %(keys) to replace
  );

  protected 	FormattedDataMessage() { super(); }

  FormattedDataMessage(String id, String msg, String type) { super(id, msg, type); }
  FormattedDataMessage(String id, String msg, String type, int maxLength) { super(id, msg, type, maxLength); }
  FormattedDataMessage(String id, String msg, String type, Map<String,String> data) { super(id, msg, type, data); }
  FormattedDataMessage(String id, String msg, String type, Map<String,String> data, int maxLength) { super(id, msg, type, data, maxLength); }
  FormattedDataMessage(StructuredDataId id, String msg, String type) { super(id, msg, type); }
  FormattedDataMessage(StructuredDataId id, String msg, String type, int maxLength) { super(id, msg, type, maxLength); }
  FormattedDataMessage(StructuredDataId id, String msg, String type, Map<String,String> data) { super(id, msg, type, data); }
  FormattedDataMessage(StructuredDataId id, String msg, String type, Map<String,String> data, int maxLength) { super(id, msg, type, data, maxLength); }



  @Override
  public String getFormat() {
    return format(super.getFormat(), getData());
  }

  private static String format(String fmt, Map<String, String> values) {
    return RE.matcher(fmt).replaceAll(match ->
        match.group(1) != null ?
            match.group(1) :
            formatValueMatch(values, match.group(3), match.group(2))
    );
  }

  private static String formatValueMatch(Map<String, String> values, String key, String defaultVal) {
    StringBuilder sb = new StringBuilder();
    ParameterFormatter.recursiveDeepToString(values.getOrDefault(key, defaultVal), sb, null);
    return sb.toString();
  }
}
