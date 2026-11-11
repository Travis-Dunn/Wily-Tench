package com.alchemysoftware.wilytench;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import com.alchemysoftware.whitetail.json.JsonArray;
import com.alchemysoftware.whitetail.json.JsonObject;
import com.alchemysoftware.whitetail.json.JsonParser;
import com.alchemysoftware.whitetail.json.JsonResult;
import com.alchemysoftware.whitetail.json.JsonType;
import com.alchemysoftware.whitetail.json.JsonValue;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * One { ... } group of named values from a JSON data file, with the
 * means to read them strictly.
 *
 * The game's data files are edited by hand, so every reader here
 * checks what it reads and, when something is wrong, throws a
 * DataError that says in plain words which file, which entry, which
 * value, and what was expected. In particular a name the game does not
 * know is an error, never ignored, since the usual cause is a typing
 * mistake. The one exception is "note", which any group may carry as a
 * comment for people; JSON has no comments of its own.
 *
 * "Where" is a description of the group for those messages, such as
 * "locs.json, loc [iron_rocks]".
 */
final class DataNode {
    private static final String FOLDER = "data";
    private static final String NOTE_KEY = "note";
    private static final int TENTHS = 10;
    private static final double TENTHS_SLACK = 1e-6;

    private final JsonObject object;
    private final String where;

    private DataNode(JsonObject object, String where) {
        this.object = object;
        this.where = where;
    }

    /**
     * Reads a file from the "data" resource folder.
     *
     * @return the file's outermost group
     */
    static DataNode Load(String filename) {
        return LoadFrom(FOLDER, filename);
    }

    /**
     * Reads a JSON file from any resource folder.
     *
     * @return the file's outermost group
     */
    static DataNode LoadFrom(String folder, String filename) {
        InputStream in = ResourceStreamResolver.Get(folder, filename);
        if (in == null)
            throw new DataError(filename + " could not be opened.");

        try {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
                return Parse(bytes.toString("UTF-8"), filename);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new DataError(filename + " could not be read: " + e);
        }
    }

    /**
     * Reads JSON text as though it were the contents of a file.
     *
     * @param filename what to call the text in error messages
     */
    static DataNode Parse(String text, String filename) {
        JsonResult result = JsonParser.Parse(text);

        if (!result.success) {
            throw new DataError(filename + " is not valid JSON, at line " +
                    result.error.line + ", column " + result.error.column +
                    ": " + result.error.message + "\n" +
                    result.error.context);
        }
        if (result.value.type != JsonType.OBJECT) {
            throw new DataError(filename + " must be a { ... } group at " +
                    "its outermost level.");
        }
        return new DataNode(result.value.objectValue, filename);
    }

    /** @return an error about this group as a whole */
    DataError Fail(String why) {
        return new DataError(where + ": " + why);
    }

    /** @return the same group, described differently in messages */
    DataNode Called(String where) {
        return new DataNode(object, where);
    }

    boolean Has(String key) { return object.get(key) != null; }

    /**
     * Refuses any value whose name is not in the list, "note" aside.
     */
    void Only(String[] known) {
        for (Iterator it = object.entries.keySet().iterator();
                it.hasNext();) {
            String key = (String) it.next();
            boolean ok = key.equals(NOTE_KEY);

            for (int i = 0; !ok && i < known.length; i++)
                ok = known[i].equals(key);
            if (!ok) {
                throw Fail("there is a value called [" + key + "], which " +
                        "is not one the game knows. The ones it knows " +
                        "are: " + Join(known) + ".");
            }
        }
    }

    /** @return the names of the values in the group, in no fixed order */
    String[] Keys() {
        String[] keys = new String[object.entries.size()];
        int n = 0;
        for (Iterator it = object.entries.keySet().iterator();
                it.hasNext();) keys[n++] = (String) it.next();
        return keys;
    }

    /** @return the text called key, which must be there and not empty */
    String Str(String key) {
        JsonValue v = Need(key, JsonType.STRING, "text in double quotes");
        if (v.stringValue.length() == 0)
            throw Fail("[" + key + "] is empty.");
        return v.stringValue;
    }

    /** @return the text called key, or fallback if there is none */
    String OptStr(String key, String fallback) {
        return Has(key) ? Str(key) : fallback;
    }

    /** @return the whole number called key, which must be there */
    int Int(String key) {
        return Whole(Need(key, JsonType.NUMBER, "a whole number"),
                "[" + key + "]");
    }

    /** @return the whole number called key, or fallback */
    int OptInt(String key, int fallback) {
        return Has(key) ? Int(key) : fallback;
    }

    /**
     * @return the number called key multiplied by ten, for values such
     *         as experience that the game counts in tenths; it may
     *         have at most one decimal place
     */
    int Tenths(String key) {
        double v = Need(key, JsonType.NUMBER, "a number").numberValue;
        double tenths = v * TENTHS;
        long rounded = Math.round(tenths);

        if (Math.abs(tenths - rounded) > TENTHS_SLACK ||
                Math.abs(tenths) > Integer.MAX_VALUE) {
            throw Fail("[" + key + "] is " + v + ", but may have at most " +
                    "one decimal place.");
        }
        return (int) rounded;
    }

    /** @return the true or false called key, or fallback */
    boolean OptBool(String key, boolean fallback) {
        if (!Has(key)) return fallback;
        return Need(key, JsonType.BOOLEAN, "true or false").booleanValue;
    }

