package com.example.mob_3_sketch;

public class Course {
    private String name;
    private double distance; // km
    private String difficulty; // 초급, 중급, 고급
    private int estimatedTime; // 분

    public Course(String name, double distance, String difficulty, int estimatedTime) {
        this.name = name;
        this.distance = distance;
        this.difficulty = difficulty;
        this.estimatedTime = estimatedTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(int estimatedTime) {
        this.estimatedTime = estimatedTime;
    }
}

