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
 * carries one picture. A line of signal is Ntsc.SAMPLES samples, a
 * little under two to a game pixel, and each sample is the average of
 * the stretch of the layers it covers; the world layer's slide of part
 * of a game pixel is allowed for in that, so across, the view still
 * moves by less than a game pixel. Up and down a row of game pixels
 * is a line of signal and there is nothing between lines.
 *
 * The averaging matters. A sample is not a whole number of game
 * pixels wide, so taking each from the one pixel it starts on gives
 * most pixels two samples and every twenty-fifth only one: fifteen
 * narrow columns standing still on the screen, which the world
 * visibly ripples through as it scrolls. Averaged, every pixel
 * carries the same weight wherever it falls.
 *
 * What comes out is as many rows, and wide enough for every screen
 * pixel across to have its own: Across() to a game pixel.
 *
 * The carrier's phase turns over sixty times a second, as a console's
 * did from field to field, whatever rate the game is drawing at. A
 * picture that has not changed, with no noise, is not sent again: the
 * two pictures it comes out as, one for each phase, are kept.
 *
 * How the signal and the set are adjusted comes from Tuning, and is
 * taken up again whenever a knob there is turned.
 */
final class Signal {
    /** The settings: no cable at all, S-Video, or composite video. */
    static final int RGB = 0, SVIDEO = 1, COMPOSITE = 2;

    /** The fewest screen pixels to a game pixel it is worth doing at. */
    static final int MIN_SCALE = 2;

    private static final long NS_PER_FIELD = 1000000000L / 60;
    private static final int PHASES = 2;

    private final int mode;
    private int noise, tuned = -1;
    private boolean flips;
    private final int pictureW, rows, viewX, viewY, viewH, worldW, across;

    private final Ntsc ntsc;
    private final int[] scene, sent;
    private final int[][] shown = new int[PHASES][];
    private final boolean[] kept = new boolean[PHASES];

    /* How finely a sample's share of a pixel is counted. */
    private static final int WHOLE = 256;

    /* For each sample of a line: the column of the picture it starts
       on, and how much of it, out of WHOLE, lies on the next column
       instead. Then the same for the world layer, which moves with
       the slide. */
    private final int[] pictureCol, pictureNext, worldCol, worldNext;
    private int slide = -1;

    /**
     * @param mode SVIDEO or COMPOSITE
     * @param pictureW the width of the picture in game pixels
     * @param rows its height
     * @param viewX the left edge of the view of the world on it
     * @param viewY the top edge of the view
     * @param viewH the height of the view
     * @param worldW the width of the world layer in game pixels
     * @param scale how many screen pixels there are to a game pixel
     */
    Signal(int mode, int pictureW, int rows, int viewX, int viewY,
           int viewH, int worldW, int scale) {
        this.mode = mode;
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
        pictureNext = new int[Ntsc.SAMPLES];
        worldCol = new int[Ntsc.SAMPLES];
        worldNext = new int[Ntsc.SAMPLES];
        Spread(pictureCol, pictureNext, 0, pictureW);
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
        if (tuned != Tuning.Version()) Tune();

        int phase = flips ?
                (int) (System.nanoTime() / NS_PER_FIELD & 1) : 0;

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

    /* Takes the knobs' settings, and forgets the pictures kept, which
       were made with the old ones. */
    private void Tune() {
        boolean two = mode == SVIDEO;

        tuned = Tuning.Version();
        noise = Tuning.Int(Tuning.NOISE);
        flips = Tuning.Int(Tuning.FLIP) != 0;
        ntsc.SetBands(Band(Tuning.LUMA_BAND), Band(Tuning.COLOR_BAND_I),
                Band(Tuning.COLOR_BAND_Q));
        ntsc.Set(Tuning.Int(Tuning.COMB),
                Tuning.Int(two ? Tuning.SHARP_2 : Tuning.SHARP),
                Tuning.Int(two ? Tuning.SATURATION_2 : Tuning.SATURATION),
                Tuning.Int(two ? Tuning.CONTRAST_2 : Tuning.CONTRAST),
                Tuning.Int(two ? Tuning.BRIGHTNESS_2 : Tuning.BRIGHTNESS),
                Tuning.Int(Tuning.HUE));
        kept[0] = kept[1] = false;
    }

    /* A knob in megahertz as Ntsc counts bands. */
    private static int Band(int knob) {
        return (int) Math.round(Tuning.Get(knob) * 100000.0);
    }

    /* Puts the two layers together, a sample at a time. */
    private void Compose(int[] picture, int[] world, int fineX) {
        if (fineX != slide) {
            Spread(worldCol, worldNext, fineX - viewX * across, worldW);
            slide = fineX;
        }

        for (int y = 0; y < rows; y++) {
            int to = y * Ntsc.SAMPLES, from = y * pictureW;
            boolean inView = y >= viewY && y < viewY + viewH;
            int under = (y - viewY) * worldW;

            for (int x = 0; x < Ntsc.SAMPLES; x++) {
                int at = from + pictureCol[x];
                int a = picture[at], b = picture[at + 1];
                int pixel = a == b ? a : Mix(a, b, pictureNext[x]);

                /* Where the picture has a hole, or part of one, the
                   world makes up the rest. A hole is black with no
                   alpha, so adding is all it takes. */
                int alpha = pixel >>> 24;
                if (alpha != 0xFF && inView) {
                    at = under + worldCol[x];
                    a = world[at];
                    b = world[at + 1];
                    int below = a == b ? a : Mix(a, b, worldNext[x]);

                    pixel = alpha == 0 ? below :
                            pixel + Mix(below, 0, alpha * WHOLE / 0xFF);
                }
                scene[to + x] = pixel | 0xFF000000;
            }
        }
    }

    /*
     * Works out which columns of a layer each sample of a line covers.
     *
     * shift - how far right of the picture's left edge the layer's
     *         own left edge is drawn, in screen pixels, negated: what
     *         to add to a place on the picture to get a place on the
     *         layer
     * w     - the layer's width in game pixels
     */
    private void Spread(int[] col, int[] next, int shift, int w) {
        /* Places along a line are counted in screen pixels times the
           number of samples, in which both a sample's edges and a
           game pixel's are whole numbers. */
        long sample = (long) pictureW * across;
        long pixel = (long) across * Ntsc.SAMPLES;

        for (int x = 0; x < Ntsc.SAMPLES; x++) {
            long start = x * sample + (long) shift * Ntsc.SAMPLES;
            long end = start + sample;
            long first = start >= 0 ? start / pixel :
                    -((-start + pixel - 1) / pixel);
            long edge = (first + 1) * pixel;
            long over = end > edge ? end - edge : 0;
            int c = (int) first, share = (int) (over * WHOLE / sample);

            /* Off the layer's edges there is only its edge. The last
               column is never the first of a pair, so that the pair
               is always there to read. */
            if (c < 0) {
                c = 0;
                share = 0;
            } else if (c >= w - 1) {
                c = w - 2;
                share = WHOLE;
            }
            col[x] = c;
            next[x] = share;
        }
    }

    /* A color part of the way from one to another: share out of
       WHOLE. Alpha is mixed like the rest. */
    private static int Mix(int a, int b, int share) {
        int keep = WHOLE - share;
        int rb = ((a & 0x00FF00FF) * keep + (b & 0x00FF00FF) * share) >>> 8;
        int ag = ((a >>> 8 & 0x00FF00FF) * keep +
                (b >>> 8 & 0x00FF00FF) * share) >>> 8;

        return rb & 0x00FF00FF | (ag & 0x00FF00FF) << 8;
    }
}
