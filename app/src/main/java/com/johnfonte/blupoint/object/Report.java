package com.johnfonte.blupoint.object;

import java.util.List;

public class Report {

    Integer id;
    List<Location> location;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<Location> getLocation() {
        return location;
    }

    public void setLocation(List<Location> location) {
        this.location = location;
    }
}

