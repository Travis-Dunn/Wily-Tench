package com.alchemysoftware.wilytench;

/**
 * A television signal: turns a picture into the voltage an NTSC video
 * cable would carry, line by line, and turns that voltage back into a
 * picture the way a television set does.
 *
 * This is a port to Java of NTSC-CRT by EMMIR (2018-2023),
 * https://github.com/LMP88959/NTSC-CRT, whose license allows any use
 * and asks for this credit. Like the original it uses whole numbers
 * only. The encoder is crt_ntsc.c and the decoder crt_core.c, for the
 * standard NTSC system at four samples to a cycle of the color
 * carrier, without its bloom, with the settings this game uses fixed:
 * a picture given one sample to a sample and one row to a line
 * ("raw"), in color, progressive, always the even field. Given the
 * same picture and the same settings of the set's controls, Encode()
 * and Decode() produce the same bytes as the C does, with noise and
 * without; that was checked when the port was made, and anything
 * changed here should be checked the same way.
 *
 * What it does to a picture is what a composite cable did. Brightness
 * and color share one wire, color riding on a carrier wave, so each
 * leaks into the other: fine patterns of light and dark turn into
 * color, and edges of color crawl. Color has far less room on the
 * wire than brightness, so it smears sideways. None of it happens up
 * and down, since each line is sent on its own.
 *
 * One thing is added to the original: S-Video, where brightness and
 * color travel on two wires. Color still smears, but nothing leaks,
 * and the brightness can be left sharper since there is no carrier to
 * filter out of it.
 *
 * The signal is HRES samples to a line and VRES lines to a field,
 * most of them picture and the rest the pulses a set finds its place
 * by. The decoder really does look for those pulses, so noise on the
 * signal can make the picture shake, as it did.
 */
final class Ntsc {
    /** How many samples of a line carry picture: a row is this wide. */
    static final int SAMPLES;

    /** The most rows of picture a field can carry. */
    static final int MAX_ROWS;

    private static final int CC_LINE = 2275;
    private static final int CB_FREQ = 4;
    private static final int HRES = CC_LINE * CB_FREQ / 10;
    private static final int VRES = 262;
    private static final int INPUT_SIZE = HRES * VRES;

    /* The first line with picture and the line after the last. */
    private static final int TOP = 21, BOT = 261;
    private static final int LINES = BOT - TOP;

    /* Samples to a cycle of the color carrier. */
    private static final int CC_SAMPLES = 4;

    private static final int HSYNC_WINDOW = 8, VSYNC_WINDOW = 8;
    private static final int HSYNC_THRESH = 4, VSYNC_THRESH = 94;

    /* The parts of a line, in nanoseconds: front porch, sync tip,
       breezeway, color burst, back porch, active video. */
    private static final int FP_NS = 1500, SYNC_NS = 4700, BW_NS = 600;
    private static final int CB_NS = 2500, BP_NS = 1600, AV_NS = 52600;
    private static final int HB_NS = FP_NS + SYNC_NS + BW_NS + CB_NS + BP_NS;
    private static final int LINE_NS = HB_NS + AV_NS;

    /* Where each part begins, in samples. */
    private static final int SYNC_BEG = Pos(FP_NS);
    private static final int BW_BEG = Pos(FP_NS + SYNC_NS);
    private static final int CB_BEG = Pos(FP_NS + SYNC_NS + BW_NS);
    private static final int AV_BEG = Pos(HB_NS);
    private static final int AV_LEN = Pos(AV_NS);

    private static final int CB_CYCLES = 10;

    /* Bandwidths, in units that divide evenly: the whole line, and
       what brightness and the two parts of color are allowed. */
    private static final int L_FREQ = 1431818;
    private static final int Y_FREQ = 420000;
    private static final int I_FREQ = 150000;
    private static final int Q_FREQ = 55000;

    /* Signal levels, in IRE units. */
    private static final int WHITE_LEVEL = 100, BURST_LEVEL = 20;
    private static final int BLACK_LEVEL = 7, BLANK_LEVEL = 0;
    private static final int SYNC_LEVEL = -40;
    private static final int MOST_IRE = 110;

