package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;


@AutoValue
public abstract class Work {
    public abstract Type type();
    public abstract int input();


    @NonNull
    public static Work create(@NonNull Type type, int input) {
        return new AutoValue_Work(type, input);
    }


    public enum Type {
        FIBONACCI, ERATOSTHENES, SQUARE_SUM
    }
}
