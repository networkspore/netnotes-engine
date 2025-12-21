package io.netnotes.engine.core.system.control.terminal;

import java.util.Objects;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

public class TextStyle {
    public enum Color {
        DEFAULT,
        BLACK, RED, GREEN, YELLOW,
        BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }

    /**
     * Box drawing styles
     */
     
    public enum BoxStyle {
        SINGLE(new char[]{'─', '│', '┌', '┐', '└', '┘'}),
        DOUBLE(new char[]{'═', '║', '╔', '╗', '╚', '╝'}),
        ROUNDED(new char[]{'─', '│', '╭', '╮', '╰', '╯'}),
        THICK(new char[]{'━', '┃', '┏', '┓', '┗', '┛'});
        
        private final char[] chars;
        
        BoxStyle(char[] chars) {
            this.chars = chars;
        }
        
        public char[] getChars() {
            return chars;
        }
    }

    public static final TextStyle NORMAL = new TextStyle();
    public static final TextStyle BOLD = new TextStyle().bold();
    public static final TextStyle INVERSE = new TextStyle().inverse();
    public static final TextStyle UNDERLINE = new TextStyle().underline();
    
    // Semantic colors
    public static final TextStyle ERROR = new TextStyle().color(Color.RED).bold();
    public static final TextStyle SUCCESS = new TextStyle().color(Color.GREEN);
    public static final TextStyle WARNING = new TextStyle().color(Color.YELLOW);
    public static final TextStyle INFO = new TextStyle().color(Color.CYAN);
    
    private Color foreground = Color.DEFAULT;
	private Color background = Color.DEFAULT;
    private boolean bold = false;
    private boolean inverse = false;
    private boolean underline = false;
    

	public TextStyle() {}
    
    public TextStyle color(Color fg) {
        this.foreground = fg;
        return this;
    }
    
    public TextStyle bgColor(Color bg) {
        this.background = bg;
        return this;
    }
    
    public TextStyle bold() {
        this.bold = true;
        return this;
    }
    
    public TextStyle inverse() {
        this.inverse = true;
        return this;
    }
    
    public TextStyle underline() {
        this.underline = true;
        return this;
    }


    public void setForeground(Color foreground) {
		this.foreground = foreground;
	}

	public void setBackground(Color background) {
		this.background = background;
	}

	public void setBold(boolean bold) {
		this.bold = bold;
	}

	public void setInverse(boolean inverse) {
		this.inverse = inverse;
	}

	public void setUnderline(boolean underline) {
		this.underline = underline;
	}

    public Color getForeground() {
		return foreground;
	}

	public Color getBackground() {
		return background;
	}

	public boolean isBold() {
		return bold;
	}

	public boolean isInverse() {
		return inverse;
	}

	public boolean isUnderline() {
		return underline;
	}
    
    public NoteBytesMap toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        if(foreground != null){
            map.put(Keys.FOREGROUND, foreground.name());
        }
        if(background != null){
            map.put(Keys.BACKGROUND, background.name());
        }
        map.put(Keys.BOLD, bold);
        map.put(Keys.INVERSE, inverse);
        map.put(Keys.UNDERLINE, underline);
        return map;
    }

    public TextStyle copy(){
        TextStyle textStyle = new TextStyle();
        textStyle.foreground = this.foreground;
        textStyle.background = this.background;
        textStyle.bold = this.bold;
        textStyle.inverse = this.inverse;
        textStyle.underline = this.underline;
        return textStyle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TextStyle)) return false;
        TextStyle other = (TextStyle) obj;
        return foreground == other.foreground &&
            background == other.background &&
            bold == other.bold &&
            inverse == other.inverse &&
            underline == other.underline;
    }

    @Override
    public int hashCode() {
        return Objects.hash(foreground, background, bold, inverse, underline);
    }
}
