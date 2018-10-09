package org.apache.logging.log4j.message.lazy;

import java.util.Map;
import java.util.function.Supplier;

public final class LazyMap {
  static final class LazyString<V> {
    private Supplier<V> supplier;
    private V value;

    LazyString(Supplier<V> s) { supplier = s; }

    @Override
    public String toString() { return String.valueOf(getValue()); }

    V getValue() {
      if (value == null) {
        value = supplier.get();
      }
      return value;
    }
  }

  public static <V> Map.Entry<String,Object> lazy(String k, Supplier<V> v) {
    LazyString<V> stringifiableV = new LazyString<V>(v);
    return Map.entry(k, stringifiableV);
  }

  public static <V> Map.Entry<String,Object> entry(String k, V v) {
    return Map.entry(k, String.valueOf(v));
  }
}