    /*
     * The set's own controls. The original leaves saturation at 10,
     * contrast at 180, and brightness at 0, which shows this game's
     * colors paler and lighter than they are. These are the settings,
     * for each kind of cable, at which the 32 colors of the game's
     * palette come back nearest to themselves: found by trying them
     * all, as one turns the knobs on a real set until the picture
     * looks right.
     */
    private static final int HUE = 0;
    private static final int SATURATION = 16, CONTRAST = 170;
    private static final int BRIGHTNESS = 0;
    private static final int SATURATION_2 = 15, CONTRAST_2 = 162;
    private static final int BRIGHTNESS_2 = 2;
    private static final int BLACK_POINT = 0, WHITE_POINT = 100;

    /* How far the decoder looks to one side of where it found the
       pulses, which makes up for the delay of its own filters. */
    private static final int X_NUDGE = -3, Y_NUDGE = 3;

    /* Sines, with a full turn divided into this many. */
    private static final int T14_2PI = 16384;
    private static final int T14_MASK = T14_2PI - 1;
    private static final int T14_PI = T14_2PI / 2;

    static {
        SAMPLES = AV_LEN;
        MAX_ROWS = (LINES * 64500) >> 16;
    }

    private final int rows, outW;

    /* The signal as sent, and as received with noise on it. Brightness
       and everything else are in one; color is in the other only for
       S-Video. Longer than a field, because the decoder reads a little
       past the end when its place has slipped. */
    private final byte[] sent = new byte[INPUT_SIZE + 2 * HRES];
    private final byte[] sentColor = new byte[INPUT_SIZE + 2 * HRES];
    private final byte[] got = new byte[INPUT_SIZE + 2 * HRES];
    private final byte[] gotColor = new byte[INPUT_SIZE + 2 * HRES];
    private boolean twoWires;

    /* The color carrier as the decoder has come to know it. */
    private final int[] carrier = new int[CC_SAMPLES];
    private int hsync, vsync;
    private int seed = 194;

    private final Lowpass limitY, limitI, limitQ;
    private final Equalizer eqY, eqYSharp, eqI, eqQ;

    /* One decoded line, before it is fitted to the picture's width. */
    private final int[] lineY = new int[AV_LEN + 1];
    private final int[] lineI = new int[AV_LEN + 1];
    private final int[] lineQ = new int[AV_LEN + 1];

    /**
     * @param rows how many rows the picture has: at most MAX_ROWS
     * @param outW how wide the decoded picture is to be, in pixels
     */
    Ntsc(int rows, int outW) {
        this.rows = rows;
        this.outW = outW;

        limitY = new Lowpass(L_FREQ, Y_FREQ);
        limitI = new Lowpass(L_FREQ, I_FREQ);
        limitQ = new Lowpass(L_FREQ, Q_FREQ);

        eqY = new Equalizer(Khz(1500), Khz(3000), HRES, 65536, 8192, 9175);
        eqI = new Equalizer(Khz(80), Khz(1150), HRES, 65536, 65536, 1311);
        eqQ = new Equalizer(Khz(80), Khz(1000), HRES, 65536, 65536, 0);
        /* Not in the original: brightness with no carrier to remove,
           so the middle band is kept whole. The top band is dropped,
           because it arrives earlier than the others and what it
           adds is a ghost beside every edge. */
        eqYSharp = new Equalizer(Khz(1500), Khz(3000), HRES, 65536, 65536,
                0);
    }