    /** @return the group called key, which must be there */
    DataNode Obj(String key) {
        return new DataNode(Need(key, JsonType.OBJECT,
                "a { ... } group").objectValue, where + ", [" + key + "]");
    }

    /**
     * @return the groups in the list called key, which must be there;
     *         each is described as "what number N" until given a
     *         better name with Called()
     */
    DataNode[] Groups(String key, String what) {
        JsonArray array = List(key);
        DataNode[] nodes = new DataNode[array.elements.size()];

        for (int i = 0; i < nodes.length; i++) {
            JsonValue v = array.get(i);
            String each = where + ", " + what + " number " + (i + 1);

            if (v.type != JsonType.OBJECT) {
                throw new DataError(each + " must be a { ... } group, " +
                        "but is " + Describe(v) + ".");
            }
            nodes[i] = new DataNode(v.objectValue, each);
        }
        return nodes;
    }

    /** @return the groups in the list called key, or none */
    DataNode[] OptGroups(String key, String what) {
        return Has(key) ? Groups(key, what) : new DataNode[0];
    }

    /** @return the texts in the list called key, which must be there */
    String[] Strs(String key) {
        JsonArray array = List(key);
        String[] out = new String[array.elements.size()];

        for (int i = 0; i < out.length; i++) {
            JsonValue v = array.get(i);
            if (v.type != JsonType.STRING) {
                throw Fail("entry " + (i + 1) + " of [" + key + "] must " +
                        "be text in double quotes, but is " + Describe(v) +
                        ".");
            }
            out[i] = v.stringValue;
        }
        return out;
    }

    /**
     * @return the whole numbers in the list called key, which must be
     *         there and hold exactly count of them
     */
    int[] Ints(String key, int count) {
        return WholeList(List(key), count, "[" + key + "]");
    }

    /** @return as Ints(), or null if there is no list called key */
    int[] OptInts(String key, int count) {
        return Has(key) ? Ints(key, count) : null;
    }

    /**
     * @return the lists of whole numbers in the list called key, which
     *         must be there, hold at least one, and each hold exactly
     *         count numbers: [[5, 1], [6, 1]] for count 2
     */
    int[][] IntLists(String key, int count) {
        JsonArray array = List(key);
        int[][] out = new int[array.elements.size()][];

        if (out.length == 0) throw Fail("[" + key + "] is an empty list.");
        for (int i = 0; i < out.length; i++) {
            JsonValue v = array.get(i);
            String each = "entry " + (i + 1) + " of [" + key + "]";

            if (v.type != JsonType.ARRAY) {
                throw Fail(each + " must be a [ ... ] list of " + count +
                        " whole numbers, but is " + Describe(v) + ".");
            }
            out[i] = WholeList(v.arrayValue, count, each);
        }
        return out;
    }

    /**
     * @return the pairs of texts in the list called key, which must be
     *         there: [["tan", "rust"], ["rust", "maroon"]]
     */
    String[][] StrPairs(String key) {
        JsonArray array = List(key);
        String[][] out = new String[array.elements.size()][];

        for (int i = 0; i < out.length; i++) {
            JsonValue v = array.get(i);
            boolean ok = v.type == JsonType.ARRAY &&
                    v.arrayValue.elements.size() == 2 &&
                    v.arrayValue.get(0).type == JsonType.STRING &&
                    v.arrayValue.get(1).type == JsonType.STRING;

            if (!ok) {
                throw Fail("entry " + (i + 1) + " of [" + key + "] must " +
                        "be a pair of texts, like [\"tan\", \"rust\"].");
            }
            out[i] = new String[] {
                v.arrayValue.get(0).stringValue,
                v.arrayValue.get(1).stringValue
            };
        }
        return out;
    }

    private JsonArray List(String key) {
        return Need(key, JsonType.ARRAY, "a [ ... ] list").arrayValue;
    }

    private JsonValue Need(String key, JsonType type, String expected) {
        JsonValue v = object.get(key);

        if (v == null) throw Fail("[" + key + "] is missing.");
        if (v.type != type) {
            throw Fail("[" + key + "] must be " + expected + ", but is " +
                    Describe(v) + ".");
        }
        return v;
    }

    private int[] WholeList(JsonArray array, int count, String what) {
        if (array.elements.size() != count) {
            throw Fail(what + " must hold exactly " + count +
                    " whole numbers, but holds " + array.elements.size() +
                    " things.");
        }

        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            JsonValue v = array.get(i);
            if (v.type != JsonType.NUMBER) {
                throw Fail(what + " must hold whole numbers, but holds " +
                        Describe(v) + ".");
            }
            out[i] = Whole(v, what);
        }
        return out;
    }

    private int Whole(JsonValue v, String what) {
        double d = v.numberValue;
        if (d != Math.floor(d) || Math.abs(d) > Integer.MAX_VALUE)
            throw Fail(what + " must be a whole number, but is " + d + ".");
        return (int) d;
    }

    private static String Describe(JsonValue v) {
        if (v.type == JsonType.STRING)
            return "the text \"" + v.stringValue + "\"";
        if (v.type == JsonType.NUMBER) return "the number " + v.numberValue;
        if (v.type == JsonType.BOOLEAN) return String.valueOf(v.booleanValue);
        if (v.type == JsonType.ARRAY) return "a [ ... ] list";
        if (v.type == JsonType.OBJECT) return "a { ... } group";
        return "null";
    }

    private static String Join(String[] words) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
