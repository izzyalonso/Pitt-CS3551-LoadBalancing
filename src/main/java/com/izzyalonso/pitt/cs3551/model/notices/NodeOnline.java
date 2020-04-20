package com.izzyalonso.pitt.cs3551.model.notices;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;


@AutoValue
public abstract class NodeOnline extends JsonConvertible {
    public abstract int id();
    public abstract int port();

    public static NodeOnline create(int id, int port) {
        return new AutoValue_NodeOnline(id, port);
    }

    public static TypeAdapter<NodeOnline> typeAdapter(Gson gson) {
        return new AutoValue_NodeOnline.GsonTypeAdapter(gson);
    }
}
