CREATE TABLE order_items (
    item_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    game_id INTEGER NOT NULL,
    price INTEGER NOT NULL,
    
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (game_id) REFERENCES games(game_id)
);