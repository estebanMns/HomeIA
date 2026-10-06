package com.home.ia.domain.model.home;

import java.util.Objects;

public class Room {
    private final RoomId id;
    private final HomeId homeId;
    private String name;

    public Room(RoomId id, HomeId homeId, String name) {
        this.id = Objects.requireNonNull(id);
        this.homeId = Objects.requireNonNull(homeId);
        rename(name);
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la habitación es obligatorio");
        }
        this.name = name;
    }

    public RoomId getId() {
        return id;
    }

    public HomeId getHomeId() {
        return homeId;
    }

    public String getName() {
        return name;
    }
}