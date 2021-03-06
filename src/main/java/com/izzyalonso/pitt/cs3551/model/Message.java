package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy;
import com.izzyalonso.pitt.cs3551.model.commands.SendNodeInfo;
import com.izzyalonso.pitt.cs3551.model.commands.controller.KillNodes;
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes;
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline;
import com.izzyalonso.pitt.cs3551.model.notices.NodesSpawned;
import com.izzyalonso.pitt.cs3551.model.notices.ResponseMessage;

import java.util.List;

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
    @Nullable public abstract BuildHierarchy buildHierarchy();
    @Nullable public abstract SendNodeInfo sendNodeInfo();
    @Nullable public abstract Job doWork();

    @Nullable public abstract LoadInfo loadInfo();

    @Nullable public abstract TreeNode hierarchy();

    // Instructs the parent of a subtree to collect all its subtrees' jobs
    public abstract boolean collectJobs(); // Default to false when using the builder
    @Nullable public abstract JobInfoList jobInfoList();
    @Nullable public abstract LoadBalancingResult loadBalancingResult();
    @Nullable public abstract List<JobTransfer> jobTransfer();
    @Nullable public abstract List<Job> jobs();

    @Nullable public abstract String log();


    private static Builder builder() {
        return new AutoValue_Message.Builder().collectJobs(false);
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

    public static Message create(@NonNull BuildHierarchy buildHierarchy) {
        return builder().buildHierarchy(buildHierarchy).build();
    }

    public static Message create(@NonNull Job job) {
        return builder().doWork(job).build();
    }

    public static Message create(@NonNull TreeNode hierarchy) {
        return builder().hierarchy(hierarchy).build();
    }

    public static Message create(@NonNull LoadInfo loadInfo) {
        return builder().loadInfo(loadInfo).build();
    }

    public static Message createCollectJobs() {
        return builder().collectJobs(true).build();
    }

    public static Message create(@NonNull JobInfoList jobInfoList) {
        return builder().jobInfoList(jobInfoList).build();
    }

    public static Message create(@NonNull LoadBalancingResult loadBalancingResult) {
        return builder().loadBalancingResult(loadBalancingResult).build();
    }

    public static Message createJobTransferRequest(@NonNull List<JobTransfer> jobTransfer) {
        return builder().jobTransfer(jobTransfer).build();
    }

    public static Message create(@NonNull List<Job> jobs) {
        return builder().jobs(jobs).build();
    }

    public static Message create(@NonNull String log) {
        return builder().log(log).build();
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

        abstract Builder buildHierarchy(@Nullable BuildHierarchy buildHierarchy);
        abstract Builder sendNodeInfo(@Nullable SendNodeInfo sendNodeInfo);
        abstract Builder doWork(@Nullable Job doWork);

        abstract Builder loadInfo(@Nullable LoadInfo loadInfo);

        abstract Builder hierarchy(@Nullable TreeNode hierarchy);

        abstract Builder collectJobs(boolean collectJobs);
        abstract Builder jobInfoList(@Nullable JobInfoList jobInfoList);
        abstract Builder loadBalancingResult(@Nullable LoadBalancingResult loadBalancingResult);
        abstract Builder jobTransfer(@Nullable List<JobTransfer> jobTransfer);
        abstract Builder jobs(@Nullable List<Job> jobs);

        abstract Builder log(@Nullable String log);

        abstract Message build();
    }
}