    /**
     * Turns a picture into a signal.
     *
     * @param rgb the picture, SAMPLES wide and as many rows as this
     *            was made for, row by row, packed 0xAABBGGRR
     * @param twoWires false for composite video, true for S-Video
     * @param frame 0 or 1, which should alternate from one frame to
     *              the next: the carrier starts each frame half a
     *              cycle on from the last, which is what makes the
     *              leaks crawl instead of standing still
     */
    void Encode(int[] rgb, boolean twoWires, int frame) {
        int[] ccModI = new int[CC_SAMPLES], ccModQ = new int[CC_SAMPLES];
        int[] ccBurst = new int[CC_SAMPLES], iccf = new int[CC_SAMPLES];
        byte[] line = sent, color = twoWires ? sentColor : sent;
        int x, y, n, t;

        this.twoWires = twoWires;

        for (x = 0; x < CC_SAMPLES; x++) {
            n = HUE + x * (360 / CC_SAMPLES);
            ccBurst[x] = Sin((n + 33) * 8192 / 180) >> 10;
            ccModI[x] = Sin(n * 8192 / 180) >> 10;
            ccModQ[x] = Sin((n - 90) * 8192 / 180) >> 10;
        }

        int xo = (AV_BEG + (AV_LEN - SAMPLES) / 2) & ~3;
        int yo = TOP + (LINES - rows) / 2;

        /* The field is always the even one, so the phase is turned
           over on frame 0. */
        int invPhase = (frame & 1) == 0 ? 1 : 0;
        int ph = invPhase == 1 ? -1 : 1;

        /* The pulses: every line's start, and the lines between
           fields that say where a field begins. */
        for (n = 0; n < VRES; n++) {
            int at = n * HRES;

            t = 0;
            if (n <= 3 || (n >= 7 && n <= 9)) {
                /* Equalizing pulses: small blips of sync. */
                while (t < 4 * HRES / 100) line[at + t++] = SYNC_LEVEL;
                while (t < 50 * HRES / 100) line[at + t++] = BLANK_LEVEL;
                while (t < 54 * HRES / 100) line[at + t++] = SYNC_LEVEL;
                while (t < HRES) line[at + t++] = BLANK_LEVEL;
            } else if (n >= 4 && n <= 6) {
                /* The vertical sync pulse: mostly sync. */
                while (t < 46 * HRES / 100) line[at + t++] = SYNC_LEVEL;
                while (t < 50 * HRES / 100) line[at + t++] = BLANK_LEVEL;
                while (t < 96 * HRES / 100) line[at + t++] = SYNC_LEVEL;
                while (t < HRES) line[at + t++] = BLANK_LEVEL;
            } else {
                /* A line of picture: porch, sync, then the burst of
                   carrier the set matches its colors to. */
                while (t < SYNC_BEG) line[at + t++] = BLANK_LEVEL;
                while (t < BW_BEG) line[at + t++] = SYNC_LEVEL;
                while (t < AV_BEG) line[at + t++] = BLANK_LEVEL;
                if (n < TOP) {
                    while (t < HRES) line[at + t++] = BLANK_LEVEL;
                }
                if (twoWires) {
                    for (t = 0; t < AV_BEG; t++)
                        color[at + t] = BLANK_LEVEL;
                }

                for (t = CB_BEG; t < CB_BEG + CB_CYCLES * CB_FREQ; t++) {
                    int cb = ccBurst[(t + invPhase * (CC_SAMPLES / 2)) %
                            CC_SAMPLES];
                    int level = (BLANK_LEVEL + cb * BURST_LEVEL) >> 5;

                    color[at + t] = (byte) level;
                    iccf[t % CC_SAMPLES] = level;
                }
            }
        }

        int white = WHITE_LEVEL * WHITE_POINT / 100;

        for (y = 0; y < rows; y++) {
            int from = y * SAMPLES, to = xo + (y + yo) * HRES;

            limitY.h = limitI.h = limitQ.h = 0;

            for (x = 0; x < SAMPLES; x++) {
                int pixel = rgb[from + x];
                int r = pixel & 0xFF, g = pixel >> 8 & 0xFF;
                int b = pixel >> 16 & 0xFF;

                /* Red, green, and blue to brightness and two parts of
                   color, each limited to the room it has. */
                int fy = (19595 * r + 38470 * g + 7471 * b) >> 14;
                int fi = (39059 * r - 18022 * g - 21103 * b) >> 14;
                int fq = (13894 * r - 34275 * g + 20382 * b) >> 14;
                int xoff = (x + xo) % CC_SAMPLES;

                fy = limitY.Step(fy);
                fi = limitI.Step(fi) * ph * ccModI[xoff] >> 4;
                fq = limitQ.Step(fq) * ph * ccModQ[xoff] >> 4;

                if (twoWires) {
                    int ire = BLACK_LEVEL + BLACK_POINT +
                            (fy * white >> 10);
                    int chroma = (fi + fq) * white >> 10;

                    line[to + x] = (byte) Math.max(0,
                            Math.min(ire, MOST_IRE));
                    color[to + x] = (byte) Math.max(-MOST_IRE,
                            Math.min(chroma, MOST_IRE));
                } else {
                    int ire = BLACK_LEVEL + BLACK_POINT +
                            ((fy + fi + fq) * white >> 10);

                    line[to + x] = (byte) Math.max(0,
                            Math.min(ire, MOST_IRE));
                }
            }
        }

        for (x = 0; x < CC_SAMPLES; x++) carrier[x] = iccf[x] << 7;
    }

