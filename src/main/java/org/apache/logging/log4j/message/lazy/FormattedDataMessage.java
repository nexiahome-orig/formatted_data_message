package org.apache.logging.log4j.message.lazy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.vlkan.log4j2.logstash.layout.util.Streamable;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.IOException;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.AsynchronouslyFormattable;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.StructuredDataId;
import org.apache.logging.log4j.util.Chars;
import org.apache.logging.log4j.util.EnglishEnums;
import org.apache.logging.log4j.util.IndexedReadOnlyStringMap;
import org.apache.logging.log4j.util.StringBuilders;

@AsynchronouslyFormattable
public class FormattedDataMessage extends MapMessage<FormattedDataMessage, Object>
    implements Streamable {
  private static final long serialVersionUID = -598540466042791478L;

  private static final Pattern RE =
      Pattern.compile(
          "\\\\(.)"
              + // Treat any character after a backslash literally
              "|"
              + "(%\\(([^)]+)\\))" // Look for %(keys) to replace
          );
  private static final Logger logger = LogManager.getLogger(FormattedDataMessage.class.getName());

  private static Class<?> formatterClass;
  private static Method recursiveDeepToStringMethod;
  private Object2ObjectArrayMap<String, String> cachedStringMap;

  static {
    try {
      formatterClass = Class.forName("org.apache.logging.log4j.message.ParameterFormatter");
      Method[] methods = formatterClass.getDeclaredMethods();
      recursiveDeepToStringMethod =
          Arrays.stream(methods)
              .filter(m -> m.getName() == "recursiveDeepToString")
              .findFirst()
              .get();
      recursiveDeepToStringMethod.setAccessible(true);
    } catch (InaccessibleObjectException re) {
      logger.fatal(re);
    } catch (SecurityException se) {
      logger.fatal("Can't make recursiveDeepToString public!", se);
    } catch (ClassNotFoundException cnfe) {
      logger.fatal("Can't load ParameterFormatter class!", cnfe);
    }
  }

  protected synchronized void recursiveDeepToString(
      Object value, StringBuilder sb, String cacheKey) {
    if (cachedStringMap.containsKey(cacheKey)) {
      sb.append(cachedStringMap.get(cacheKey));
      return;
    }

    try {
      int start = sb.length();
      recursiveDeepToStringMethod.invoke(null, value, sb, null);
      cachedStringMap.put(cacheKey, sb.substring(start));
    } catch (ReflectiveOperationException e) {
      logger.fatal("Can't get at ParameterFormatter.recursiveDeepToString()", e);
    }
  }

  private String formatValueMatch(Map<String, Object> values, String key, Object defaultVal) {
    StringBuilder sb = new StringBuilder();
    Object value = values.get(key);
    if (value == null) {
      value = ThreadContext.get(key);
    }
    if (value == null) {
      value = defaultVal;
    }
    recursiveDeepToString(value, sb, key);
    return sb.toString();
  }

  protected String formatMessage(String fmt, Map<String, Object> values) {
    return RE.matcher(fmt)
        .replaceAll(
            match ->
                match.group(1) != null
                    ? Matcher.quoteReplacement(match.group(1))
                    : Matcher.quoteReplacement(
                        formatValueMatch(values, match.group(3), match.group(2))));
  }

  private static final int MAX_LENGTH = 32;
  private static final int HASHVAL = 31;
  private static final List<String> RESERVED_KEYS =
      Arrays.asList("type", "id", "message", "template");

  private StructuredDataId id;

  private String template;

  private String type;

  private final int maxLength;

  /** Supported formats. */
  public enum Format {
    /** The map should be formatted as XML. */
    XML,

    /** The map should be formatted as interpolated XML. */
    INTERPOLATED_XML,

    /** The map should be formatted as JSON. */
    JSON,

    /** The map should be formatted as interpolated JSON. */
    INTERPOLATED_JSON,

    /** Full message format includes the type and message. */
    FULL;

    /**
     * Maps a format name to an {@link Format} while ignoring case.
     *
     * @param format a Format name
     * @return a Format
     */
    public static Format lookupIgnoreCase(final String format) {
      return XML.name().equalsIgnoreCase(format)
          ? XML //
          : INTERPOLATED_XML.name().equalsIgnoreCase(format)
              ? INTERPOLATED_XML //
              : JSON.name().equalsIgnoreCase(format)
                  ? JSON //
                  : INTERPOLATED_JSON.name().equalsIgnoreCase(format)
                      ? INTERPOLATED_JSON //
                      : FULL.name().equalsIgnoreCase(format)
                          ? FULL //
                          : null;
    }

    /**
     * All {@code Format} names.
     *
     * @return All {@code Format} names.
     */
    public static String[] names() {
      return new String[] {
        XML.name(), INTERPOLATED_XML.name(), JSON.name(), INTERPOLATED_JSON.name(), FULL.name()
      };
    }
  }

  /**
   * Creates a FormattedDataMessage using an ID (max 32 characters), message, and type (max 32
   * characters).
   *
   * @param id The String id.
   * @param msg The message.
   * @param type The message type.
   */
  public FormattedDataMessage(final String id, final String msg, final String type) {
    this(id, msg, type, MAX_LENGTH);
  }

  /**
   * Creates a FormattedDataMessage using an ID (user specified max characters), message, and type
   * (user specified maximum number of characters).
   *
   * @param id The String id.
   * @param msg The message.
   * @param type The message type.
   * @param maxLength The maximum length of keys;
   * @since 2.9
   */
  public FormattedDataMessage(
      final String id, final String msg, final String type, final int maxLength) {
    this.id = new StructuredDataId(id, null, null, maxLength);
    this.template = msg;
    this.type = type;
    this.maxLength = maxLength;
  }

  /**
   * Creates a FormattedDataMessage using an ID (max 32 characters), message, type (max 32
   * characters), and an initial map of structured data to include.
   *
   * @param id The String id.
   * @param msg The message.
   * @param type The message type.
   * @param data The StructuredData map.
   */
  public FormattedDataMessage(
      final String id, final String msg, final String type, final Map<String, Object> data) {
    this(id, msg, type, data, MAX_LENGTH);
  }

  /**
   * Creates a FormattedDataMessage using an (user specified max characters), message, and type
   * (user specified maximum number of characters, and an initial map of structured data to include.
   *
   * @param id The String id.
   * @param msg The message.
   * @param type The message type.
   * @param data The StructuredData map.
   * @param maxLength The maximum length of keys;
   * @since 2.9
   */
  public FormattedDataMessage(
      final String id,
      final String msg,
      final String type,
      final Map<String, Object> data,
      final int maxLength) {
    super(data);
    this.id = new StructuredDataId(id, null, null, maxLength);
    this.template = msg;
    this.type = type;
    this.maxLength = maxLength;
    this.cachedStringMap = new Object2ObjectArrayMap<>(data.size() + 2);
  }

  /**
   * Creates a FormattedDataMessage using a StructuredDataId, message, and type (max 32 characters).
   *
   * @param id The StructuredDataId.
   * @param msg The message.
   * @param type The message type.
   */
  public FormattedDataMessage(final StructuredDataId id, final String msg, final String type) {
    this(id, msg, type, MAX_LENGTH);
  }

  /**
   * Creates a FormattedDataMessage using a StructuredDataId, message, and type (max 32 characters).
   *
   * @param id The StructuredDataId.
   * @param msg The message.
   * @param type The message type.
   * @param maxLength The maximum length of keys;
   * @since 2.9
   */
  public FormattedDataMessage(
      final StructuredDataId id, final String msg, final String type, final int maxLength) {
    this.id = id;
    this.template = msg;
    this.type = type;
    this.maxLength = maxLength;
  }

  /**
   * Creates a FormattedDataMessage using a StructuredDataId, message, type (max 32 characters), and
   * an initial map of structured data to include.
   *
   * @param id The StructuredDataId.
   * @param msg The message.
   * @param type The message type.
   * @param data The StructuredData map.
   */
  public FormattedDataMessage(
      final StructuredDataId id,
      final String msg,
      final String type,
      final Map<String, Object> data) {
    this(id, msg, type, data, MAX_LENGTH);
  }

  /**
   * Creates a FormattedDataMessage using a StructuredDataId, message, type (max 32 characters), and
   * an initial map of structured data to include.
   *
   * @param id The StructuredDataId.
   * @param msg The message.
   * @param type The message type.
   * @param data The StructuredData map.
   * @param maxLength The maximum length of keys;
   * @since 2.9
   */
  public FormattedDataMessage(
      final StructuredDataId id,
      final String msg,
      final String type,
      final Map<String, Object> data,
      final int maxLength) {
    super(data);
    this.id = id;
    this.template = msg;
    this.type = type;
    this.maxLength = maxLength;
  }

  /**
   * Constructor based on a FormattedDataMessage.
   *
   * @param msg The FormattedDataMessage.
   * @param map The StructuredData map.
   */
  private FormattedDataMessage(final FormattedDataMessage msg, final Map<String, Object> map) {
    super(map);
    this.id = msg.id;
    this.template = msg.template;
    this.type = msg.type;
    this.maxLength = MAX_LENGTH;
  }

  /** Basic constructor. */
  protected FormattedDataMessage() {
    maxLength = MAX_LENGTH;
  }

  /**
   * Returns the supported formats.
   *
   * @return An array of the supported format names.
   */
  @Override
  public String[] getFormats() {
    return Format.names();
  }

  /**
   * Returns this message id.
   *
   * @return the StructuredDataId.
   */
  public StructuredDataId getId() {
    return id;
  }

  /**
   * Sets the id from a String. This ID can be at most 32 characters long.
   *
   * @param id The String id.
   */
  protected void setId(final String id) {
    this.id = new StructuredDataId(id, null, null);
  }

  /**
   * Sets the id.
   *
   * @param id The StructuredDataId.
   */
  protected void setId(final StructuredDataId id) {
    this.id = id;
  }

  /**
   * Returns this message type.
   *
   * @return the type.
   */
  public String getType() {
    return type;
  }

  protected void setType(final String type) {
    if (type.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "structured data type exceeds maximum length of 32 characters: " + type);
    }
    this.type = type;
  }

  @Override
  public void formatTo(final StringBuilder buffer) {
    asString(Format.FULL, null, buffer);
  }

  @Override
  public void formatTo(String[] formats, StringBuilder buffer) {
    asString(getFormat(formats), null, buffer);
  }

  /**
   * Returns the message, interpolated.
   *
   * @return the message, interpolated.
   */
  @Override
  public String getFormat() {
    return formatMessage(template, getData());
  }

  protected void setMessageFormat(final String msg) {
    this.template = msg;
  }

  /**
   * Formats the structured data as described in RFC 5424.
   *
   * @return The formatted String.
   */
  @Override
  public String asString() {
    return asString(Format.FULL, null);
  }

  /**
   * Formats the structured data as described in RFC 5424.
   *
   * @param format The format identifier. Ignored in this implementation.
   * @return The formatted String.
   */
  @Override
  public String asString(final String format) {
    try {
      return asString(EnglishEnums.valueOf(Format.class, format), null);
    } catch (final IllegalArgumentException ex) {
      return asString();
    }
  }

  /**
   * Formats the structured data as described in RFC 5424.
   *
   * @param format "full" will include the type and message. null will return only the
   *     STRUCTURED-DATA as described in RFC 5424
   * @param structuredDataId The SD-ID as described in RFC 5424. If null the value in the
   *     StructuredData will be used.
   * @return The formatted String.
   */
  public final String asString(final Format format, final StructuredDataId structuredDataId) {
    final StringBuilder sb = new StringBuilder();
    asString(format, structuredDataId, sb);
    return sb.toString();
  }

  /**
   * Formats the structured data as described in RFC 5424.
   *
   * @param format "full" will include the type and message. null will return only the
   *     STRUCTURED-DATA as described in RFC 5424
   * @param structuredDataId The SD-ID as described in RFC 5424. If null the value in the
   *     StructuredData will be used.
   * @param sb The StringBuilder to append the formatted message to.
   */
  public final void asString(
      final Format format, final StructuredDataId structuredDataId, final StringBuilder sb) {
    final boolean full = Format.FULL.equals(format);
    if (full) {
      final String myType = getType();
      if (myType == null) {
        return;
      }
      sb.append(getType()).append(' ');
    }
    StructuredDataId sdId = getId();
    if (sdId != null) {
      sdId = sdId.makeId(structuredDataId); // returns sdId if structuredDataId is null
    } else {
      sdId = structuredDataId;
    }
    if (sdId == null || sdId.getName() == null) {
      return;
    }
    if (Format.XML.equals(format)) {
      asXml(sdId, template, sb);
      return;
    }
    if (Format.INTERPOLATED_XML.equals(format)) {
      asXml(sdId, getFormat(), sb);
      return;
    }
    if (Format.JSON.equals(format)) {
      asJson(sdId, template, sb);
      return;
    }
    if (Format.INTERPOLATED_JSON.equals(format)) {
      asJson(sdId, getFormat(), sb);
      return;
    }
    sb.append('[');
    StringBuilders.appendValue(sb, sdId); // avoids toString if implements StringBuilderFormattable
    sb.append(' ');
    appendMap(sb);
    sb.append(']');
    if (full) {
      final String msg = getFormat();
      if (msg != null) {
        sb.append(' ').append(msg);
      }
    }
  }

  protected void asXml(StructuredDataId structuredDataId, String message, StringBuilder sb) {
    sb.append("<StructuredData>\n");
    sb.append("<type>").append(type).append("</type>\n");
    sb.append("<id>").append(structuredDataId).append("</id>\n");
    sb.append("<message>").append(message).append("</message>\n");
    sb.append("<Map>\n");

    IndexedReadOnlyStringMap data = getIndexedReadOnlyStringMap();
    for (int i = 0; i < data.size(); i++) {
      sb.append("  <Entry key=\"").append(data.getKeyAt(i)).append("\">");
      int size = sb.length();
      recursiveDeepToString(data.getValueAt(i), sb, data.getKeyAt(i));
      StringBuilders.escapeXml(sb, size);
      sb.append("</Entry>\n");
    }
    sb.append("</Map>");
    sb.append("\n</StructuredData>\n");
  }

  protected void asJson(StructuredDataId structuredDataId, String message, StringBuilder sb) {
    sb.append('{');
    sb.append(Chars.DQUOTE);
    sb.append("type");
    sb.append(Chars.DQUOTE).append(':').append(Chars.DQUOTE);
    int start = sb.length();
    sb.append(type);
    StringBuilders.escapeJson(sb, start);
    sb.append(Chars.DQUOTE).append(", ").append(Chars.DQUOTE);
    sb.append("id");
    sb.append(Chars.DQUOTE).append(':').append(Chars.DQUOTE);
    start = sb.length();
    sb.append(structuredDataId);
    StringBuilders.escapeJson(sb, start);
    sb.append(Chars.DQUOTE).append(", ").append(Chars.DQUOTE);
    sb.append("message");
    sb.append(Chars.DQUOTE).append(':').append(Chars.DQUOTE);
    start = sb.length();
    sb.append(message);
    StringBuilders.escapeJson(sb, start);
    sb.append(Chars.DQUOTE);
    IndexedReadOnlyStringMap data = getIndexedReadOnlyStringMap();
    for (int i = 0; i < data.size(); i++) {
      sb.append(", ");
      sb.append(Chars.DQUOTE);
      start = sb.length();
      sb.append(data.getKeyAt(i));
      StringBuilders.escapeJson(sb, start);
      sb.append(Chars.DQUOTE).append(':').append(Chars.DQUOTE);
      start = sb.length();
      recursiveDeepToString(data.getValueAt(i), sb, data.getKeyAt(i));
      StringBuilders.escapeJson(sb, start);
      sb.append(Chars.DQUOTE);
    }
    sb.append('}');
  }

  @Override
  public void streamTo(JsonGenerator stream) throws IOException {
    stream.writeStartObject();
    stream.writeStringField("type", type);
    stream.writeStringField("id", id.toString());
    stream.writeStringField("message", getFormat());
    stream.writeStringField("template", template);
    IndexedReadOnlyStringMap data = getIndexedReadOnlyStringMap();
    for (int i = 0; i < data.size(); i++) {
      StringBuilder sb = new StringBuilder();
      recursiveDeepToString(data.getValueAt(i), sb, data.getKeyAt(i));
      stream.writeStringField(data.getKeyAt(i), sb.toString());
    }
    stream.writeEndObject();
  }

  /**
   * Formats the message and return it.
   *
   * @return the formatted message.
   */
  @Override
  public String getFormattedMessage() {
    return asString(Format.FULL, null);
  }

  /**
   * Formats the message according the the specified format.
   *
   * @param formats An array of Strings that provide extra information about how to format the
   *     message. FormattedDataMessage accepts only a format of "FULL" which will cause the event
   *     type to be prepended and the event message to be appended. Specifying any other value will
   *     cause only the StructuredData to be included. The default is "FULL".
   * @return the formatted message.
   */
  @Override
  public String getFormattedMessage(final String[] formats) {
    return asString(getFormat(formats), null);
  }

  private Format getFormat(final String[] formats) {
    if (formats == null || formats.length == 0) {
      return Format.FULL;
    }
    for (int i = 0; i < formats.length; i++) {
      final Format mapFormat = Format.lookupIgnoreCase(formats[i]);
      if (mapFormat != null) {
        return mapFormat;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return asString(null, null);
  }

  @Override
  public FormattedDataMessage newInstance(final Map<String, Object> map) {
    return new FormattedDataMessage(this, map);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final FormattedDataMessage that = (FormattedDataMessage) o;

    if (!super.equals(o)) {
      return false;
    }
    if (type != null ? !type.equals(that.type) : that.type != null) {
      return false;
    }
    if (id != null ? !id.equals(that.id) : that.id != null) {
      return false;
    }
    if (template != null ? !template.equals(that.template) : that.template != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = HASHVAL * result + (type != null ? type.hashCode() : 0);
    result = HASHVAL * result + (id != null ? id.hashCode() : 0);
    result = HASHVAL * result + (template != null ? template.hashCode() : 0);
    return result;
  }

  @Override
  protected void validate(final String key, final boolean value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final byte value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final char value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final double value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final float value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final int value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final long value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final Object value) {
    validateKey(key);
  }

  /** @since 2.9 */
  @Override
  protected void validate(final String key, final short value) {
    validateKey(key);
  }

  @Override
  protected void validate(final String key, final String value) {
    validateKey(key);
  }

  protected void validateKey(final String key) {
    if (maxLength > 0 && key.length() > maxLength) {
      throw new IllegalArgumentException(
          "Structured data keys are limited to " + maxLength + " characters. key: " + key);
    }
    for (int i = 0; i < key.length(); i++) {
      final char c = key.charAt(i);
      if (c < '!' || c > '~' || c == '=' || c == ']' || c == '"') {
        throw new IllegalArgumentException(
            "Structured data keys must contain printable US ASCII characters"
                + "and may not contain a space, =, ], or \"");
      }
    }
    if (RESERVED_KEYS.contains(key)) {
      throw new IllegalArgumentException(
          "Structured data keys " + RESERVED_KEYS + " are reserved. key: " + key);
    }
  }
}
