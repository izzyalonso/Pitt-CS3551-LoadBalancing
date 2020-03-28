package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;

import java.util.List;


@AutoValue
public abstract class TreeNode extends JsonConvertible {
    abstract NodeInfo leader();
    @Nullable
    abstract NodeInfo parent();
    @Nullable abstract List<TreeNode> children();

    @NonNull
    static TreeNode createLeaf(@NonNull NodeInfo leader) {
        return null;//new AutoValue_TreeNode(leader, null);
    }

    @NonNull
    static TreeNode create(@NonNull NodeInfo leader, @NonNull List<TreeNode> children) {
        return null;//new AutoValue_TreeNode(leader, children);
    }

    public static TypeAdapter<TreeNode> typeAdapter(Gson gson) {
        return new AutoValue_TreeNode.GsonTypeAdapter(gson);
    }
}
