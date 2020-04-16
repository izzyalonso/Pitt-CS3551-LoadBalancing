package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;


@AutoValue
public abstract class Job extends JsonConvertible {
    public abstract int id();
    public abstract Type type();
    public abstract int input();


    int weight() {
        switch (type()) {
            case FIBONACCI:
                return input();

            case ERATOSTHENES:
                return (int)Math.ceil(input()*Math.log(input()));

            case SQUARE_SUM:
                return input()*input();

            default:
                return 0; // Not a thing, type is enum.
        }
    }

    public JobInfo getInfo(@NonNull NodeInfo owner) {
        return JobInfo.create(this, owner);
    }

    @NonNull
    public static Job create(@NonNull Type type, int input) {
        return new AutoValue_Job(createId(), type, input);
    }

    private static int nextId = 0;

    private static synchronized int createId() {
        return nextId++;
    }

    public enum Type {
        FIBONACCI, ERATOSTHENES, SQUARE_SUM
    }
}
