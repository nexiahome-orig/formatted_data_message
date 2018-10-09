package org.apache.logging.log4j.message.lazy;

import java.util.Map;
import static java.util.Map.entry;
import java.util.function.Supplier;

public final class LazyMap {
  static final class LazyString<V> {
    private Supplier<V> supplier;

    LazyString(Supplier<V> s) { supplier = s; }

    @Override
    public String toString() { return String.valueOf(supplier.get()); }
  }

  public static <V> Map.Entry<String,Object> lazy(String k, Supplier<V> v) {
    LazyString<V> stringifiableV = new LazyString<V>(v);
    return entry(k, stringifiableV);
  }

  public static <V> Map.Entry<String,Object> nonlazy(String k, V v) {
    return entry(k, String.valueOf(v));
  }
}
