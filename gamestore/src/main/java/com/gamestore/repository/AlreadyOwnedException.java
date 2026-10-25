package com.gamestore.repository;

import java.sql.SQLException;

/** A fresh order cannot include a game this member already owns. */
public class AlreadyOwnedException extends SQLException {
    public AlreadyOwnedException() { super("An order contains an already owned game"); }
}
