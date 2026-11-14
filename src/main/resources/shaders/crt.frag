#version 120

/*
 * The CRT filter: shows the game's picture as a cathode ray tube would.
 *
 * The steps are those of Bisqwit's CRT filter, as its write-up describes
 * them (https://github.com/bisqwit/crt-filter), done for one screen pixel
 * at a time instead of for a whole picture, and with nothing of that
 * program's code:
 *
 *   1. Colors are turned into amounts of light.
 *   2. Each row of game pixels is a scanline: brightest along its
 *      middle, fading above and below.
 *   3. The light passes a shadow mask: one red, one green, and one blue
 *      stripe to each game pixel, with a dark gap after each, cut into
 *      cells one scanline tall, and every other column's cells half a
 *      cell lower.
 *   4. A blurred copy of the picture is mixed in: the glow of the
 *      glass.
 *   5. Light too bright to show is not cut off channel by channel,
 *      which would change its color, but paled toward white.
 *   6. Amounts of light are turned back into colors.
 *
 * One thing is this filter's own. A tube's phosphors can be driven far
 * brighter than white, so a mask that blocks most of the light still
 * leaves a bright picture; a flat screen's pixels cannot. The
 * scanlines and the mask are therefore made to move light about and
 * not to remove it: what they take from one screen pixel they give to
 * its neighbors, so an area is as bright, and the same color, as it
 * was. A color with no room left to be brightened, such as white, is
 * given less of both, down to none, which is also how an overdriven
 * tube looks.
 *
 * Positions are in game pixels from the top left of the picture. The
 * picture is two layers, as Screen describes: the interface, and under
 * it, wherever the interface has no alpha, the world, slid by part of a
 * game pixel.
 *
 * The numbers under "To taste" are the look, and are meant to be
 * changed.
 */

uniform sampler2D picture;
uniform sampler2D world;
uniform vec2 pictureTexels;     /* the size of the picture's texture */
uniform vec2 worldTexels;       /* the size of the world's texture */
uniform vec2 pictureSize;       /* the picture, in game pixels */
uniform vec2 worldSize;         /* the world layer, in game pixels */
uniform vec2 viewOrigin;        /* where the view is on the picture */
uniform vec2 slide;             /* how far the world is slid left and up */
uniform float scale;            /* screen pixels to a game pixel */

/* To taste. */

/* How wide a scanline's glow is, as a share of the distance between
   scanlines. Smaller is thinner lines with darker gaps. */
const float SCAN_WIDTH = 0.30;
/* How much of the scanlines is applied: 0 none, 1 all. */
const float SCAN_DEPTH = 0.60;
/* How much of the shadow mask is applied: 0 none, 1 all. At 1 a pixel
   under a red stripe shows no green or blue at all. */
const float MASK_DEPTH = 0.30;
/* How much of the light is spread about as glow, and how far, in
   game pixels. */
const float GLOW = 0.22;
const float GLOW_SPREAD = 0.75;
/* How many screen pixels the change from one game pixel to the next
   takes along a scanline. */
const float BEAM_BLUR = 1.5;

/* The shadow mask, in tenths of a game pixel across and sixths down:
   where each stripe starts and ends, and where a cell's gap starts. */
const vec3 STRIPE_START = vec3(0.0, 0.3, 0.6);
const vec3 STRIPE_END = vec3(0.2, 0.5, 0.8);
const float CELL_LIT = 5.0 / 6.0;
const float CELL_STAGGER = 0.5;

/* The share of the mask that is lit, and the average height of a
   scanline's glow, used to keep the picture as bright as it was. */
const float MASK_MEAN = 0.2 * CELL_LIT;

/* How bright the eye finds red, green, and blue light. */
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

/* One game pixel of a layer as light, with its alpha. */
vec4 Texel(sampler2D layer, vec2 texels, vec2 size, vec2 cell)
{
    vec4 c = texture2D(layer,
            (clamp(cell, vec2(0.0), size - 1.0) + 0.5) / texels);
    return vec4(c.rgb * c.rgb, c.a);
}

/* A layer as the beam draws it: a whole row of game pixels, each
   running into the next over a short distance. */
