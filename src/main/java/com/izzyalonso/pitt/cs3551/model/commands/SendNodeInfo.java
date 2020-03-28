package com.izzyalonso.pitt.cs3551.model.commands;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;
import com.izzyalonso.pitt.cs3551.model.NodeInfo;


@AutoValue
public abstract class SendNodeInfo extends JsonConvertible {
    public abstract NodeInfo node();

    public static TypeAdapter<SendNodeInfo> typeAdapter(Gson gson) {
        return new AutoValue_SendNodeInfo.GsonTypeAdapter(gson);
    }
}
