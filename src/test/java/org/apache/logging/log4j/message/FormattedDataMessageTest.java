package org.apache.logging.log4j.message;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FormattedDataMessageTest {
  FormattedDataMessage message;
  String messageId = "a message ID";
  String messageFormat;
  String messageType = "a message Type";
  Map<String, String> dataFields;

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
}