    /**
     * Turns the signal last made by Encode() back into a picture.
     *
     * @param out where the picture goes: as wide as this was made for
     *            and as many rows, packed 0xAABBGGRR
     * @param noise how much noise is on the signal: 0 for none, and
     *              by about 40 the picture struggles to hold still
     */
    void Decode(int[] out, int noise) {
        byte[] sigY = got, sigC = twoWires ? gotColor : got;
        Equalizer luma = twoWires ? eqYSharp : eqY;
        int[] wave = new int[CC_SAMPLES];
        int i, j = 0, line = 0, s;
        int saturation = twoWires ? SATURATION_2 : SATURATION;
        int contrast = twoWires ? CONTRAST_2 : CONTRAST;
        int bright = (twoWires ? BRIGHTNESS_2 : BRIGHTNESS) -
                (BLACK_LEVEL + BLACK_POINT);
        int rn = seed;

        int hueSn = Sin(((HUE % 360) + 33) * 8192 / 180) >> 11;
        int hueCs = Cos(((HUE % 360) + 33) * 8192 / 180) >> 11;

        /* The signal as it arrives: what was sent, and noise. */
        for (i = 0; i < INPUT_SIZE; i++) {
            rn = 214019 * rn + 140327895;
            int hiss = (((rn >> 16) & 0xFF) - 0x7F) * noise >> 8;

            s = sent[i] + hiss;
            got[i] = (byte) Math.max(-127, Math.min(s, 127));
            if (twoWires) {
                s = sentColor[i] + hiss;
                gotColor[i] = (byte) Math.max(-127, Math.min(s, 127));
            }
        }
        seed = rn;

        /* Look for where the field begins: the one place where the
           signal stays low for most of a line. Adding the signal up
           along a line smooths the noise out of it. */
        search:
        for (i = -VSYNC_WINDOW; i < VSYNC_WINDOW; i++) {
            line = PosMod(vsync + i, VRES);
            s = 0;
            for (j = 0; j < HRES; j++) {
                s += got[line * HRES + j];
                if (s <= VSYNC_THRESH * SYNC_LEVEL) break search;
            }
        }
        vsync = line;

        /* Which rows of the field are the picture, when the set has
           found its place where it should. */
        int first = TOP + (LINES - rows) / 2 - VSYNC_FOUND - Y_NUDGE;
        int dx = ((AV_LEN - 1) << 12) / outW;
        int scanR = (AV_LEN - 1) << 12;

        for (line = TOP; line < BOT; line++) {
            int row = line - first;

            /* Look for where the line begins, likewise. */
            int ln = PosMod(line + vsync, VRES) * HRES;
            int at = ln + hsync;
            s = 0;
            for (i = -HSYNC_WINDOW; i < HSYNC_WINDOW; i++) {
                s += got[at + SYNC_BEG + i];
                if (s <= HSYNC_THRESH * SYNC_LEVEL) break;
            }
            hsync = PosMod(i + hsync, HRES);

            int xpos = PosMod(AV_BEG + hsync + X_NUDGE, HRES);
            int ypos = PosMod(line + vsync + Y_NUDGE, VRES);
            int pos = xpos + ypos * HRES;

            /* Learn the carrier from the burst, a little each line. */
            at = ln + (hsync & ~3);
            for (i = CB_BEG; i < CB_BEG + CB_CYCLES * CB_FREQ; i++) {
                int p = carrier[i % CC_SAMPLES] * 127 / 128;
                carrier[i % CC_SAMPLES] = p + sigC[at + i];
            }

            if (row < 0 || row >= rows) continue;

            /* How big the carrier is says how strong the colors are,
               and how far round it is says which they are. */
            int align = PosMod(hsync, CC_SAMPLES);
            int dci = carrier[(align + 1) & 3] - carrier[(align + 3) & 3];
            int dcq = carrier[(align + 2) & 3] - carrier[(align + 0) & 3];

            wave[0] = ((dci * hueCs - dcq * hueSn) >> 4) * saturation;
            wave[1] = ((dcq * hueCs + dci * hueSn) >> 4) * saturation;
            wave[2] = -wave[0];
            wave[3] = -wave[1];

            luma.Reset();
            eqI.Reset();
            eqQ.Reset();
            for (i = 0; i < AV_LEN; i++) {
                int c = sigC[pos + i];

                lineY[i] = luma.Step(sigY[pos + i] + bright) << 4;
                lineI[i] = eqI.Step(c * wave[i & 3] >> 9) >> 3;
                lineQ[i] = eqQ.Step(c * wave[(i + 3) & 3] >> 9) >> 3;
            }

            /* Fit the line to the picture's width, and turn brightness
               and color back into red, green, and blue. */
            int to = row * outW, end = to + outW;
            for (int p = 0; p < scanR && to < end; p += dx) {
                int right = p & 0xFFF, left = 0xFFF - right;
                int a = p >> 12, b = a + 1;

                int y = (lineY[a] * left >> 2) + (lineY[b] * right >> 2);
                int ci = (lineI[a] * left >> 14) +
                        (lineI[b] * right >> 14);
                int cq = (lineQ[a] * left >> 14) +
                        (lineQ[b] * right >> 14);

                int r = ((y + 3879 * ci + 2556 * cq) >> 12) * contrast >> 8;
                int g = ((y - 1126 * ci - 2605 * cq) >> 12) * contrast >> 8;
                int bl = ((y - 4530 * ci + 7021 * cq) >> 12) *
                        contrast >> 8;

                r = Math.max(0, Math.min(r, 255));
                g = Math.max(0, Math.min(g, 255));
                bl = Math.max(0, Math.min(bl, 255));
                out[to++] = 0xFF000000 | bl << 16 | g << 8 | r;
            }
        }
    }

