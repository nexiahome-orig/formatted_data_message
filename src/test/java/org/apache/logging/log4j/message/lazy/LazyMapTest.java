package org.apache.logging.log4j.message.lazy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.apache.logging.log4j.message.lazy.LazyMap.lazy;
import static org.apache.logging.log4j.message.lazy.LazyMap.entry;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

public class LazyMapTest {
  Supplier<String> stringSupplier = new Supplier<String>() {
    @Override
    public String get() {
      return "value";
    }
  };

  @Nested
  @DisplayName("#lazy")
  class Lazy {
    @Test
    void itDoesNotCallTheSupplierOnInsert() {
      Supplier<String> mockStringSupplier = spy(stringSupplier);

      Map<String, Object> lazyMap = Map.ofEntries(lazy("key", mockStringSupplier));

      verify(mockStringSupplier, never()).get();
    }

    @Test
    void itCallsTheSupplierOnToString() {
      Supplier<String> mockStringSupplier = spy(stringSupplier);

      Map<String, Object> lazyMap = Map.ofEntries(lazy("key", mockStringSupplier));
      lazyMap.get("key").toString();

      verify(mockStringSupplier).get();
    }
  }

  @Nested
  @DisplayName("#entry")
  class Entry {
    @Test
    void itStringifiesNullValues() {
      Map<String, Object> lazyMap = Map.ofEntries(entry("key", null));

      assertThat(lazyMap.get("key"), is(equalTo("null")));
    }

    @Test
    void itWorksAsNormalOtherwise() {
      Supplier<String> mockStringSupplier = spy(stringSupplier);

      Map<String, Object> lazyMap = Map.ofEntries(entry("key", "value"));

      assertThat(lazyMap.get("key").toString(), is(equalTo("value")));
    }
  }
}
