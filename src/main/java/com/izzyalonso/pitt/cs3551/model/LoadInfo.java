package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;

@AutoValue
public abstract class LoadInfo extends JsonConvertible {
    public abstract NodeInfo node();
    public abstract double load();


    public static LoadInfo create(@NonNull NodeInfo node, double load) {
        return new AutoValue_LoadInfo(node, load);
    }

    public static TypeAdapter<LoadInfo> typeAdapter(Gson gson) {
        return new AutoValue_LoadInfo.GsonTypeAdapter(gson);
    }
}
