package com.skillswap.app.models;

import java.util.ArrayList;
import java.util.List;

/**
 * User profile model stored at users/{uid} in Firebase Realtime Database.
 * A no-arg constructor is required for Firebase's automatic deserialization.
 */
public class User {

    private String uid;
    private String name;
    private String email;
    private String bio;
    private String location;
    private double lat;
    private double lng;
    private List<String> skillsTeach;
    private List<String> skillsWant;
    private double avgRating;
    private int ratingCount;
    private String avatarColor; // used to render an initials-avatar consistently
    private long createdAt;

    public User() {
        // Required empty constructor for Firebase
        skillsTeach = new ArrayList<>();
        skillsWant = new ArrayList<>();
    }

    public User(String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.bio = "";
        this.location = "";
        this.lat = 0;
        this.lng = 0;
        this.skillsTeach = new ArrayList<>();
        this.skillsWant = new ArrayList<>();
        this.avgRating = 0;
        this.ratingCount = 0;
        this.avatarColor = "#2E7D6B";
        this.createdAt = System.currentTimeMillis();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public List<String> getSkillsTeach() { return skillsTeach; }
    public void setSkillsTeach(List<String> skillsTeach) { this.skillsTeach = skillsTeach; }

    public List<String> getSkillsWant() { return skillsWant; }
    public void setSkillsWant(List<String> skillsWant) { this.skillsWant = skillsWant; }

    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
