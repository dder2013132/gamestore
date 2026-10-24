package com.gamestore.repository;

import java.sql.SQLException;

/** Catalog changed after the customer reviewed the cart; no order was committed. */
public class CatalogConflictException extends SQLException {
    public CatalogConflictException(String message) { super(message); }
}