vec4 Beam(sampler2D layer, vec2 texels, vec2 size, vec2 p)
{
    float x = p.x - 0.5, left = floor(x), row = floor(p.y);
    float along = clamp((x - left - 0.5) * scale / BEAM_BLUR + 0.5,
            0.0, 1.0);

    return mix(Texel(layer, texels, size, vec2(left, row)),
            Texel(layer, texels, size, vec2(left + 1.0, row)), along);
}

/* The two layers together at one game pixel, for the glow. */
vec3 Both(vec2 cell)
{
    vec4 over = Texel(picture, pictureTexels, pictureSize, cell);
    vec4 under = Texel(world, worldTexels, worldSize,
            floor(cell + 0.5 - viewOrigin + slide));

    return over.rgb + (1.0 - over.a) * under.rgb;
}

/* How much of the stretch from a to b lies between lo and hi. */
float Overlap(float a, float b, float lo, float hi)
{
    return max(0.0, min(b, hi) - max(a, lo));
}

/* How much of this screen pixel each color's stripe covers. */
vec3 Mask(vec2 p)
{
    float reach = 0.5 / scale;
    float x = fract(p.x);
    float y = fract(p.y + CELL_STAGGER * mod(floor(p.x), 2.0));
    float x0 = x - reach, x1 = x + reach;
    float y0 = y - reach, y1 = y + reach;

    vec3 across = vec3(
            Overlap(x0, x1, STRIPE_START.r, STRIPE_END.r),
            Overlap(x0, x1, STRIPE_START.g, STRIPE_END.g),
            Overlap(x0, x1, STRIPE_START.b, STRIPE_END.b));
    float gap = Overlap(y0, y1, CELL_LIT, 1.0) +
            Overlap(y0 + 1.0, y1 + 1.0, CELL_LIT, 1.0) +
            Overlap(y0 - 1.0, y1 - 1.0, CELL_LIT, 1.0);

    return across * scale * (1.0 - gap * scale);
}

/* A scanline's brightness at a height through it, 0 to 1. */
float Scanline(float n)
{
    float d = (n - 0.5) / SCAN_WIDTH;
    return exp(-0.5 * d * d);
}

/* Brings light into the range a screen can show, keeping how bright
   it looks and giving up how strong its color is. */
vec3 Pale(vec3 c)
{
    float luma = dot(c, LUMA);
    if (luma >= 1.0) return vec3(1.0);

    float most = max(c.r, max(c.g, c.b));
    float keep = most > 1.0 ? (1.0 - luma) / (most - luma) : 1.0;
    return clamp((c - luma) * keep + luma, 0.0, 1.0);
}

void main()
{
    vec2 p = gl_TexCoord[0].xy;

    vec4 over = Beam(picture, pictureTexels, pictureSize, p);
    vec4 under = Beam(world, worldTexels, worldSize,
            p - viewOrigin + slide);
    vec3 light = over.rgb + (1.0 - over.a) * under.rgb;

    /* The average of Scanline() over a line, by the four screen rows
       of a typical one. */
    float scanMean = 0.25 * (Scanline(0.125) + Scanline(0.375) +
            Scanline(0.625) + Scanline(0.875));
    float scan = mix(1.0, Scanline(fract(p.y)) / scanMean, SCAN_DEPTH);
    vec3 mask = mix(vec3(1.0), Mask(p) / MASK_MEAN, MASK_DEPTH);

    /* By how much the two brighten the luckiest screen pixel, and so
       how much of them each channel of this color has room for. */
    float most = mix(1.0, 1.0 / scanMean, SCAN_DEPTH) * mix(1.0,
            min(STRIPE_END.r * scale, 1.0) / MASK_MEAN, MASK_DEPTH);
    vec3 room = clamp((1.0 / max(light, vec3(0.001)) - 1.0) /
            (most - 1.0), 0.0, 1.0);

    vec3 glow = vec3(0.0);
    float weights = 0.0;
    vec2 cell = floor(p);
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 at = cell + vec2(float(dx), float(dy));
            vec2 d = (at + 0.5 - p) / GLOW_SPREAD;
            float w = exp(-0.5 * dot(d, d));
            glow += w * Both(at);
            weights += w;
        }
    }

    /* The glow is taken out of the picture before it is put back
       spread, so it too moves light and adds none. */
    light = (1.0 - GLOW) * light * mix(vec3(1.0), scan * mask, room) +
            GLOW * glow / weights;
    gl_FragColor = vec4(sqrt(Pale(light)), 1.0);
}
