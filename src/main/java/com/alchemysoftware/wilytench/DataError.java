package com.alchemysoftware.wilytench;

/**
 * Something wrong in one of the game's data files, worded for whoever
 * edited the file. Thrown while data is being read and caught by
 * Content.Load(), which reports it; it never leaves the game's code.
 */
final class DataError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    DataError(String message) { super(message); }
}
