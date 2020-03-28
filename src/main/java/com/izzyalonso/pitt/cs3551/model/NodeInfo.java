package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;

@AutoValue
public abstract class NodeInfo extends JsonConvertible {
    @Nullable
    public abstract Integer id();
    public abstract String address();
    public abstract int port();


    @NonNull
    public static NodeInfo create(@NonNull String address, int port) {
        return new AutoValue_NodeInfo(null, address, port);
    }

    @NonNull
    public static NodeInfo create(int id, @NonNull NodeInfo machine) {
        return new AutoValue_NodeInfo(id, machine.address(), machine.port());
    }

    public static TypeAdapter<NodeInfo> typeAdapter(Gson gson) {
        return new AutoValue_NodeInfo.GsonTypeAdapter(gson);
    }
}
