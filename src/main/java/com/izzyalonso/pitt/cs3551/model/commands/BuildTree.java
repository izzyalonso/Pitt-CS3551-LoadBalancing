package com.izzyalonso.pitt.cs3551.model.commands;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;
import com.izzyalonso.pitt.cs3551.model.NodeInfo;

import java.util.List;

@AutoValue
public abstract class BuildTree extends JsonConvertible {
    public abstract int branchingFactor();
    public abstract List<NodeInfo> nodes();

    public BuildTree create(int branchingFactor, List<NodeInfo> nodes) {
        return new AutoValue_BuildTree(branchingFactor, nodes);
    }

    public static TypeAdapter<BuildTree> typeAdapter(Gson gson) {
        return new AutoValue_BuildTree.GsonTypeAdapter(gson);
    }
}
