package com.skillswap.app.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    /** Required by Firebase for deserialization; do not call directly. */
    public User() {
        skillsTeach = new ArrayList<>();
        skillsWant = new ArrayList<>();
    }

    public User(String uid, String name, String email) {
        this.uid = Objects.requireNonNull(uid, "uid");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
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

    /**
     * Returns an unmodifiable view of this user's "can teach" skills. Callers that
     * want to change a user's skills (see {@code ManageSkillsActivity}) already keep
     * their own working copy and write it back via {@code setSkillsTeach} /
     * {@code UserRepository.updateFields} rather than mutating this list in place —
     * this method just makes that the only option, instead of an accident waiting to
     * confuse a future caller who mutates the returned list and expects it to persist.
     */
    public List<String> getSkillsTeach() {
        return skillsTeach == null ? Collections.emptyList() : Collections.unmodifiableList(skillsTeach);
    }

    public void setSkillsTeach(List<String> skillsTeach) {
        this.skillsTeach = skillsTeach == null ? new ArrayList<>() : new ArrayList<>(skillsTeach);
    }

    public List<String> getSkillsWant() {
        return skillsWant == null ? Collections.emptyList() : Collections.unmodifiableList(skillsWant);
    }

    public void setSkillsWant(List<String> skillsWant) {
        this.skillsWant = skillsWant == null ? new ArrayList<>() : new ArrayList<>(skillsWant);
    }

    public double getAvgRating() { return avgRating; }
    public void setAvgRating(double avgRating) { this.avgRating = avgRating; }

    public int getRatingCount() { return ratingCount; }
    public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User other = (User) o;
        return Objects.equals(uid, other.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }

    @Override
    public String toString() {
        return "User{uid='" + uid + "', name='" + name + "'}";
    }
}
