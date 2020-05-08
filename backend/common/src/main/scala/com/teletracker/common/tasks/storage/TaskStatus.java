package com.teletracker.common.tasks.storage;

public enum TaskStatus {
    Waiting,
    Scheduled,
    Executing,
    Completed,
    Failed,
    Canceled;

    TaskStatus() {}

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }

    public static TaskStatus fromString(final String s) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.toString().equalsIgnoreCase(s)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Could not find InstanceStatus value for string = " + s);
    }
}
