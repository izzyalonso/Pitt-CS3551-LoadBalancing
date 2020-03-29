package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;
import com.izzyalonso.pitt.cs3551.model.commands.BuildTree;
import com.izzyalonso.pitt.cs3551.model.commands.DoWork;
import com.izzyalonso.pitt.cs3551.model.commands.SendNodeInfo;
import com.izzyalonso.pitt.cs3551.model.commands.controller.KillNodes;
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes;
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline;
import com.izzyalonso.pitt.cs3551.model.notices.NodesSpawned;
import com.izzyalonso.pitt.cs3551.model.notices.ResponseMessage;

@AutoValue
public abstract class Message extends JsonConvertible {
    // Messages for the node controller
    @Nullable public abstract SpinUpNodes spinUpNodes();
    @Nullable public abstract KillNodes killNodes();
    @Nullable public abstract NodeOnline nodeOnline();

    // Messages from the node controller
    @Nullable public abstract NodesSpawned nodesSpawned();
    @Nullable public abstract ResponseMessage responseMessage();

    // Messages for a node
    @Nullable public abstract BuildTree buildTree();
    @Nullable public abstract SendNodeInfo sendNodeInfo();
    @Nullable public abstract DoWork doWork();


    private static Builder builder() {
        return new AutoValue_Message.Builder();
    }

    /*
     * The following methods create a message with the specified request.
     */

    public static Message create(@NonNull SpinUpNodes spinUpNodes) {
        return builder().spinUpNodes(spinUpNodes).build();
    }

    public static Message create(@NonNull KillNodes killNodes) {
        return builder().killNodes(killNodes).build();
    }

    public static Message create(@NonNull NodeOnline nodeOnline) {
        return builder().nodeOnline(nodeOnline).build();
    }

    public static Message create(@NonNull NodesSpawned nodesSpawned) {
        return builder().nodesSpawned(nodesSpawned).build();
    }

    public static Message create(@NonNull ResponseMessage responseMessage) {
        return builder().responseMessage(responseMessage).build();
    }

    public static Message create(@NonNull BuildTree buildTree) {
        return builder().buildTree(buildTree).build();
    }

    /**
     * Creates a message from a JSON string.
     *
     * @param json the json string.
     * @return the Message.
     */
    public static Message fromJson(String json) {
        return ModelTypeAdapterFactory.fromJson(json, Message.class);
    }


    public static TypeAdapter<Message> typeAdapter(Gson gson) {
        return new AutoValue_Message.GsonTypeAdapter(gson);
    }


    @AutoValue.Builder
    static abstract class Builder {
        abstract Builder spinUpNodes(@Nullable SpinUpNodes spinUpNodes);
        abstract Builder killNodes(@Nullable KillNodes killNodes);
        abstract Builder nodeOnline(@Nullable NodeOnline nodeOnline);

        abstract Builder nodesSpawned(@Nullable NodesSpawned nodesSpawned);
        abstract Builder responseMessage(@Nullable ResponseMessage responseMessage);

        abstract Builder buildTree(@Nullable BuildTree buildTree);
        abstract Builder sendNodeInfo(@Nullable SendNodeInfo sendNodeInfo);
        abstract Builder doWork(@Nullable DoWork doWork);

        abstract Message build();
    }
}
