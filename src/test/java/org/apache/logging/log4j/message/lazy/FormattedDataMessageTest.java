package org.apache.logging.log4j.message.lazy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.core.LoggerContext;
//import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.message.lazy.FormattedDataMessage;

import java.util.Map;
import static java.util.Map.entry;

import com.vlkan.log4j2.logstash.layout.LogstashLayout;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FormattedDataMessageTest {
  private static final Logger logger = LogManager.getLogger(FormattedDataMessageTest.class.getName());
  FormattedDataMessage message;
  String messageId = "a_message_id";
  String messageType = "a_message_type";
  String messageFormat;
  Map<String, Object> dataFields;

//  @BeforeAll
//  static void setupAll() {
//    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
//    Configuration loggerConfig = loggerContext.getConfiguration();
//    LogstashLayout layout = LogstashLayout
//        .newBuilder()
//        .setConfiguration(loggerConfig)
//        .setTemplateUri("classpath:LogstashTestLayout.json")
//        .setStackTraceEnabled(true)
//        .setLocationInfoEnabled(true)
//        .build();
//  }

  @BeforeEach
  void setup() {
    messageFormat = "This is a message. a=%(a) b=%(b)";
    dataFields = Map.ofEntries(entry("a", "aVal"), entry("b", "bVal"), entry("c", "cVal"));
    message = new FormattedDataMessage(messageId, messageFormat, messageType, dataFields);
  }

  @Test
  void testMessageSubstitutesData() {
    assertThat(message.getFormat(), is(equalTo("This is a message. a=aVal b=bVal")));
  }

  @Test
  void testFullMessageSubstitutesData() {
    String[] formats = { "FULL" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("a_message_type [a_message_id a=\"aVal\" b=\"bVal\" c=\"cVal\"] This is a message. a=aVal b=bVal")));
  }

  @Test
  void testStructuredOnlyMessageOmitsMessage() {
    String[] formats = { "structured_only" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("[a_message_id a=\"aVal\" b=\"bVal\" c=\"cVal\"]")));
  }

  @Test
  void testDefaultFormatIsFull() {
    assertThat(message.getFormattedMessage(), is(equalTo("a_message_type [a_message_id a=\"aVal\" b=\"bVal\" c=\"cVal\"] This is a message. a=aVal b=bVal")));
  }

  @Test
  void testXmlDoesNotInterpolate() {
    String[] formats = { "XML" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("<StructuredData>\n<type>a_message_type</type>\n<id>a_message_id</id>\n<message>This is a message. a=%(a) b=%(b)</message>\n<Map>\n  <Entry key=\"a\">aVal</Entry>\n  <Entry key=\"b\">bVal</Entry>\n  <Entry key=\"c\">cVal</Entry>\n</Map>\n</StructuredData>\n")));
  }

  @Test
  void testJsonDoesNotInterpolate() {
    String[] formats = { "JSON" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("{\"type\":\"a_message_type\", \"id\":\"a_message_id\", \"message\":\"This is a message. a=%(a) b=%(b)\", \"a\":\"aVal\", \"b\":\"bVal\", \"c\":\"cVal\"}")));
  }

  @Test
  void testInterpolatedXmlInterpolates() {
    String[] formats = { "INTERPOLATED_XML" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("<StructuredData>\n<type>a_message_type</type>\n<id>a_message_id</id>\n<message>This is a message. a=aVal b=bVal</message>\n<Map>\n  <Entry key=\"a\">aVal</Entry>\n  <Entry key=\"b\">bVal</Entry>\n  <Entry key=\"c\">cVal</Entry>\n</Map>\n</StructuredData>\n")));
  }

  @Test
  void testInterpolatedJsonInterpolates() {
    String[] formats = { "INTERPOLATED_JSON" };
    assertThat(message.getFormattedMessage(formats), is(equalTo("{\"type\":\"a_message_type\", \"id\":\"a_message_id\", \"message\":\"This is a message. a=aVal b=bVal\", \"a\":\"aVal\", \"b\":\"bVal\", \"c\":\"cVal\"}")));
  }

  @Test
  void loggerLogs() {
    logger.info(message);
  }
}
