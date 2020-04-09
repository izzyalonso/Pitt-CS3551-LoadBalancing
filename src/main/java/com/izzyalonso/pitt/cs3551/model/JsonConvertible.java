package com.izzyalonso.pitt.cs3551.model;

/**
 * For our GSONd AutoValue classes to be converted to JSON.
 */
public abstract class JsonConvertible {
    public String toJson() {
        return ModelTypeAdapterFactory.toJson(this);
    }
}
