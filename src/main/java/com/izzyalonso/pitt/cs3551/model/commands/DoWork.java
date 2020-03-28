package com.izzyalonso.pitt.cs3551.model.commands;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;


@AutoValue
public abstract class DoWork extends JsonConvertible {

    public static TypeAdapter<DoWork> typeAdapter(Gson gson) {
        return new AutoValue_DoWork.GsonTypeAdapter(gson);
    }
}
