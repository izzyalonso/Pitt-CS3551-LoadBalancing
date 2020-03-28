package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;

@AutoValue
public abstract class Work {
    public abstract String type();
    public abstract int input();


    @NonNull
    public static Work create(@NonNull String type, int input) {
        return new AutoValue_Work(type, input);
    }
}
