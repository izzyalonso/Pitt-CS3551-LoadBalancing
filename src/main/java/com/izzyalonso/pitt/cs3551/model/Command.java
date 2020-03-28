package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.izzyalonso.pitt.cs3551.annotation.Nullable;


@AutoValue
public abstract class Command extends JsonConvertible {
    public abstract Operation operation();

    @Nullable public abstract Integer nodeCount();
    @Nullable public abstract NodeInfo recipient();
    @Nullable public abstract Work work();



    public enum Operation {
        // Lets a machine know it's responsible for building the tree
        PREPARE_TO_BUILD_TREE,
        // Instructs a machine to send its information to other machine
        SEND_NODE_INFO,

        // Schedules work in a machine
        DO_WORK
    }
}
