package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;


@AutoValue
public abstract class JobTransfer extends JsonConvertible {
    public abstract JobInfo job();
    public abstract NodeInfo donor();
    public abstract NodeInfo recipient();

    public static JobTransfer create(@NonNull JobInfo job, @NonNull NodeInfo donor, @NonNull NodeInfo recipient) {
        return new AutoValue_JobTransfer(job, donor, recipient);
    }

    public static TypeAdapter<JobTransfer> typeAdapter(Gson gson) {
        return new AutoValue_JobTransfer.GsonTypeAdapter(gson);
    }
}
