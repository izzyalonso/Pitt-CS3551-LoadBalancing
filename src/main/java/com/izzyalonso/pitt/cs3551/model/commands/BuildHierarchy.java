package com.izzyalonso.pitt.cs3551.model.commands;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;
import com.izzyalonso.pitt.cs3551.model.NodeInfo;

import java.util.List;

@AutoValue
public abstract class BuildHierarchy extends JsonConvertible {
    public abstract int branchingFactor();
    public abstract List<NodeInfo> nodes();

    public static BuildHierarchy create(int branchingFactor, List<NodeInfo> nodes) {
        return new AutoValue_BuildHierarchy(branchingFactor, nodes);
    }

    public static TypeAdapter<BuildHierarchy> typeAdapter(Gson gson) {
        return new AutoValue_BuildHierarchy.GsonTypeAdapter(gson);
    }
}
