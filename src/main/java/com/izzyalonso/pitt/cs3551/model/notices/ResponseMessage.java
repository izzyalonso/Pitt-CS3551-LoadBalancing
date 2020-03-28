package com.izzyalonso.pitt.cs3551.model.notices;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.izzyalonso.pitt.cs3551.model.JsonConvertible;

@AutoValue
public abstract class ResponseMessage extends JsonConvertible {
    public abstract String message();


    public static ResponseMessage create(String message) {
        return new AutoValue_ResponseMessage(message);
    }

    public static TypeAdapter<ResponseMessage> typeAdapter(Gson gson) {
        return new AutoValue_ResponseMessage.GsonTypeAdapter(gson);
    }
}
