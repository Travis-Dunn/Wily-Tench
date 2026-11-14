package com.alchemysoftware.wilytench;

/**
 * The video cable between the game and the screen: an option that
 * sends the picture as a television signal, through Ntsc, and shows
 * what comes out the other end.
 *
 * It is a different thing from the CRT filter, and either can be on
 * without the other. This is the cable and that is the tube: this one
 * turns a picture into another picture, smeared and fringed the way
 * the wire did it, and whichever way Screen has of showing a picture
 * then shows that one.
 *
 * The two layers Screen keeps are put together here, since a signal
 * carries one picture. A line of signal is Ntsc.SAMPLES samples, about
 * two to a game pixel, and each sample is taken from wherever it falls
 * on the layers; the world layer's slide of part of a game pixel is
 * allowed for in that, so across, the view still moves by less than a
 * game pixel. Up and down a row of game pixels is a line of signal
 * and there is nothing between lines.
 *
 * What comes out is as many rows, and wide enough for every screen
 * pixel across to have its own: Across() to a game pixel.
 *
 * The carrier's phase turns over sixty times a second, as a console's
 * did from field to field, whatever rate the game is drawing at. A
 * picture that has not changed, with no noise, is not sent again: the
 * two pictures it comes out as, one for each phase, are kept.
 */
final class Signal {
    /** The settings: no cable at all, S-Video, or composite video. */
    static final int RGB = 0, SVIDEO = 1, COMPOSITE = 2;

    /** The fewest screen pixels to a game pixel it is worth doing at. */
    static final int MIN_SCALE = 2;

    private static final long NS_PER_FIELD = 1000000000L / 60;
    private static final int PHASES = 2;

    private final int mode, noise;
    private final int pictureW, rows, viewX, viewY, viewH, worldW, across;

    private final Ntsc ntsc;
    private final int[] scene, sent;
    private final int[][] shown = new int[PHASES][];
    private final boolean[] kept = new boolean[PHASES];

    /* For each sample of a line, which column of the picture it falls
       on and which of the world layer. The second moves with the
       slide. */
    private final int[] pictureCol, worldCol;
    private int slide = -1;

    /**
     * @param mode SVIDEO or COMPOSITE
     * @param noise how much noise is on the cable, as Ntsc.Decode()
     *              counts it
     * @param pictureW the width of the picture in game pixels
     * @param rows its height
     * @param viewX the left edge of the view of the world on it
     * @param viewY the top edge of the view
     * @param viewH the height of the view
     * @param worldW the width of the world layer in game pixels
     * @param scale how many screen pixels there are to a game pixel
     */
    Signal(int mode, int noise, int pictureW, int rows, int viewX,
           int viewY, int viewH, int worldW, int scale) {
        this.mode = mode;
        this.noise = noise;
        this.pictureW = pictureW;
        this.rows = rows;
        this.viewX = viewX;
        this.viewY = viewY;
        this.viewH = viewH;
        this.worldW = worldW;
        across = scale;

        ntsc = new Ntsc(rows, pictureW * across);
        scene = new int[Ntsc.SAMPLES * rows];
        sent = new int[Ntsc.SAMPLES * rows];
        for (int i = 0; i < PHASES; i++)
            shown[i] = new int[pictureW * across * rows];

        pictureCol = new int[Ntsc.SAMPLES];
        worldCol = new int[Ntsc.SAMPLES];
        for (int x = 0; x < Ntsc.SAMPLES; x++)
            pictureCol[x] = x * pictureW / Ntsc.SAMPLES;
    }

    /** @return how many pixels of Run()'s picture make a game pixel */
    int Across() { return across; }

    /**
     * Sends a frame down the cable.
     *
     * @param picture the upper layer, packed 0xAABBGGRR, with no alpha
     *                wherever the world shows through
     * @param world the world layer, likewise packed
     * @param fineX how far left the world layer is slid, in screen
     *              pixels
     * @return the frame as received: Across() times as wide as the
     *         picture and as many rows, likewise packed. It is this
     *         object's own and is good until the next call.
     */
    int[] Run(int[] picture, int[] world, int fineX) {
        int phase = (int) (System.nanoTime() / NS_PER_FIELD & 1);

        Compose(picture, world, fineX);

        boolean same = noise == 0;
        for (int i = 0, n = scene.length; same && i < n; i++)
            same = scene[i] == sent[i];
        if (!same) {
            System.arraycopy(scene, 0, sent, 0, scene.length);
            kept[0] = kept[1] = false;
        }
        if (!kept[phase]) {
            ntsc.Encode(scene, mode == SVIDEO, phase);
            ntsc.Decode(shown[phase], noise);
            kept[phase] = noise == 0;
        }
        return shown[phase];
    }

    /* Puts the two layers together, a sample at a time. */
    private void Compose(int[] picture, int[] world, int fineX) {
        if (fineX != slide) {
            /* Positions along a line in screen pixels. */
            for (int x = 0; x < Ntsc.SAMPLES; x++) {
                int at = x * pictureW * across / Ntsc.SAMPLES;
                int col = (at - viewX * across + fineX) / across;

                worldCol[x] = Math.max(0, Math.min(col, worldW - 1));
            }
            slide = fineX;
        }

        for (int y = 0; y < rows; y++) {
            int to = y * Ntsc.SAMPLES, from = y * pictureW;
            boolean inView = y >= viewY && y < viewY + viewH;
            int under = (y - viewY) * worldW;

            for (int x = 0; x < Ntsc.SAMPLES; x++) {
                int pixel = picture[from + pictureCol[x]];

                if (pixel >>> 24 == 0 && inView)
                    pixel = world[under + worldCol[x]];
                scene[to + x] = pixel;
            }
        }
    }
}
