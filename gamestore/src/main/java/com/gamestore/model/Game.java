package com.gamestore.model;

public class Game {
    private String id;
    private String title;
    private int price;
    private String genre;
    private String imagePath;

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

    public Game(String id, String title, int price, String genre, String imagePath) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.genre = genre;
        this.imagePath = imagePath;
    }
}
