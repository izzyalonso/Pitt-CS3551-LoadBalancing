package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

@AutoValue
public abstract class JobInfo extends JsonConvertible implements Comparable<JobInfo> {
    public abstract int jobId();
    public abstract long weight();
    public abstract NodeInfo owner();

    @Override
    public int compareTo(@NotNull JobInfo other) {
        // Reverse order, heaviest first
        return Long.compare(other.weight(), weight());
    }

    public static JobInfo create(@NonNull Job job, @NonNull NodeInfo owner) {
        return new AutoValue_JobInfo(job.id(), job.weight(), owner);
    }

    public static TypeAdapter<JobInfo> typeAdapter(Gson gson) {
        return new AutoValue_JobInfo.GsonTypeAdapter(gson);
    }
}
