package com.exonum.binding.qaservice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates that it might make sense to promote the annotated element
 * to the core Exonum Java Binding library.
 */
@Target({
    ElementType.TYPE,
    ElementType.METHOD
})
public @interface PromoteToCore {
  String value() default "";
}