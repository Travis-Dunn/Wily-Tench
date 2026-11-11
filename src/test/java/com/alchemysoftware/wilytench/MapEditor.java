package com.alchemysoftware.wilytench;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * The map editor: a desktop program for making and changing the
 * game's maps. It is a tool for whoever is building the game and is
 * not part of the game. Run it with the "Map Editor" run configuration
 * or
 *
 *     mvn test-compile exec:java@editor
 *
 * from the project's root folder.
 *
 * It edits the map files in src/main/resources/maps, and nothing else:
 * there is no editor format and nothing to export. A map saved here is
 * the same text file a person or an AI would have typed, in the same
 * tidy layout, and a map either of them typed opens here. MapFile
 * reads and writes the file. MapEdit, which has no window, holds the
 * map and makes the changes; this class shows it and turns the mouse
 * into changes.
 *
 * The map is drawn by the game's own code from the game's own data
 * files, so it looks as it will in the game. What can be painted is
 * whatever the map's tileset in tiles.json offers, the people in
 * npcs.json, and named points.
 *
 * Using it:
 *
 *   Pick something from the lists on the left, then paint with the
 *   left mouse button. The pencil paints tile by tile as it is
 *   dragged; the rectangle fills the area dragged over.
 *   The right mouse button picks up whatever is under it.
 *   The map grows as tiles are painted past its right and bottom
 *   edges. To make room at the top or left, use the Map menu.
 *   Things only this map has, a person or a named point, are on the
 *   "This map" list, and are added with the buttons under it.
 *   The line at the bottom says what is under the pointer and, in
 *   red, whatever stops the map being used by the game as it stands.
 *
 * Saving also redraws the map's picture in docs/maps.
 */
public final class MapEditor {
    private static final String MAPS_DIR = MapTool.GAME_MAPS;
    private static final String TITLE = "Wily Tench Map Editor";
    private static final int TILE = MapPicture.TILE;

    private static final int MIN_ZOOM = 1, MAX_ZOOM = 6, START_ZOOM = 3;
    /* Tiles of empty space shown past the map's right and bottom
       edges, to paint into. */
    private static final int SPARE = 12;
    private static final int ICON_SCALE = 2;
    private static final int PALETTE_W = 250;

    private static final int TOOL_PENCIL = 0, TOOL_RECT = 1;

    private static final Color EMPTY = new Color(0x26262E);
    private static final Color GRID = new Color(0x38FFFFFF, true);
    private static final Color EDGE = new Color(0x90FFE66E, true);
    private static final Color HOVER = new Color(0xFFFFFF);
    private static final Color LABEL = new Color(0x7DFFB4);
    private static final Color PROBLEM = new Color(0xB00020);

    /* One line of a palette list: something that can be painted. */
    private static final class Entry {
        final char symbol;
        final String label;
        final ImageIcon icon;

        Entry(char symbol, String label, ImageIcon icon) {
            this.symbol = symbol;
            this.label = label;
            this.icon = icon;
        }
    }

    private final JFrame frame = new JFrame(TITLE);
    private final Canvas canvas = new Canvas();
    private final JLabel status = new JLabel(" ");
    private final JLabel problem = new JLabel(" ");
    private final DefaultListModel groundModel = new DefaultListModel();
    private final DefaultListModel thingModel = new DefaultListModel();
    private final DefaultListModel ownModel = new DefaultListModel();
    private final JList groundList = new JList(groundModel);
    private final JList thingList = new JList(thingModel);
    private final JList ownList = new JList(ownModel);
    private final JLabel brushLabel = new JLabel();

    private File file;
    private MapEdit edit;
    private BufferedImage picture;

    private char brush = Tiles.NOTHING;
    private int tool = TOOL_PENCIL;
    private int zoom = START_ZOOM;
    private boolean showGrid = true, showSymbols = true;
    private boolean selecting;

    private AbstractAction undoAction, redoAction;

    private MapEditor() {}

