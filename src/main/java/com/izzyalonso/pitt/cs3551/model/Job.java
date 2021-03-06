package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;


@AutoValue
public abstract class Job extends JsonConvertible {
    public abstract int id();
    public abstract Type type();
    public abstract int input();


    long weight() {
        switch (type()) {
            case FIBONACCI:
                return input()-1;

            case ERATOSTHENES:
                return (long)Math.ceil(input()*Math.log(input()));

            case SQUARE_SUM:
                return input();

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

    public static TypeAdapter<Job> typeAdapter(Gson gson) {
        return new AutoValue_Job.GsonTypeAdapter(gson);
    }

    private static int nextId = 0;

    private static synchronized int createId() {
        return nextId++;
    }

    public enum Type {
        FIBONACCI, ERATOSTHENES, SQUARE_SUM
    }
}
