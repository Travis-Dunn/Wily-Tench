package com.alchemysoftware.wilytench;

/**
 * Program entry point. Does nothing but switch assertions on and hand
 * over to Game, because Whitetail reports most misuse through assert and
 * a class only honors the switch if it is loaded after it is thrown.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        Game.Run();
    }
}
