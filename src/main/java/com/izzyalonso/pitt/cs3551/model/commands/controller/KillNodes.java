package com.izzyalonso.pitt.cs3551.model.commands.controller;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;

@AutoValue
public abstract class KillNodes extends JsonConvertible {
    public static KillNodes create() {
        return new AutoValue_KillNodes();
    }

    public static TypeAdapter<KillNodes> typeAdapter(Gson gson) {
        return new AutoValue_KillNodes.GsonTypeAdapter(gson);
    }
}
