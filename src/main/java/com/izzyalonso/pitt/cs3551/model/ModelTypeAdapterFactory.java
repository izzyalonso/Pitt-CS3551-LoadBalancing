package com.izzyalonso.pitt.cs3551.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import com.ryanharter.auto.value.gson.GsonTypeAdapterFactory;

@GsonTypeAdapterFactory
public abstract class ModelTypeAdapterFactory implements TypeAdapterFactory {
    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .registerTypeAdapterFactory(ModelTypeAdapterFactory.create())
            .create();

    public static TypeAdapterFactory create() {
        return new AutoValueGson_ModelTypeAdapterFactory();
    }

    public static String toJson(JsonConvertible obj) {
        return GSON.toJson(obj);
    }

    static <T> T fromJson(String json, Class<T> cls) {
        return GSON.fromJson(json, cls);
    }
}
