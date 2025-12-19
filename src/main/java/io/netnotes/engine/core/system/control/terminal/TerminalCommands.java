package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class TerminalCommands {
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
    public static final NoteBytesReadOnly TERMINAL_BEGIN_BATCH = 
        new NoteBytesReadOnly("terminal_begin_batch");
    public static final NoteBytesReadOnly TERMINAL_END_BATCH = 
        new NoteBytesReadOnly("terminal_end_batch");

}
