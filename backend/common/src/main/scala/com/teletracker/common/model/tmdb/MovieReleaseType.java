package com.teletracker.common.model.tmdb;

public enum MovieReleaseType {
    Premiere(1),
    LimitedTheatrical(2),
    Theatrical(3),
    Digital(4),
    Physical(5),
    TV(6);

    final int id;

    MovieReleaseType(final int id) {
        this.id = id;
    }
}