    /* Which line the decoder takes the field to begin at when there is
       no noise: the first line of the vertical sync pulse. */
    private static final int VSYNC_FOUND = 4;

    private static int Pos(int ns) { return ns * HRES / LINE_NS; }

    private static int Khz(int khz) { return HRES * (khz * 100) / L_FREQ; }

    private static int PosMod(int x, int n) { return (x % n + n) % n; }

    /* A low-pass filter that keeps a signal within its bandwidth. */
    private static final class Lowpass {
        private static final int P = 11, ONE = 1 << P, MASK = ONE - 1;
        private static final int PI = 6434;
        private static final int[] E = { ONE, 5567, 15133, 41135, 111817 };

        final int c;
        int h;

        /* freq is the whole bandwidth and limit the most allowed. */
        Lowpass(int freq, int limit) {
            int rate = (freq << 9) / limit;

            c = ONE - Exp(-((PI << 9) / rate));
        }

        int Step(int s) {
            h += (s - h) * c >> P;
            return h;
        }

        /* e to the power of n, in fixed point. */
        private static int Exp(int n) {
            boolean neg = n < 0;
            int idx, res = ONE, nxt = ONE, acc = 0, del = 1, i;

            if (n == 0) return ONE;
            if (neg) n = -n;

            idx = n >> P;
            for (i = 0; i < idx / 4; i++) res = res * E[4] >> P;
            idx &= 3;
            if (idx > 0) res = res * E[idx] >> P;

            n &= MASK;
            for (i = 1; i < 17; i++) {
                acc += nxt / del;
                nxt = nxt * n >> P;
                del *= i;
                if (del > nxt || nxt <= 0 || del <= 0) break;
            }
            res = res * acc >> P;

            return neg ? (ONE << P) / res : res;
        }
    }

