package com.teletracker.common.pubsub;

public enum EsIngestMessageOperation {
    Update("update"),
    Index("index");

    private final String op;

    EsIngestMessageOperation(String op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return this.op;
    }
}
