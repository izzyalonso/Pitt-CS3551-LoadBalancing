package com.izzyalonso.pitt.cs3551.model;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.annotation.NonNull;

import java.util.List;

@AutoValue
public abstract class JobInfoList extends JsonConvertible {
    public abstract NodeInfo sender();
    public abstract List<JobInfo> jobInfoList();

    public static JobInfoList create(@NonNull NodeInfo sender, @NonNull List<JobInfo> jobInfoList) {
        return new AutoValue_JobInfoList(sender, jobInfoList);
    }

    public static TypeAdapter<JobInfoList> typeAdapter(Gson gson) {
        return new AutoValue_JobInfoList.GsonTypeAdapter(gson);
    }
}
