package com.example.mob_3_record;

public class RunningRecord {
    private String date;
    private String distance;
    private String runningType;
    private String time;
    private String averagePace;

    public RunningRecord(String date, String distance, String runningType, String time, String averagePace) {
        this.date = date;
        this.distance = distance;
        this.runningType = runningType;
        this.time = time;
        this.averagePace = averagePace;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getRunningType() {
        return runningType;
    }

    public void setRunningType(String runningType) {
        this.runningType = runningType;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAveragePace() {
        return averagePace;
    }

    public void setAveragePace(String averagePace) {
        this.averagePace = averagePace;
    }
}

