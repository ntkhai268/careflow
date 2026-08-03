package com.careflow.queue.dto;

public record RequeueRequest(Position position) {
    public enum Position { FRONT, BACK }
}
