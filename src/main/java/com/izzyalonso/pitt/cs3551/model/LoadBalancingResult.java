package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;

import java.util.List;

@AutoValue
public abstract class LoadBalancingResult extends JsonConvertible {
    public abstract List<JobTransfer> jobTransfers();


    public static LoadBalancingResult create(@NonNull List<JobTransfer> jobTransfers) {
        return new AutoValue_LoadBalancingResult(jobTransfers);
    }

    public static TypeAdapter<LoadBalancingResult> typeAdapter(Gson gson) {
        return new AutoValue_LoadBalancingResult.GsonTypeAdapter(gson);
    }
}
