package org.apache.logging.log4j.message.lazy;

import java.util.Map;
import static java.util.Map.entry;
import java.util.function.Supplier;

public class LazyMap {
  static class LazyString<V> {
    private Supplier<V> supplier;

    LazyString(Supplier<V> s) { supplier = s; }

    @Override
    public String toString() { return String.valueOf(supplier.get()); }
  }

  public static <V> Map.Entry<String,Object> lazy(String k, Supplier<V> v) {
    LazyString<V> stringifiableV = new LazyString<V>(v);
    return entry(k, stringifiableV);
  }
}
