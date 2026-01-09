package io.netnotes.engine.core.system.control.terminal;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

public class TerminalCommands {
    public static final String PRESS_ANY_KEY = "Press any key to continue...";

    public static final NoteBytesReadOnly START_ROW = 
        new NoteBytesReadOnly("start_row");
    public static final NoteBytesReadOnly START_COL = 
        new NoteBytesReadOnly("start_col");
    public static final NoteBytesReadOnly END_ROW = 
        new NoteBytesReadOnly("end_row");
    public static final NoteBytesReadOnly END_COL = 
        new NoteBytesReadOnly("end_col");
    public static final NoteBytesReadOnly BOX_STYLE = 
        new NoteBytesReadOnly("box_style");



    public static final NoteBytesReadOnly TERMINAL_CLEAR = 
        new NoteBytesReadOnly("terminal_clear");
    public static final NoteBytesReadOnly TERMINAL_PRINT = 
        new NoteBytesReadOnly("terminal_print");
    public static final NoteBytesReadOnly TERMINAL_PRINTLN = 
        new NoteBytesReadOnly("terminal_println");
    public static final NoteBytesReadOnly TERMINAL_PRINT_AT = 
        new NoteBytesReadOnly("terminal_print_at");    
    public static final NoteBytesReadOnly TERMINAL_MOVE_CURSOR = 
        new NoteBytesReadOnly("terminal_move_cursor");
    public static final NoteBytesReadOnly TERMINAL_SHOW_CURSOR = 
        new NoteBytesReadOnly("terminal_show_cursor");
    public static final NoteBytesReadOnly TERMINAL_HIDE_CURSOR = 
        new NoteBytesReadOnly("terminal_hide_cursor");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE = 
        new NoteBytesReadOnly("terminal_clear_line");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE_AT = 
        new NoteBytesReadOnly("terminal_clear_line_at");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_REGION = 
        new NoteBytesReadOnly("terminal_clear_region");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BOX = 
        new NoteBytesReadOnly("terminal_draw_box");
    public static final NoteBytesReadOnly TERMINAL_DRAW_HLINE = 
        new NoteBytesReadOnly("terminal_draw_hline");

    
    public static final NoteBytesReadOnly TERMINAL_RESIZE = 
            new NoteBytesReadOnly("terminal_resize");


    public static NoteBytesMap clear() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR);
        return command;
    }


    

    public static NoteBytesMap print(
        String text,
        TextStyle style
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        return command;
    }

    public static NoteBytesMap println(
        String text,
        TextStyle style
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINTLN);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        return command;
    }

    public static NoteBytesMap printAt(
            int row,
            int col,
            String text,
            TextStyle style
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT_AT);
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        return command;
    }

    public static NoteBytesMap printAtCenterRowSpan(int rowEnd, int col, String text) {
        int rowStart = 0;
        int centerRowSpan = (rowEnd - rowStart) / 2;
        int row = Math.max(0, rowStart + centerRowSpan);

        return printAt(row, col, text, TextStyle.NORMAL);
    }

    public static NoteBytesMap printAtCenterRowSpan(int rowStart, int rowEnd, int col, String text) {
        
        int centerRowSpan = (rowEnd - rowStart) / 2;
        int row = Math.max(0, rowStart + centerRowSpan);

        return printAt(row, col, text, TextStyle.NORMAL);
    }

    public static NoteBytesMap printAtCenterColSpan(int row, int colEnd, String text) {
        int colStart = 0;
        int halfText = text.length() / 2;
        int centerSpan = (colEnd - colStart) / 2;
        int col = Math.max(0, (colStart + centerSpan) - halfText);

        return printAt(row, col, text, TextStyle.NORMAL);
    }

    public static NoteBytesMap printAtCenterColSpan(int row, int colStart,  int colEnd, String text) {
        
        int halfText = text.length() / 2;
        int centerSpan = (colEnd - colStart) / 2;
        int col = Math.max(0, (colStart + centerSpan) - halfText);

        return printAt(row, col, text, TextStyle.NORMAL);
    }

    public static NoteBytesMap printAtCenterSpan(int rowStart, int rowEnd, int colStart,  int colEnd, String text) {
       
        int centerRowSpan = (rowEnd - rowStart) / 2;
        int row = Math.max(0, rowStart + centerRowSpan);
        
        int halfText = text.length() / 2;
        int centerSpan = (colEnd - colStart) / 2;
        int col = Math.max(0, (colStart + centerSpan) - halfText);

        return printAt(row, col, text, TextStyle.NORMAL);
    }

    public static NoteBytesMap moveCursor(
        int row,
        int col
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_MOVE_CURSOR);
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        return command;
    }

    public static NoteBytesMap showCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        return command;
    }

    public static NoteBytesMap hideCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        return command;
    }


    public static NoteBytesMap clearLine() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        return command;
    }

    public static NoteBytesMap clearLineAt(
            int row
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE_AT);
        command.put(Keys.ROW, row);
        return command;
    }

    public static NoteBytesMap clearRegion(
            int startRow,
            int startCol,
            int endRow,
            int endCol
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_REGION);
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(TerminalCommands.END_ROW, endRow);
        command.put(TerminalCommands.END_COL, endCol);
        return command;
    }

    public static NoteBytesMap drawBox(
        int startRow,
        int startCol,
        int width,
        int height,
        String title,
        BoxStyle boxStyle
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_BOX);
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.WIDTH, width);
        command.put(Keys.HEIGHT, height);
        command.put(Keys.TITLE, title != null ? title : "");
        command.put(TerminalCommands.BOX_STYLE, boxStyle.name());
        return command;
    }

    public static NoteBytesMap drawHLine(
        int row,
        int startCol,
        int length
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_HLINE);
        command.put(Keys.ROW, row);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.LENGTH, length);
        return command;
    }


}
