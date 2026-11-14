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
 * game pixel. When the picture has come down a video cable (Signal) it
 * is one layer with no holes, and finer across than game pixels.
 *
 * The numbers that are the look are knobs, below.
 */

uniform sampler2D picture;
uniform sampler2D world;
uniform vec2 pictureTexels;     /* the size of the picture's texture */
uniform vec2 worldTexels;       /* the size of the world's texture */
uniform vec2 pictureSize;       /* the picture, in its own texels */
uniform vec2 worldSize;         /* the world layer, in game pixels */
uniform vec2 viewOrigin;        /* where the view is on the picture */
uniform vec2 slide;             /* how far the world is slid left and up */
uniform float scale;            /* screen pixels to a game pixel */
uniform float across;           /* picture texels to a game pixel, across */

/*
 * The look. These are knobs: Tuning has their names, ranges, and the
 * settings they start at, and they can be turned while the game runs.
 */

/* How wide a scanline's glow is, as a share of the distance between
   scanlines. Smaller is thinner lines with darker gaps. */
uniform float scanWidth;
/* How much of the scanlines is applied: 0 none, 1 all. */
uniform float scanDepth;
/* How much of the shadow mask is applied: 0 none, 1 all. At 1 a pixel
   under a red stripe shows no green or blue at all. */
uniform float maskDepth;
/* How many red-green-blue triads of the mask there are to a game
   pixel, across. */
uniform float maskPitch;
/* How wide each stripe of the mask is, as a share of a triad. A third
   leaves no gaps between them. */
uniform float stripeWidth;
/* How much of a mask cell's height is lit; the rest is the gap under
   it. At 1 the stripes run unbroken from top to bottom, as on an
   aperture grille. */
uniform float cellLit;
/* How far down every other column of cells is set, as a share of a
   cell: 0 for a plain grid, a half for bricks. */
uniform float cellStagger;
/* How much of the light is spread about as glow, and how far, in
   game pixels. This glow moves light and adds none. */
uniform float glow;
uniform float glowSpread;
/* How much of the same blurred picture is added on top, which does
   make the picture brighter, bright areas most. */
uniform float bloom;
/* How many screen pixels the change from one game pixel to the next
   takes along a scanline. */
uniform float beamBlur;

/* Where in a triad each stripe starts: red, green, blue, the blue a
   little apart from the others, as Bisqwit has them. */
const vec3 STRIPE_START = vec3(0.0, 0.3, 0.6);

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
   texel running into the next over a short distance. The layer has
   grid texels to a game pixel across. */
vec4 Beam(sampler2D layer, vec2 texels, vec2 size, vec2 p, float grid)
{
    float x = p.x * grid - 0.5, left = floor(x), row = floor(p.y);
    float along = clamp((x - left - 0.5) * scale / (grid * beamBlur) +
            0.5, 0.0, 1.0);

    return mix(Texel(layer, texels, size, vec2(left, row)),
            Texel(layer, texels, size, vec2(left + 1.0, row)), along);
}

/* The two layers together at one game pixel, for the glow. */
vec3 Both(vec2 cell)
{
    vec4 over = Texel(picture, pictureTexels, pictureSize,
            vec2(cell.x * across + floor(across * 0.5), cell.y));
    vec4 under = Texel(world, worldTexels, worldSize,
            floor(cell + 0.5 - viewOrigin + slide));

    return over.rgb + (1.0 - over.a) * under.rgb;
}

/* How much of the stretch from a to b lies between lo and hi. */
float Overlap(float a, float b, float lo, float hi)
{
    return max(0.0, min(b, hi) - max(a, lo));
}

/* The same for a pattern that repeats every 1: a stretch that runs
   off one end comes back on at the other. */
float Wrapped(float a, float b, float lo, float hi)
{
    return Overlap(a, b, lo, hi) + Overlap(a + 1.0, b + 1.0, lo, hi) +
            Overlap(a - 1.0, b - 1.0, lo, hi);
}

/* How much of this screen pixel each color's stripe covers. Across,
   places are counted in triads; down, in cells, which are a scanline
   tall. */
vec3 Mask(vec2 p)
{
    float across_px = 0.5 * maskPitch / scale, down_px = 0.5 / scale;
    float triad = p.x * maskPitch;
    float x = fract(triad);
    float y = fract(p.y + cellStagger * mod(floor(triad), 2.0));
    float x0 = x - across_px, x1 = x + across_px;
    float y0 = y - down_px, y1 = y + down_px;

    vec3 lit = vec3(
            Wrapped(x0, x1, STRIPE_START.r, STRIPE_START.r + stripeWidth),
            Wrapped(x0, x1, STRIPE_START.g, STRIPE_START.g + stripeWidth),
            Wrapped(x0, x1, STRIPE_START.b, STRIPE_START.b + stripeWidth));
    float gap = Wrapped(y0, y1, cellLit, 1.0);

    return lit / (x1 - x0) * (1.0 - gap / (y1 - y0));
}

/* A scanline's brightness at a height through it, 0 to 1. */
float Scanline(float n)
{
    float d = (n - 0.5) / scanWidth;
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

    vec4 over = Beam(picture, pictureTexels, pictureSize, p, across);
    vec4 under = Beam(world, worldTexels, worldSize,
            p - viewOrigin + slide, 1.0);
    vec3 light = over.rgb + (1.0 - over.a) * under.rgb;

    /* The share of the mask that is lit, and the average of
       Scanline() over a line, by the four screen rows of a typical
       one: what the two are divided by to keep the picture as bright
       as it was. */
    float maskMean = stripeWidth * cellLit;
    float scanMean = 0.25 * (Scanline(0.125) + Scanline(0.375) +
            Scanline(0.625) + Scanline(0.875));
    float scan = mix(1.0, Scanline(fract(p.y)) / scanMean, scanDepth);
    vec3 mask = mix(vec3(1.0), Mask(p) / maskMean, maskDepth);

    /* By how much the two brighten the luckiest screen pixel, and so
       how much of them each channel of this color has room for. */
    float most = mix(1.0, 1.0 / scanMean, scanDepth) * mix(1.0,
            min(stripeWidth * scale / maskPitch, 1.0) / maskMean,
            maskDepth);
    vec3 room = clamp((1.0 / max(light, vec3(0.001)) - 1.0) /
            max(most - 1.0, 0.001), 0.0, 1.0);

    vec3 spread = vec3(0.0);
    float weights = 0.0;
    vec2 cell = floor(p);
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            vec2 at = cell + vec2(float(dx), float(dy));
            vec2 d = (at + 0.5 - p) / glowSpread;
            float w = exp(-0.5 * dot(d, d));
            spread += w * Both(at);
            weights += w;
        }
    }

    /* The glow is taken out of the picture before it is put back
       spread, so it too moves light and adds none. */
    spread /= weights;
    light = (1.0 - glow) * light * mix(vec3(1.0), scan * mask, room) +
            (glow + bloom) * spread;
    gl_FragColor = vec4(sqrt(Pale(light)), 1.0);
}
