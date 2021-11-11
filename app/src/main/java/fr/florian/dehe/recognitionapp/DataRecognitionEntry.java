package fr.florian.dehe.recognitionapp;

public class DataRecognitionEntry {

    private long time;
    private String activity;
    private double latitude;
    private double longitude;

    public DataRecognitionEntry(long time, String activity, double latitude, double longitude) {
        this.time = time;
        this.activity = activity;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String serialized() {
        return time + "," + activity + "," + latitude + "," + longitude;
    }
}
