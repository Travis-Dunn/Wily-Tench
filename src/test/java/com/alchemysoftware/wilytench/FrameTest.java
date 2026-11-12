package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.alchemysoftware.whitetail.image.Image;

/**
 * The game frame and what it is drawn with: whole pictures read from
 * PNG files, the reader for the indexed kind of PNG, and the layout
 * file that says where on the frame things are.
 */
public class FrameTest {
    private static final int RED = 0xB82525, BLUE = 0x2D5EE3;
    private static final int WHITE = 0xFFFFFF, BLACK = 0x000000;

    public void testTheRealFrameLoadsAndIsTheSizeOfThePicture() {
        Fixture.Start(0);
        Bitmap frame = Bitmap.Load(Layout.frame);

        Check("loads", frame != null);
        Check("as wide as the picture", frame.w == Layout.pictureW);
        Check("as tall as the picture", frame.h == Layout.pictureH);
        Fixture.Finish();
    }

    public void testTheLayoutAgreesWithTheFrame() {
        Fixture.Start(0);
        Bitmap frame = Bitmap.Load(Layout.frame);
        int half = Layout.CELL / 2;

        Check("the middle of the view is a hole", At(frame,
                Layout.viewX + Layout.viewW / 2,
                Layout.viewY + Layout.viewH / 2) == Bitmap.CLEAR);
        Check("the view's rectangle reaches to the frame on each side",
                At(frame, Layout.viewX - 1, Layout.viewY + Layout.viewH / 2)
                != Bitmap.CLEAR && At(frame, Layout.viewX + Layout.viewW,
                Layout.viewY + Layout.viewH / 2) != Bitmap.CLEAR &&
                At(frame, Layout.viewX + Layout.viewW / 2, Layout.viewY - 1)
                != Bitmap.CLEAR && At(frame, Layout.viewX + Layout.viewW / 2,
                Layout.viewY + Layout.viewH) != Bitmap.CLEAR);

        int parchment = At(frame, Layout.chatX + Layout.chatW / 2,
                Layout.chatY + Layout.chatH / 2);
        Check("the chat is on something", parchment != Bitmap.CLEAR);
        Check("which fills its rectangle, edge to edge",
                At(frame, Layout.chatX, Layout.chatY + Layout.chatH / 2)
                == parchment && At(frame, Layout.chatX + Layout.chatW - 1,
                Layout.chatY + Layout.chatH / 2) == parchment &&
                At(frame, Layout.chatX + Layout.chatW / 2, Layout.chatY)
                == parchment && At(frame, Layout.chatX + Layout.chatW / 2,
                Layout.chatY + Layout.chatH - 1) == parchment);

        int square = At(frame, Layout.slotsX, Layout.slotsY);
        int tab = At(frame, Layout.topTabsX, Layout.topTabsY);
        for (int i = 0; i < Inventory.SLOTS; i++) {
            int x = Layout.slotsX + i % Layout.ACROSS * Layout.pitch;
            int y = Layout.slotsY + i / Layout.ACROSS * Layout.pitch;

            Check("slot " + i + " is a square of the frame",
                    At(frame, x, y) == square &&
                    At(frame, x + half, y + half) == square &&
                    At(frame, x + Layout.CELL - 1, y + Layout.CELL - 1)
                    == square);
            Check("with a gap before slot " + i,
                    At(frame, x - 1, y) != square &&
                    At(frame, x, y - 1) != square);
        }
        for (int i = 0; i < Layout.ACROSS; i++) {
            int dx = i * Layout.pitch;
            Check("tab " + i + " in each row is a square of the frame",
                    At(frame, Layout.topTabsX + dx, Layout.topTabsY) == tab
                    && At(frame, Layout.bottomTabsX + dx + half,
                    Layout.bottomTabsY + half) == tab);
        }
        Check("tabs and slots are told apart", tab != square);
        Fixture.Finish();
    }

