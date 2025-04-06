package com.hchen.collect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Collect {
    String targetPackage();

    boolean onLoadPackage() default true;

    boolean onZygote() default false;

    boolean onApplication() default false;
}