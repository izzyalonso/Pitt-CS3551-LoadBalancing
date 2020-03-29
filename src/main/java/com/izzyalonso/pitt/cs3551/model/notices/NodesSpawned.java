package com.izzyalonso.pitt.cs3551.model.notices;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;
import com.izzyalonso.pitt.cs3551.model.NodeInfo;

import java.util.List;

@AutoValue
public abstract class NodesSpawned extends JsonConvertible {
    public abstract List<NodeInfo> nodes();

    public static NodesSpawned create(List<NodeInfo> nodes) {
        return new AutoValue_NodesSpawned(nodes);
    }

    public static TypeAdapter<NodesSpawned> typeAdapter(Gson gson) {
        return new AutoValue_NodesSpawned.GsonTypeAdapter(gson);
    }
}
