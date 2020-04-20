package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

@AutoValue
public abstract class NodeInfo extends JsonConvertible implements Comparable<NodeInfo> {
    private static final Object lock = new Object();

    public abstract int id();
    public abstract String address();
    public abstract int port();


    @Override
    public int compareTo(@NotNull NodeInfo other) {
        return Integer.compare(id(), other.id());
    }

    @Override
    public final String toString() {
        return "Node#" + id();
    }

    @NonNull
    public static NodeInfo create(@NonNull String address, int port) {
        return new AutoValue_NodeInfo(createId(), address, port);
    }

    @NonNull
    public static NodeInfo create(int id, @NonNull String address, int port) {
        synchronized(lock) {
            if (nextId <= id) {
                nextId = id+1;
            }
        }
        return new AutoValue_NodeInfo(id, address, port);
    }

    private static int nextId = 0;

    private static int createId() {
        synchronized (lock) {
            return nextId++;
        }
    }

    public static TypeAdapter<NodeInfo> typeAdapter(Gson gson) {
        return new AutoValue_NodeInfo.GsonTypeAdapter(gson);
    }
}
