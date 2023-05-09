package com.example.demo.model;

public class Message {

    private final Long id;
    private boolean error;

    public Message(Long id) {
        this.id = id;
    }

    public boolean hasError() {
        return error;
    }

    public Message withError() {
        this.error = true;
        return this;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", error=" + error +
                '}';
    }
}