    public static void main(final String[] args) {
        try {
            if (!new File(MAPS_DIR).isDirectory()) {
                throw new IllegalStateException("The map editor must be " +
                        "run from the project's root folder, the one " +
                        "holding pom.xml; there is no [" + MAPS_DIR +
                        "] here.");
            }
            MapPicture.Start();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), TITLE,
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MapEditor editor = new MapEditor();
                editor.Build();
                File first = args.length > 0 ? new File(MAPS_DIR, args[0]) :
                        editor.FirstMap();
                if (first != null) editor.Load(first);
            }
        });
    }

    /* ---- The window ---- */

    private void Build() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { Quit(); }
        });

        JScrollPane scroll = new JScrollPane(canvas);
        scroll.getViewport().setBackground(EMPTY);
        scroll.getVerticalScrollBar().setUnitIncrement(TILE * 2);
        scroll.getHorizontalScrollBar().setUnitIncrement(TILE * 2);

        JPanel bottom = new JPanel(new GridLayout(2, 1));
        bottom.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        problem.setForeground(PROBLEM);
        bottom.add(status);
        bottom.add(problem);

        frame.setJMenuBar(Menus());
        frame.getContentPane().add(Tools(), BorderLayout.NORTH);
        frame.getContentPane().add(PalettePanel(), BorderLayout.WEST);
        frame.getContentPane().add(scroll, BorderLayout.CENTER);
        frame.getContentPane().add(bottom, BorderLayout.SOUTH);
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JMenuBar Menus() {
        JMenuBar bar = new JMenuBar();
        int ctrl = InputEvent.CTRL_MASK;

        undoAction = new AbstractAction("Undo") {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent e) {
                edit.Undo();
                Changed();
            }
        };
        redoAction = new AbstractAction("Redo") {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(ActionEvent e) {
                edit.Redo();
                Changed();
            }
        };

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(Item("New map...", KeyEvent.VK_N, ctrl,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { NewMap(); }
        }));
        fileMenu.add(Item("Open...", KeyEvent.VK_O, ctrl,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { Open(); }
        }));
        fileMenu.add(Item("Save", KeyEvent.VK_S, ctrl, new ActionListener() {
            public void actionPerformed(ActionEvent e) { Save(); }
        }));
        fileMenu.addSeparator();
        fileMenu.add(Item("Quit", KeyEvent.VK_Q, ctrl, new ActionListener() {
            public void actionPerformed(ActionEvent e) { Quit(); }
        }));

        JMenu editMenu = new JMenu("Edit");
        JMenuItem undo = new JMenuItem(undoAction);
        undo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrl));
        JMenuItem redo = new JMenuItem(redoAction);
        redo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ctrl));
        editMenu.add(undo);
        editMenu.add(redo);

        JMenu mapMenu = new JMenu("Map");
        mapMenu.add(Item("Rename the place...", 0, 0, new ActionListener() {
            public void actionPerformed(ActionEvent e) { Rename(); }
        }));
        mapMenu.addSeparator();
        mapMenu.add(Item("Add a row at the top", 0, 0, new ActionListener() {
            public void actionPerformed(ActionEvent e) { Shift(0, 1); }
        }));
        mapMenu.add(Item("Add a column at the left", 0, 0,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { Shift(1, 0); }
        }));
        mapMenu.add(Item("Crop the empty space around the map", 0, 0,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!ConfirmMove()) return;
                edit.Begin();
                edit.Crop();
                Changed();
            }
        }));

        JMenu viewMenu = new JMenu("View");
        viewMenu.add(Item("Zoom in", KeyEvent.VK_EQUALS, ctrl,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { Zoom(1); }
        }));
        viewMenu.add(Item("Zoom out", KeyEvent.VK_MINUS, ctrl,
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { Zoom(-1); }
        }));

        bar.add(fileMenu);
        bar.add(editMenu);
        bar.add(mapMenu);
        bar.add(viewMenu);
        return bar;
    }

    private static JMenuItem Item(String text, int key, int modifiers,
                                  ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (key != 0)
            item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        item.addActionListener(action);
        return item;
    }

    private JToolBar Tools() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        final JToggleButton pencil = new JToggleButton("Pencil", true);
        final JToggleButton rect = new JToggleButton("Rectangle");
        pencil.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tool = TOOL_PENCIL;
                pencil.setSelected(true);
                rect.setSelected(false);
            }
        });
        rect.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tool = TOOL_RECT;
                rect.setSelected(true);
                pencil.setSelected(false);
            }
        });

        final JToggleButton grid = new JToggleButton("Grid", true);
        grid.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showGrid = grid.isSelected();
                canvas.repaint();
            }
        });
        final JToggleButton symbols = new JToggleButton("Symbols", true);
        symbols.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showSymbols = symbols.isSelected();
                canvas.repaint();
            }
        });

        bar.add(new JButton(undoAction));
        bar.add(new JButton(redoAction));
        bar.addSeparator();
        bar.add(pencil);
        bar.add(rect);
        bar.addSeparator();
        bar.add(grid);
        bar.add(symbols);
        bar.addSeparator();
        bar.add(new JLabel("Painting: "));
        bar.add(brushLabel);
        return bar;
    }

    private JComponent PalettePanel() {
        JTabbedPane tabs = new JTabbedPane();
        JPanel own = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel(new GridLayout(4, 1, 0, 2));

        SetUpList(groundList);
        SetUpList(thingList);
        SetUpList(ownList);

        buttons.add(Button("Add a person...", new ActionListener() {
            public void actionPerformed(ActionEvent e) { AddPerson(); }
        }));
        buttons.add(Button("Add a named point...", new ActionListener() {
            public void actionPerformed(ActionEvent e) { AddPoint(); }
        }));
        buttons.add(Button("Add a thing on a chosen floor...",
                new ActionListener() {
            public void actionPerformed(ActionEvent e) { AddCopy(); }
        }));
        buttons.add(Button("Remove the selected one", new ActionListener() {
            public void actionPerformed(ActionEvent e) { RemoveOwn(); }
        }));
        own.add(new JScrollPane(ownList), BorderLayout.CENTER);
        own.add(buttons, BorderLayout.SOUTH);

        tabs.addTab("Ground", new JScrollPane(groundList));
        tabs.addTab("Things", new JScrollPane(thingList));
        tabs.addTab("This map", own);
        tabs.setPreferredSize(new Dimension(PALETTE_W, 100));
        return tabs;
    }

    private static JButton Button(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        return button;
    }

    private void SetUpList(final JList list) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            public Component getListCellRendererComponent(JList l,
                    Object value, int index, boolean selected,
                    boolean focus) {
                Entry entry = (Entry) value;
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        l, entry.label, index, selected, focus);
                label.setIcon(entry.icon);
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                return label;
            }
        });
        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                Entry entry = (Entry) list.getSelectedValue();
                if (selecting || entry == null) return;
                SetBrush(entry.symbol);
            }
        });
    }

    /* ---- Files ---- */

    private File FirstMap() {
        String[] names = MapNames();
        return names.length > 0 ? new File(MAPS_DIR, names[0]) : null;
    }

    private static String[] MapNames() {
        String[] all = new File(MAPS_DIR).list();
        java.util.ArrayList names = new java.util.ArrayList();

        for (int i = 0; all != null && i < all.length; i++)
            if (all[i].endsWith(".txt")) names.add(all[i]);
        java.util.Collections.sort(names);
        return (String[]) names.toArray(new String[names.size()]);
    }

    private void Load(File from) {
        try {
            MapFile map = MapFile.Parse(MapTool.ReadFile(from),
                    from.getName());
            MapEdit loaded = new MapEdit(map);
            /* Refuse now what could not be shown. */
            World.Build(from.getName(), map, false);

            file = from;
            edit = loaded;
            FillPalettes();
            SetBrush(Tiles.NOTHING);
            Changed();
        } catch (DataError e) {
            Complain("This map cannot be opened until the mistake in it " +
                    "is put right in a text editor.\n\n" + e.getMessage());
        } catch (IOException e) {
            Complain("The file could not be read: " + e.getMessage());
        }
    }

    private void Open() {
        if (!MayDiscard()) return;

        String[] names = MapNames();
        if (names.length == 0) {
            Complain("There are no maps in " + MAPS_DIR + ".");
            return;
        }
        Object choice = JOptionPane.showInputDialog(frame,
                "Which map?", "Open", JOptionPane.PLAIN_MESSAGE, null,
                names, file == null ? names[0] : file.getName());
        if (choice != null) Load(new File(MAPS_DIR, (String) choice));
    }

    private void NewMap() {
        if (!MayDiscard()) return;

        JTextField fileName = new JTextField("newplace");
        JTextField placeName = new JTextField("New Place");
        JComboBox tileset = new JComboBox(Tiles.Names().split(", "));
        JPanel panel = new JPanel(new GridLayout(6, 1));

        panel.add(new JLabel("File name (lower-case letters, digits, " +
                "underscores):"));
        panel.add(fileName);
        panel.add(new JLabel("What the place is called:"));
        panel.add(placeName);
        panel.add(new JLabel("Tileset, which cannot be changed later:"));
        panel.add(tileset);

        if (JOptionPane.showConfirmDialog(frame, panel, "New map",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;

        String name = fileName.getText().trim();
        if (!name.matches("[a-z][a-z0-9_]*") ||
                placeName.getText().trim().length() == 0) {
            Complain("The file name must be lower-case letters, digits, " +
                    "and underscores, beginning with a letter, and the " +
                    "place needs a name.");
            return;
        }
        File target = new File(MAPS_DIR, name + ".txt");
        if (target.exists()) {
            Complain("There is already a map called " + target.getName() +
                    ".");
            return;
        }

        MapFile map = new MapFile();
        map.name = placeName.getText().trim();
        map.tileset = (String) tileset.getSelectedItem();
        try {
            MapEdit made = new MapEdit(map);
            StarterRoom(made);
            file = target;
            edit = made;
            FillPalettes();
            SetBrush(Tiles.NOTHING);
            Changed();
        } catch (DataError e) {
            Complain(e.getMessage());
        }
    }

    /* A small walled room with the start in it, to have something to
       begin from. Not a stroke: there is nothing before it to undo to. */
    private static void StarterRoom(MapEdit made) {
        Tiles.Set set = made.Set();
        char wall = 0, floor = made.FloorBeside(0, 0);
        int w = 12, h = 8;

        for (char c = 0; c < 128 && wall == 0; c++)
            if (set.walls[c] != null) wall = c;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                made.Paint(x, y, edge && wall != 0 ? wall : floor);
            }
        }
        made.Paint(w / 2, h / 2, Tiles.START);
    }

    private void Save() {
        if (edit == null) return;

        String text = edit.File().Write(), trouble = Problem();
        boolean isNew = !file.exists();

        if (trouble != null && JOptionPane.showConfirmDialog(frame,
                "The game will not start with this map as it stands:\n\n" +
                trouble + "\n\nSave it anyway?", "Save",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;

        try {
            MapTool.WriteFile(file, text);
            edit.MarkSaved();
            if (trouble == null) {
                MapFile map = MapFile.Parse(text, file.getName());
                MapPicture.Save(file.getName(), map,
                        World.Build(file.getName(), map, true));
            }
            Changed();
            if (isNew) {
                JOptionPane.showMessageDialog(frame, "Saved as " +
                        file.getPath() + ".\n\nFor the game to use the " +
                        "new map, add \"" + file.getName() + "\" to the " +
                        "\"maps\" list in\nsrc/main/resources/data/" +
                        "game.json, and give it a way in: a point here " +
                        "and a script\nthat sends the player to it.",
                        "Save", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            Complain("The map could not be saved: " + e.getMessage());
        }
    }

    private void Quit() {
        if (!MayDiscard()) return;
        frame.dispose();
        MapPicture.Finish();
        System.exit(0);
    }

    /* Asks about unsaved changes. True if it is all right to lose the
       map as it stands, having saved it first if that was wanted. */
    private boolean MayDiscard() {
        if (edit == null || (!edit.Changed() && file.exists())) return true;

        int answer = JOptionPane.showConfirmDialog(frame,
                "Save the changes to " + file.getName() + "?", TITLE,
                JOptionPane.YES_NO_CANCEL_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            Save();
            return !edit.Changed();
        }
        return answer == JOptionPane.NO_OPTION;
    }

    /* ---- Changes that are not painting ---- */

    private void Rename() {
        String name = JOptionPane.showInputDialog(frame,
                "What is the place called?", edit.Name());
        if (name == null || name.trim().length() == 0) return;
        edit.Begin();
        edit.SetName(name.trim());
        Changed();
    }

    private void Shift(int dx, int dy) {
        if (!ConfirmMove()) return;
        edit.Begin();
        edit.Shift(dx, dy);
        Changed();
    }

    /* Moving every tile matters to saved games, so it is asked about,
       once a map has been saved and so may have been played. */
    private boolean ConfirmMove() {
        if (!file.exists()) return true;
        return JOptionPane.showConfirmDialog(frame, "This moves every " +
                "tile of the map, so a game saved on it will find the " +
                "player and\nany mined-out rocks in the wrong place. " +
                "Go ahead?", TITLE, JOptionPane.OK_CANCEL_OPTION)
                == JOptionPane.OK_OPTION;
    }

    private void AddPerson() {
        java.util.ArrayList ids = new java.util.ArrayList();
        DataNode[] nodes = DataNode.Load("npcs.json").Groups("npcs", "npc");
        for (int i = 0; i < nodes.length; i++) ids.add(nodes[i].Str("id"));

        if (ids.isEmpty()) {
            Complain("npcs.json lists nobody yet.");
            return;
        }
        Object id = JOptionPane.showInputDialog(frame, "Who? They are " +
                "the people in npcs.json.", "Add a person",
                JOptionPane.PLAIN_MESSAGE, null, ids.toArray(), ids.get(0));
        if (id != null) AddDefine(MapFile.KIND_NPC, (String) id,
                MapFile.NO_FLOOR);
    }

    private void AddPoint() {
        String name = JOptionPane.showInputDialog(frame, "A name for the " +
                "point, as scripts will write it:\nlower-case letters, " +
                "digits, and underscores.", "from_somewhere");
        if (name == null) return;
        if (!name.matches("[a-z][a-z0-9_]*")) {
            Complain("A point's name is lower-case letters, digits, and " +
                    "underscores, beginning with a letter.");
            return;
        }
        MapFile.Define[] defines = edit.Defines();
        for (int i = 0; i < defines.length; i++) {
            if (defines[i].kind.equals(MapFile.KIND_POINT) &&
                    defines[i].value.equals(name)) {
                Complain("This map already has a point called " + name +
                        ".");
                return;
            }
        }
        AddDefine(MapFile.KIND_POINT, name, MapFile.NO_FLOOR);
    }

    /* For when the floor the game works out under a thing is not the
       one wanted: the same thing, on a floor that is said. */
    private void AddCopy() {
        if (thingModel.isEmpty()) return;

        Object[] things = thingModel.toArray();
        String[] thingNames = new String[things.length];
        for (int i = 0; i < things.length; i++)
            thingNames[i] = ((Entry) things[i]).label;

        java.util.ArrayList floors = new java.util.ArrayList();
        for (char c = 0; c < 128; c++)
            if (edit.Set().floors[c] != null) floors.add(String.valueOf(c));

        JComboBox thing = new JComboBox(thingNames);
        JComboBox floor = new JComboBox(floors.toArray());
        JPanel panel = new JPanel(new GridLayout(5, 1));
        panel.add(new JLabel("The game works out the floor under a thing " +
                "from the tiles beside it."));
        panel.add(new JLabel("Use this where it gets that wrong. " +
                "Which thing?"));
        panel.add(thing);
        panel.add(new JLabel("Standing on which floor?"));
        panel.add(floor);

        if (JOptionPane.showConfirmDialog(frame, panel,
                "Add a thing on a chosen floor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) return;

        Entry chosen = (Entry) things[thing.getSelectedIndex()];
        AddDefine(MapFile.KIND_LIKE, String.valueOf(chosen.symbol),
                ((String) floor.getSelectedItem()).charAt(0));
    }

    private void AddDefine(String kind, String value, char on) {
        edit.Begin();
        char symbol = edit.AddDefine(kind, value, on);
        if (symbol == 0) {
            edit.Undo();
            Complain("Every symbol is taken: this map has as many kinds " +
                    "of thing as a map file can hold.");
            return;
        }
        Changed();
        SetBrush(symbol);
    }

    private void RemoveOwn() {
        Entry entry = (Entry) ownList.getSelectedValue();
        if (entry == null || edit.Defined(entry.symbol) == null) return;

        int count = edit.Count(entry.symbol);
        if (count > 0 && JOptionPane.showConfirmDialog(frame,
                entry.label + " is on the map " + count + (count == 1 ?
                " time" : " times") + ". Remove it from the map too?",
                TITLE, JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) return;

        edit.Begin();
        edit.RemoveDefine(entry.symbol);
        if (brush == entry.symbol) SetBrush(Tiles.NOTHING);
        Changed();
    }

    /* ---- Keeping the window in step with the map ---- */

    /* Call after anything changes the map. */
    private void Changed() {
        try {
            picture = MapPicture.Draw(World.Build(file.getName(),
                    edit.File(), false));
        } catch (DataError e) {
            /* MapEdit only makes maps that can be shown, so this is a
               fault in the editor; say so and keep the old picture. */
            Complain("The map could not be drawn: " + e.getMessage());
        }

        FillOwn();
        undoAction.setEnabled(edit.CanUndo());
        redoAction.setEnabled(edit.CanRedo());
        frame.setTitle((edit.Changed() || !file.exists() ? "* " : "") +
                file.getName() + " - " + edit.Name() + " - " + TITLE);

        String trouble = Problem();
        problem.setText(trouble == null ? " " : trouble);
        canvas.Resize();
        canvas.repaint();
    }

    /* What stops the game using the map as it stands, or null. */
    private String Problem() {
        try {
            World.Build(file.getName(), edit.File(), true);
            return null;
        } catch (DataError e) {
            return e.getMessage();
        }
    }

    private void FillPalettes() {
        Tiles.Set set = edit.Set();

        groundModel.clear();
        thingModel.clear();
        groundModel.addElement(new Entry(Tiles.NOTHING,
                "Nothing (solid, undrawn)", Icon(World.NO_PICTURE, 0)));
        for (char c = 0; c < 128; c++) {
            if (set.floors[c] == null) continue;
            groundModel.addElement(new Entry(c, "Floor  " + c,
                    Icon(set.floors[c].pictures[0], 0)));
        }
        for (char c = 0; c < 128; c++) {
            if (set.walls[c] == null) continue;
            groundModel.addElement(new Entry(c, set.walls[c].name + "  " +
                    c, Icon(World.WallPicture(set.walls[c], 0), 0)));
        }
        for (char c = 0; c < 128; c++) {
            Tiles.Thing thing = set.things[c];
            if (thing == null) continue;
            thingModel.addElement(new Entry(c, thing.name + "  " + c,
                    Icon(thing.pictures[0], thing.colorMap)));
        }
        FillOwn();
    }

    private void FillOwn() {
        MapFile.Define[] defines = edit.Defines();
        Tiles.Set set = edit.Set();

        selecting = true;
        ownModel.clear();
        ownModel.addElement(new Entry(Tiles.START,
                "Player start  @", Icon(World.NO_PICTURE, 0)));
        for (int i = 0; i < defines.length; i++) {
            MapFile.Define d = defines[i];
            String label;
            ImageIcon icon = Icon(World.NO_PICTURE, 0);

            if (d.kind.equals(MapFile.KIND_NPC)) {
                Npc npc = Npc.Named(d.value);
                label = (npc == null ? d.value : npc.name) + " (person)";
                if (npc != null) icon = Icon(npc.picture, 0);
            } else if (d.kind.equals(MapFile.KIND_POINT)) {
                label = "Point: " + d.value;
            } else {
                Tiles.Thing thing = set.things[d.value.charAt(0)];
                label = thing.name;
                icon = Icon(thing.pictures[0], thing.colorMap);
            }
            if (d.on != MapFile.NO_FLOOR) label += " on floor " + d.on;
            ownModel.addElement(new Entry(d.symbol, label + "  " + d.symbol,
                    icon));
        }
        SelectInLists();
        selecting = false;
    }

    private static ImageIcon Icon(int picture, int colorMap) {
        BufferedImage small = MapPicture.Icon(picture, colorMap);
        BufferedImage big = new BufferedImage(TILE * ICON_SCALE,
                TILE * ICON_SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.setColor(EMPTY);
        g.fillRect(0, 0, big.getWidth(), big.getHeight());
        g.drawImage(small, 0, 0, big.getWidth(), big.getHeight(), null);
        g.dispose();
        return new ImageIcon(big);
    }

    private void SetBrush(char symbol) {
        brush = symbol;
        selecting = true;
        SelectInLists();
        selecting = false;

        Entry entry = EntryFor(symbol);
        brushLabel.setText(entry == null ? "" : entry.label);
        brushLabel.setIcon(entry == null ? null : entry.icon);
    }

    /* Shows the brush as the selection of whichever list has it. */
    private void SelectInLists() {
        JList[] lists = { groundList, thingList, ownList };

        for (int l = 0; l < lists.length; l++) {
            DefaultListModel model = (DefaultListModel) lists[l].getModel();
            lists[l].clearSelection();
            for (int i = 0; i < model.size(); i++) {
                if (((Entry) model.get(i)).symbol != brush) continue;
                lists[l].setSelectedIndex(i);
                lists[l].ensureIndexIsVisible(i);
            }
        }
    }

    private Entry EntryFor(char symbol) {
        DefaultListModel[] models = { ownModel, thingModel, groundModel };

        for (int m = 0; m < models.length; m++) {
            for (int i = 0; i < models[m].size(); i++) {
                Entry entry = (Entry) models[m].get(i);
                if (entry.symbol == symbol) return entry;
            }
        }
        return null;
    }

    private void Zoom(int by) {
        zoom = Math.max(MIN_ZOOM, Math.min(zoom + by, MAX_ZOOM));
        canvas.Resize();
        canvas.repaint();
    }

    private void Complain(String message) {
        JOptionPane.showMessageDialog(frame, message, TITLE,
                JOptionPane.ERROR_MESSAGE);
    }

    /* ---- The picture of the map, and the mouse on it ---- */

    private final class Canvas extends JComponent {
        private static final long serialVersionUID = 1L;

        private int hoverX = -1, hoverY = -1;
        /* Where a rectangle drag began, or -1. */
        private int dragX = -1, dragY = -1;
        private boolean painting;

        Canvas() {
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { Pressed(e); }
                public void mouseReleased(MouseEvent e) { Released(e); }
                public void mouseExited(MouseEvent e) {
                    hoverX = hoverY = -1;
                    Describe();
                    repaint();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) { Moved(e); }
                public void mouseDragged(MouseEvent e) { Moved(e); }
            });
        }

        void Resize() {
            if (edit == null) return;
            setPreferredSize(new Dimension(
                    (edit.Width() + SPARE) * TILE * zoom,
                    (edit.Height() + SPARE) * TILE * zoom));
            revalidate();
        }

        private void Pressed(MouseEvent e) {
            if (edit == null) return;
            int x = e.getX() / (TILE * zoom), y = e.getY() / (TILE * zoom);

            if (SwingUtilities.isRightMouseButton(e)) {
                SetBrush(edit.At(x, y));
            } else if (SwingUtilities.isLeftMouseButton(e)) {
                edit.Begin();
                painting = true;
                if (tool == TOOL_RECT) {
                    dragX = x;
                    dragY = y;
                } else if (edit.Paint(x, y, brush)) {
                    Changed();
                }
            }
        }

        private void Moved(MouseEvent e) {
            if (edit == null) return;
            int x = e.getX() / (TILE * zoom), y = e.getY() / (TILE * zoom);
            if (x == hoverX && y == hoverY) return;

            hoverX = x;
            hoverY = y;
            if (painting && tool == TOOL_PENCIL && edit.Paint(x, y, brush))
                Changed();
            Describe();
            repaint();
        }

        private void Released(MouseEvent e) {
            if (!painting) return;
            painting = false;

            if (tool == TOOL_RECT && dragX >= 0) {
                edit.Fill(dragX, dragY, Math.max(hoverX, 0),
                        Math.max(hoverY, 0), brush);
                dragX = dragY = -1;
                Changed();
            }
            edit.End();
            Changed();
        }

        /* Writes what is under the pointer on the status line. */
        private void Describe() {
            if (edit == null || hoverX < 0) {
                status.setText(" ");
                return;
            }
            char c = edit.At(hoverX, hoverY);
            Entry entry = EntryFor(c);
            status.setText("Column " + hoverX + ", row " + hoverY + ":  " +
                    (entry == null ? "[" + c + "]" : entry.label) +
                    "      Map " + edit.Width() + " by " + edit.Height());
        }

        protected void paintComponent(Graphics g) {
            Rectangle clip = g.getClipBounds();
            int step = TILE * zoom;

            g.setColor(EMPTY);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            if (edit == null || picture == null) return;

            g.drawImage(picture, 0, 0, picture.getWidth() * zoom,
                    picture.getHeight() * zoom, null);

            if (showGrid) {
                g.setColor(GRID);
                for (int x = 0; x <= getWidth(); x += step)
                    g.drawLine(x, 0, x, getHeight());
                for (int y = 0; y <= getHeight(); y += step)
                    g.drawLine(0, y, getWidth(), y);
            }
            g.setColor(EDGE);
            g.drawRect(0, 0, edit.Width() * step, edit.Height() * step);

            if (showSymbols) {
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD,
                        Math.max(10, 4 * zoom + 2)));
                for (int y = 0; y < edit.Height(); y++) {
                    for (int x = 0; x < edit.Width(); x++) {
                        char c = edit.At(x, y);
                        if (c != Tiles.START && edit.Defined(c) == null)
                            continue;
                        g.setColor(Color.BLACK);
                        g.drawString(String.valueOf(c), x * step + 3,
                                y * step + 4 * zoom + 3);
                        g.setColor(LABEL);
                        g.drawString(String.valueOf(c), x * step + 2,
                                y * step + 4 * zoom + 2);
                    }
                }
            }

            if (hoverX < 0) return;
            g.setColor(HOVER);
            if (dragX >= 0) {
                int x0 = Math.min(dragX, hoverX), y0 = Math.min(dragY, hoverY);
                int x1 = Math.max(dragX, hoverX), y1 = Math.max(dragY, hoverY);
                g.drawRect(x0 * step, y0 * step, (x1 - x0 + 1) * step - 1,
                        (y1 - y0 + 1) * step - 1);
            } else {
                g.drawRect(hoverX * step, hoverY * step, step - 1, step - 1);
            }
        }
    }
}
