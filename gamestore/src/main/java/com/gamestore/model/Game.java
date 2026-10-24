package com.gamestore.model;

public class Game {
    public static final int DESCRIPTION_MAX_LENGTH = 5000;
    private String id;
    private String title;
    private int price;
    private String genre;
    private String imagePath;
    private String description;
    private boolean active;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
    
    public int getPrice() {
        return price;
    }

    public String getGenre() {
        return genre;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getDescription() { return description; }
    public boolean isActive() { return active; }

    public Game(String id, String title, int price, String genre, String imagePath) {
        this(id, title, price, genre, imagePath, null);
    }

    public Game(String id, String title, int price, String genre, String imagePath, String description) {
        this(id, title, price, genre, imagePath, description, true);
    }

    public Game(String id, String title, int price, String genre, String imagePath, String description, boolean active) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.genre = genre;
        this.imagePath = imagePath;
        this.description = description;
        this.active = active;
    }
}
