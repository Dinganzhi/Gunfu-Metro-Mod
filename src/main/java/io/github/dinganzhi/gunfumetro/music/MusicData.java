package io.github.dinganzhi.gunfumetro.music;

public class MusicData {
    public String id;
    public String title;
    public String author;
    public int length;
    public String path;

    public MusicData(String id, String title, String author, int length, String path) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.length = length;
        this.path = path;
    }
}