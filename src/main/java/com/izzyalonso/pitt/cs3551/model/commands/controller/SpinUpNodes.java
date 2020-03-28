package com.izzyalonso.pitt.cs3551.model.commands.controller;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;

@AutoValue
public abstract class SpinUpNodes extends JsonConvertible {
    public abstract int nodeCount();


    public static SpinUpNodes create(int nodeCount) {
        return new AutoValue_SpinUpNodes(nodeCount);
    }

    public static TypeAdapter<SpinUpNodes> typeAdapter(Gson gson) {
        return new AutoValue_SpinUpNodes.GsonTypeAdapter(gson);
    }
}
