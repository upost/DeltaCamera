package de.uwepost.android.deltacam;

import java.util.Random;

/**
 * Created by uwe on 15.03.18.
 */

public class PseudoUUID {

    private static final int LENGTH = 10;
    private static Random rnd = new Random();

    public static String create() {
        String res="";
        for(int i=0; i<LENGTH; i++) {
            res += Character.valueOf( (char)(rnd.nextInt(26)+64));
        }
        return res;
    }
}