    public void testIndexedPngsOfEveryDepthAreRead() throws Exception {
        int[] colors = { RED, BLUE, WHITE, BLACK };
        int[] depths = { 1, 2, 4, 8 };

        for (int d = 0; d < depths.length; d++) {
            int count = Math.min(colors.length, 1 << depths[d]);
            int w = 13, h = 7;
            byte[] r = new byte[count], g = new byte[count];
            byte[] b = new byte[count];
            for (int i = 0; i < count; i++) {
                r[i] = (byte) (colors[i] >> 16);
                g[i] = (byte) (colors[i] >> 8);
                b[i] = (byte) colors[i];
            }
            BufferedImage img = new BufferedImage(w, h, depths[d] == 8 ?
                    BufferedImage.TYPE_BYTE_INDEXED :
                    BufferedImage.TYPE_BYTE_BINARY,
                    new IndexColorModel(depths[d], count, r, g, b));
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    img.getRaster().setSample(x, y, 0, (x * 3 + y) % count);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            byte[] file = out.toByteArray();

            Check(depths[d] + " bits: seen to be indexed",
                    IndexedPng.Is(file));
            Image image = IndexedPng.Decode(file, "t.png");
            Check(depths[d] + " bits: size", image.width == w &&
                    image.height == h);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int at = (y * w + x) * 4;
                    int rgb = (image.data[at] & 0xFF) << 16 |
                            (image.data[at + 1] & 0xFF) << 8 |
                            (image.data[at + 2] & 0xFF);
                    Check(depths[d] + " bits: pixel " + x + "," + y,
                            rgb == colors[(x * 3 + y) % count] &&
                            image.data[at + 3] == (byte) 255);
                }
            }
        }
    }

    public void testAFullColorPngIsNotTakenForAnIndexedOne()
            throws Exception {
        BufferedImage img = new BufferedImage(4, 4,
                BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        Check("left to Whitetail", !IndexedPng.Is(out.toByteArray()));
        Check("nor is nonsense", !IndexedPng.Is(new byte[3]));
    }

    public void testADamagedIndexedPngIsExplained() throws Exception {
        BufferedImage img = new BufferedImage(8, 8,
                BufferedImage.TYPE_BYTE_INDEXED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] file = out.toByteArray();
        byte[] half = new byte[file.length / 2];
        System.arraycopy(file, 0, half, 0, half.length);

        try {
            IndexedPng.Decode(half, "half.png");
        } catch (DataError e) {
            Check("named; said: " + e.getMessage(),
                    e.getMessage().indexOf("half.png could not be read") >= 0);
            return;
        }
        Check("half a file was accepted", false);
    }

    public void testAPictureMustBeInPaletteColors() {
        Fixture.Start(0);
        byte[] good = {
            (byte) 0xB8, 0x25, 0x25, (byte) 255,     0, 0, 0, 0
        };
        Bitmap bitmap = Bitmap.FromImage(Image.Create(2, 1,
                Image.FORMAT_RGBA_8, good), "g.png");
        Check("a palette color keeps its place in the palette",
                (bitmap.texels[0] & 0xFF) == Palette.TEXT_WARN);
        Check("see-through is clear", bitmap.texels[1] == Bitmap.CLEAR);

        Refused("a color the palette lacks", new byte[] {
            1, 2, 3, (byte) 255
        }, "the color 010203, which is not in palette.json");
        Refused("a pixel partly see-through", new byte[] {
            (byte) 0xB8, 0x25, 0x25, (byte) 128
        }, "partly see-through");
        Fixture.Finish();
    }

    public void testDrawingABitmapLeavesWhatItsClearPixelsCover() {
        Fixture.Start(0);
        byte[] rgba = {
            (byte) 0xB8, 0x25, 0x25, (byte) 255,     0, 0, 0, 0,
            0, 0, 0, 0,     (byte) 0xB8, 0x25, 0x25, (byte) 255
        };
        Bitmap bitmap = Bitmap.FromImage(Image.Create(2, 2,
                Image.FORMAT_RGBA_8, rgba), "b.png");
        byte[] buf = new byte[4 * 4];
        byte under = (byte) Palette.TEXT, red = (byte) Palette.TEXT_WARN;

        Gfx.Init(buf, 4, 4);
        Gfx.FillRect(0, 0, 4, 4, Palette.TEXT);
        Gfx.DrawBitmap(bitmap, 1, 1);
        Check("solid pixels drawn", buf[1 * 4 + 1] == red &&
                buf[2 * 4 + 2] == red);
        Check("clear pixels not", buf[1 * 4 + 2] == under &&
                buf[2 * 4 + 1] == under);

        Gfx.FillRect(0, 0, 4, 4, Palette.TEXT);
        Gfx.DrawBitmap(bitmap, 3, 3);
        Gfx.DrawBitmap(bitmap, -1, -1);
        Check("cut off at the edges without harm", buf[15] == red &&
                buf[0] == red && buf[1] == under);

        Gfx.FillRect(0, 0, 4, 4, Palette.TEXT);
        Gfx.SetClip(0, 0, 2, 2);
        Gfx.DrawBitmap(bitmap, 1, 1);
        Check("and at the clip", buf[1 * 4 + 1] == red &&
                buf[2 * 4 + 2] == under);
        Gfx.Shutdown();
        Fixture.Finish();
    }

    public void testMistakesInTheLayoutFileAreExplained() {
        Fixture.Start(0);
        String rest = "\"frame\": \"frame.png\", \"widgets\": [0, 0, 1, 1], " +
                "\"chat\": [0, 0, 1, 1], \"slots\": [0, 0], \"top_tabs\": " +
                "[0, 0], \"bottom_tabs\": [0, 0]";

        RefusedLayout("a view off the picture", "{ \"picture\": [384, 216], " +
                "\"view\": [300, 5, 100, 100], \"pitch\": 18, " + rest + " }",
                "[view] must be a rectangle");
        RefusedLayout("slots that would overlap", "{ \"picture\": " +
                "[384, 216], \"view\": [5, 5, 100, 100], \"pitch\": 12, " +
                rest + " }", "[pitch] must be at least 16");
        RefusedLayout("a value the game has no use for", "{ \"picture\": " +
                "[384, 216], \"view\": [5, 5, 100, 100], \"pitch\": 18, " +
                "\"minimap\": [0, 0, 1, 1], " + rest + " }", "[minimap]");

        Check("the real one back", Layout.Load());
        Fixture.Finish();
    }

    private static int At(Bitmap bitmap, int x, int y) {
        return bitmap.texels[y * bitmap.w + x] & 0xFF;
    }

    private static void Refused(String what, byte[] rgba, String expected) {
        try {
            Bitmap.FromImage(Image.Create(1, 1, Image.FORMAT_RGBA_8, rgba),
                    "p.png");
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }

    private static void RefusedLayout(String what, String json,
                                      String expected) {
        try {
            Layout.LoadOrThrow(DataNode.Parse(json, "layout.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
