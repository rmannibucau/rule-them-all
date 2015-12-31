package com.github.rmannibucau.rules.api.dbunit;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, METHOD })
@Retention(RUNTIME)
public @interface DbUnitInstance {
    String value() default "";
}
