package org.aegiskb.util;

@FunctionalInterface
public interface TaskCall<V> {

    V call();
}
