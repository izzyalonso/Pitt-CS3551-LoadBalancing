package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;


@AutoValue
public abstract class TreeNode extends JsonConvertible {
    public abstract NodeInfo node();
    @Nullable
    public abstract NodeInfo parent(); // <- null at the root
    public abstract List<TreeNode> children(); // <- empty at the leafs

    public boolean isLeaf() {
        return children().isEmpty();
    }

    @NonNull
    public TreeNode assignParent(@NonNull NodeInfo parent) {
        return new AutoValue_TreeNode(node(), parent, children());
    }

    @NonNull
    public static TreeNode create(@NonNull NodeInfo node) {
        return new AutoValue_TreeNode(node, null, new ArrayList<>());
    }

    public static TypeAdapter<TreeNode> typeAdapter(Gson gson) {
        return new AutoValue_TreeNode.GsonTypeAdapter(gson);
    }
}
