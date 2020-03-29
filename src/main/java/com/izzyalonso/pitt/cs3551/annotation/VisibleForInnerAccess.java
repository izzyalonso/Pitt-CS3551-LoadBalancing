package com.izzyalonso.pitt.cs3551.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Just to signal a method is visible as a micro-optimization.
 *
 * Methods annotated with this SHOULD NOT be called outside of the file they're declared in.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface VisibleForInnerAccess {}