    /* A three-band equalizer: splits a signal into low, middle, and
       high, and puts it back together with a gain on each. The gains
       are 16-bit fixed point. */
    private static final class Equalizer {
        private static final int P = 16, ROUND = 1 << (P - 1);

        private final int lf, hf;
        private final int gLo, gMid, gHi;
        /* Four low-pass stages for each of the two cutoffs, and the
           last three samples in. */
        private int l0, l1, l2, l3, u0, u1, u2, u3;
        private int h0, h1, h2;

        Equalizer(int fLo, int fHi, int rate, int gLo, int gMid, int gHi) {
            this.gLo = gLo;
            this.gMid = gMid;
            this.gHi = gHi;
            lf = 2 * (Sin(T14_PI * fLo / rate) << (P - 15));
            hf = 2 * (Sin(T14_PI * fHi / rate) << (P - 15));
        }

        void Reset() {
            l0 = l1 = l2 = l3 = u0 = u1 = u2 = u3 = 0;
            h0 = h1 = h2 = 0;
        }

        int Step(int s) {
            l0 += (lf * (s - l0) + ROUND) >> P;
            u0 += (hf * (s - u0) + ROUND) >> P;
            l1 += (lf * (l0 - l1) + ROUND) >> P;
            u1 += (hf * (u0 - u1) + ROUND) >> P;
            l2 += (lf * (l1 - l2) + ROUND) >> P;
            u2 += (hf * (u1 - u2) + ROUND) >> P;
            l3 += (lf * (l2 - l3) + ROUND) >> P;
            u3 += (hf * (u2 - u3) + ROUND) >> P;

            int lo = l3 * gLo >> P;
            int mid = (u3 - l3) * gMid >> P;
            int hi = (h2 - u3) * gHi >> P;

            h2 = h1;
            h1 = h0;
            h0 = s;
            return lo + mid + hi;
        }
    }

    /* Sixteen points along a quarter of a sine wave, and one past. */
    private static final int[] SINES = {
        0x0000,
        0x0c88, 0x18f8, 0x2528, 0x30f8, 0x3c50, 0x4718, 0x5130, 0x5a80,
        0x62f0, 0x6a68, 0x70e0, 0x7640, 0x7a78, 0x7d88, 0x7f60, 0x8000,
        0x7f60
    };

    private static int QuarterSin(int n) {
        int f = n & 0xFF, i = n >> 8 & 0xFF;
        int a = SINES[i], b = SINES[i + 1];

        return a + ((b - a) * f >> 8);
    }

    /* The sine of n, where T14_2PI is a full turn. */
    private static int Sin(int n) {
        int h, s;

        n &= T14_MASK;
        h = n & (T14_PI - 1);
        s = h > T14_2PI / 4 - 1 ? QuarterSin(T14_PI - h) : QuarterSin(h);
        return n > T14_PI - 1 ? -s : s;
    }

    private static int Cos(int n) {
        int h, c;

        n &= T14_MASK;
        h = n & (T14_PI - 1);
        c = h > T14_2PI / 4 - 1 ? -QuarterSin(h - T14_2PI / 4) :
                QuarterSin(T14_2PI / 4 - h);
        return n > T14_PI - 1 ? -c : c;
    }
}
