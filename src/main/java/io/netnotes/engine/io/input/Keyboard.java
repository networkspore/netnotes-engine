package io.netnotes.engine.io.input;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public final class Keyboard {

    private Keyboard() {}

    public final class Mod {
                // Modifier keys (used in modifier byte)
        public static final byte MOD_LEFT_CTRL   = 0x01;
        public static final byte MOD_LEFT_SHIFT  = 0x02;
        public static final byte MOD_LEFT_ALT    = 0x04;
        public static final byte MOD_LEFT_GUI    = 0x08;  // Windows/Super key
        public static final byte MOD_RIGHT_CTRL  = 0x10;
        public static final byte MOD_RIGHT_SHIFT = 0x20;
        public static final byte MOD_RIGHT_ALT   = 0x40;
        public static final byte MOD_RIGHT_GUI   = (byte) 0x80;
    }

    public final class KeyCode {

        private KeyCode() {}

        // ============================================================
        // FULL USB HID KEYBOARD USAGE TABLE (all ints)
        // ============================================================

        public static final int NONE = 0x00;

        public static final int ERROR_ROLLOVER   = 0x01;
        public static final int POST_FAIL        = 0x02;
        public static final int ERROR_UNDEFINED  = 0x03;

        // Letters
        public static final int A = 0x04;
        public static final int B = 0x05;
        public static final int C = 0x06;
        public static final int D = 0x07;
        public static final int E = 0x08;
        public static final int F = 0x09;
        public static final int G = 0x0A;
        public static final int H = 0x0B;
        public static final int I = 0x0C;
        public static final int J = 0x0D;
        public static final int K = 0x0E;
        public static final int L = 0x0F;
        public static final int M = 0x10;
        public static final int N = 0x11;
        public static final int O = 0x12;
        public static final int P = 0x13;
        public static final int Q = 0x14;
        public static final int R = 0x15;
        public static final int S = 0x16;
        public static final int T = 0x17;
        public static final int U = 0x18;
        public static final int V = 0x19;
        public static final int W = 0x1A;
        public static final int X = 0x1B;
        public static final int Y = 0x1C;
        public static final int Z = 0x1D;

        // Number row
        public static final int DIGIT_1 = 0x1E;
        public static final int DIGIT_2 = 0x1F;
        public static final int DIGIT_3 = 0x20;
        public static final int DIGIT_4 = 0x21;
        public static final int DIGIT_5 = 0x22;
        public static final int DIGIT_6 = 0x23;
        public static final int DIGIT_7 = 0x24;
        public static final int DIGIT_8 = 0x25;
        public static final int DIGIT_9 = 0x26;
        public static final int DIGIT_0 = 0x27;

        // Basics
        public static final int ENTER = 0x28;
        public static final int ESCAPE = 0x29;
        public static final int BACKSPACE = 0x2A;
        public static final int TAB = 0x2B;
        public static final int SPACE = 0x2C;

        // Symbols
        public static final int MINUS = 0x2D;
        public static final int EQUALS = 0x2E;
        public static final int LEFT_BRACKET = 0x2F;
        public static final int RIGHT_BRACKET = 0x30;
        public static final int BACKSLASH = 0x31;
        public static final int NON_US_HASH = 0x32;
        public static final int SEMICOLON = 0x33;
        public static final int APOSTROPHE = 0x34;
        public static final int GRAVE = 0x35;
        public static final int COMMA = 0x36;
        public static final int PERIOD = 0x37;
        public static final int SLASH = 0x38;

        // Locks & function keys
        public static final int CAPS_LOCK = 0x39;
        public static final int F1 = 0x3A;
        public static final int F2 = 0x3B;
        public static final int F3 = 0x3C;
        public static final int F4 = 0x3D;
        public static final int F5 = 0x3E;
        public static final int F6 = 0x3F;
        public static final int F7 = 0x40;
        public static final int F8 = 0x41;
        public static final int F9 = 0x42;
        public static final int F10 = 0x43;
        public static final int F11 = 0x44;
        public static final int F12 = 0x45;

        public static final int PRINT_SCREEN = 0x46;
        public static final int SCROLL_LOCK  = 0x47;
        public static final int PAUSE = 0x48;

        // Navigation block
        public static final int INSERT = 0x49;
        public static final int HOME = 0x4A;
        public static final int PAGE_UP = 0x4B;
        public static final int DELETE = 0x4C;
        public static final int END = 0x4D;
        public static final int PAGE_DOWN = 0x4E;

        // Arrows
        public static final int RIGHT = 0x4F;
        public static final int LEFT  = 0x50;
        public static final int DOWN  = 0x51;
        public static final int UP    = 0x52;

        // Keypad
        public static final int NUM_LOCK = 0x53;
        public static final int KP_SLASH = 0x54;
        public static final int KP_ASTERISK = 0x55;
        public static final int KP_MINUS = 0x56;
        public static final int KP_PLUS = 0x57;
        public static final int KP_ENTER = 0x58;
        public static final int KP_1 = 0x59;
        public static final int KP_2 = 0x5A;
        public static final int KP_3 = 0x5B;
        public static final int KP_4 = 0x5C;
        public static final int KP_5 = 0x5D;
        public static final int KP_6 = 0x5E;
        public static final int KP_7 = 0x5F;
        public static final int KP_8 = 0x60;
        public static final int KP_9 = 0x61;
        public static final int KP_0 = 0x62;
        public static final int KP_PERIOD = 0x63;

        // Extended keys
        public static final int NON_US_BACKSLASH = 0x64;
        public static final int APPLICATION = 0x65;
        public static final int POWER = 0x66;
        public static final int KP_EQUALS = 0x67;

        // F13–F24
        public static final int F13 = 0x68;
        public static final int F14 = 0x69;
        public static final int F15 = 0x6A;
        public static final int F16 = 0x6B;
        public static final int F17 = 0x6C;
        public static final int F18 = 0x6D;
        public static final int F19 = 0x6E;
        public static final int F20 = 0x6F;
        public static final int F21 = 0x70;
        public static final int F22 = 0x71;
        public static final int F23 = 0x72;
        public static final int F24 = 0x73;

        // Media keys
        public static final int EXECUTE = 0x74;
        public static final int HELP = 0x75;
        public static final int MENU = 0x76;
        public static final int SELECT = 0x77;
        public static final int STOP = 0x78;
        public static final int AGAIN = 0x79;
        public static final int UNDO = 0x7A;
        public static final int CUT = 0x7B;
        public static final int COPY = 0x7C;
        public static final int PASTE = 0x7D;
        public static final int FIND = 0x7E;
        public static final int MUTE = 0x7F;
        public static final int VOLUME_UP = 0x80;
        public static final int VOLUME_DOWN = 0x81;

        // Modifiers (left/right)
        public static final int LEFT_CONTROL  = 0xE0;
        public static final int LEFT_SHIFT    = 0xE1;
        public static final int LEFT_ALT      = 0xE2;
        public static final int LEFT_META     = 0xE3;
        public static final int RIGHT_CONTROL = 0xE4;
        public static final int RIGHT_SHIFT   = 0xE5;
        public static final int RIGHT_ALT     = 0xE6;
        public static final int RIGHT_META    = 0xE7;



        // ============================================================
        // GROUPING SETS (fast O(1) lookup)
        // ============================================================

        public static final Set<Integer> MODIFIER_KEYS = Set.of(
                LEFT_CONTROL, LEFT_SHIFT, LEFT_ALT, LEFT_META,
                RIGHT_CONTROL, RIGHT_SHIFT, RIGHT_ALT, RIGHT_META
        );

        public static final Set<Integer> NAVIGATION_KEYS = Set.of(
                UP, DOWN, LEFT, RIGHT,
                HOME, END, PAGE_UP, PAGE_DOWN,
                INSERT, DELETE
        );

        public static final Set<Integer> SYSTEM_KEYS = Set.of(
                F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
                F13, F14, F15, F16, F17, F18, F19, F20, F21, F22, F23, F24,
                PRINT_SCREEN, SCROLL_LOCK, PAUSE
        );

        public static final Set<Integer> KEYPAD_KEYS = Set.of(
                NUM_LOCK, KP_SLASH, KP_ASTERISK, KP_MINUS, KP_PLUS, KP_ENTER,
                KP_0, KP_1, KP_2, KP_3, KP_4, KP_5, KP_6, KP_7, KP_8, KP_9,
                KP_PERIOD, KP_EQUALS
        );

        public static final Set<Integer> MEDIA_KEYS = Set.of(
                EXECUTE, HELP, MENU, SELECT, STOP, AGAIN, UNDO,
                CUT, COPY, PASTE, FIND,
                MUTE, VOLUME_UP, VOLUME_DOWN
        );

        public static final Set<Integer> TEXT_INPUT_KEYS = Set.of(
                A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,
                DIGIT_0,DIGIT_1,DIGIT_2,DIGIT_3,DIGIT_4,
                DIGIT_5,DIGIT_6,DIGIT_7,DIGIT_8,DIGIT_9,
                SPACE, TAB, ENTER, BACKSPACE
        );


        // ============================================================
        // REVERSE LOOKUP MAP
        // ============================================================

        public static final Map<Integer, String> NAME_BY_CODE;
        public static final Map<String, Integer> CODE_BY_NAME;

        static {
            Map<Integer, String> nameMap = new HashMap<>();
            Map<String, Integer> codeMap = new HashMap<>();

            for (var field : KeyCode.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    try {
                        int code = field.getInt(null);
                        String name = field.getName();
                        nameMap.put(code, name);
                        codeMap.put(name, code);
                    } catch (Exception ignored) {}
                }
            }

            NAME_BY_CODE = Collections.unmodifiableMap(nameMap);
            CODE_BY_NAME = Collections.unmodifiableMap(codeMap);
        }


        // ============================================================
        // CLEAN HELPER METHODS
        // ============================================================

        public static boolean isModifierKey(int keyCode) {
            return MODIFIER_KEYS.contains(keyCode);
        }

        public static boolean isNavigationKey(int keyCode) {
            return NAVIGATION_KEYS.contains(keyCode);
        }

        public static boolean isSystemKey(int keyCode) {
            return SYSTEM_KEYS.contains(keyCode);
        }

        public static boolean isMediaKey(int keyCode) {
            return MEDIA_KEYS.contains(keyCode);
        }

        public static boolean isKeypadKey(int keyCode) {
            return KEYPAD_KEYS.contains(keyCode);
        }

        public static boolean isTextInputKey(int keyCode) {
            return TEXT_INPUT_KEYS.contains(keyCode);
        }

        public static String nameOf(int keyCode) {
            return NAME_BY_CODE.getOrDefault(keyCode, "UNKNOWN(" + keyCode + ")");
        }


        // ============================================================
        // CONTROL CHARACTER MAPPINGS (ASCII 1-26 to HID)
        // ============================================================

        /**
         * Map ASCII control character (1-26) to HID keycode
         * These represent Ctrl+A through Ctrl+Z
         * 
         * Usage: 
         *   int ascii = 3; // Ctrl+C
         *   int hidCode = CONTROL_CHAR_TO_HID[ascii]; // Returns KeyCode.C
         */
        public static final int[] CONTROL_CHAR_TO_HID = new int[27];

        static {
            // Initialize control character mapping
            // ASCII 1-26 maps to Ctrl+A through Ctrl+Z
            for (int i = 1; i <= 26; i++) {
                CONTROL_CHAR_TO_HID[i] = KeyCode.A + (i - 1);
            }
        }

        /**
         * Check if an ASCII code is a control character (1-26)
         * These are generated by Ctrl+A through Ctrl+Z
         */
        public static boolean isControlChar(int ascii) {
            return ascii >= 1 && ascii <= 26;
        }

        /**
         * Convert ASCII control character to HID keycode
         * Returns KeyCode.NONE if not a control character
         * 
         * @param ascii ASCII control code (1-26)
         * @return HID keycode (A-Z) or NONE
         */
        public static int controlCharToHid(int ascii) {
            if (isControlChar(ascii)) {
                return CONTROL_CHAR_TO_HID[ascii];
            }
            return KeyCode.NONE;
        }

        /**
         * Get the letter representation of a control character
         * E.g., Ctrl+C (ASCII 3) returns 'C'
         */
        public static char controlCharToLetter(int ascii) {
            if (isControlChar(ascii)) {
                return (char)('A' + ascii - 1);
            }
            return '\0';
        }
    }


    private static final NoteBytes[] CP_TO_CHAR_TABLE = new NoteBytes[127];
    private static final int[] codepoint = new int[1];
  
    static {
        
        // Letters: a-z and A-Z
        for (int i = 0; i < 26; i++) {
            char lower = (char) ('a' + i);
            char upper = (char) ('A' + i);

            CP_TO_CHAR_TABLE[(int) lower] = new NoteBytes(String.valueOf(lower));
            CP_TO_CHAR_TABLE[(int) upper] = new NoteBytes(String.valueOf(upper));
        }

        // Number row unshifted
        for (int i = 1; i <= 9; i++) {
            char c = (char) ('0' + i);
            CP_TO_CHAR_TABLE[(int) c] = new NoteBytes(String.valueOf(c));
        }
        CP_TO_CHAR_TABLE[(int) '0'] = new NoteBytes("0");


        // Number row shifted
        char[] shiftedNums = "!@#$%^&*()".toCharArray();
        for (char c : shiftedNums) {
            CP_TO_CHAR_TABLE[(int) c] = new NoteBytes(String.valueOf(c));
        }

        // Unshifted punctuation
        char[] unshifted = {
                '-', '=', '[', ']', '\\', ';', '\'', '`', ',', '.', '/', ' ', '\t', '\n'
        };
        for (char c : unshifted) {
            CP_TO_CHAR_TABLE[(int) c] = new NoteBytes(String.valueOf(c));
        }

        // Shifted punctuation
        char[] shifted = {
                '_', '+', '{', '}', '|', ':', '"', '~', '<', '>', '?'
        };
        for (char c : shifted) {
            CP_TO_CHAR_TABLE[(int) c] = new NoteBytes(String.valueOf(c));
        }
    }

    private static final byte ZERO = (byte) 0;

    public static NoteBytes codePointToASCII(NoteBytes codePointBytes){
        byte[] cpbytes = codePointBytes.get();
        
        if(cpbytes[0] == ZERO && cpbytes[1] == ZERO && cpbytes[2] == ZERO){
            codepoint[0] = cpbytes[3] & 0xFF;
            if(codepoint[0] < CP_TO_CHAR_TABLE.length){
                NoteBytes value = CP_TO_CHAR_TABLE[codepoint[0]];
                codepoint[0] = ZERO;
                return value;
            }else{
                return null;
            }
        }else{
            return null;
        }
    }

    public static NoteBytes codePointToUtf8(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("Invalid Unicode code point");
        }

        if (codePoint <= 0x7F) {
            return new NoteBytes(new byte[] {
                (byte) codePoint
            }, NoteBytesMetaData.STRING_TYPE);
        } else if (codePoint <= 0x7FF) {
            return new NoteBytes(new byte[] {
                (byte) (0b11000000 | (codePoint >> 6)),
                (byte) (0b10000000 | (codePoint & 0b00111111))
            }, NoteBytesMetaData.STRING_TYPE);
        } else if (codePoint <= 0xFFFF) {
            return new NoteBytes(new byte[] {
                (byte) (0b11100000 | (codePoint >> 12)),
                (byte) (0b10000000 | ((codePoint >> 6) & 0b00111111)),
                (byte) (0b10000000 | (codePoint & 0b00111111))
            }, NoteBytesMetaData.STRING_TYPE);
        } else {
            return new NoteBytes(new byte[] {
                (byte) (0b11110000 | (codePoint >> 18)),
                (byte) (0b10000000 | ((codePoint >> 12) & 0b00111111)),
                (byte) (0b10000000 | ((codePoint >> 6) & 0b00111111)),
                (byte) (0b10000000 | (codePoint & 0b00111111))
            }, NoteBytesMetaData.STRING_TYPE);
        }
    }


    public final class KeyCodeBytes{
        public static final NoteBytes NONE  = new NoteBytes(0x00);

        public static final NoteBytes ERROR_ROLLOVER    = new NoteBytes(KeyCode.ERROR_ROLLOVER);
        public static final NoteBytes POST_FAIL         = new NoteBytes(KeyCode.POST_FAIL);
        public static final NoteBytes ERROR_UNDEFINED   = new NoteBytes(KeyCode.ERROR_UNDEFINED);

        // Letters
        public static final NoteBytes A  = new NoteBytes(KeyCode.A);
        public static final NoteBytes B  = new NoteBytes(KeyCode.B);
        public static final NoteBytes C  = new NoteBytes(KeyCode.C);
        public static final NoteBytes D  = new NoteBytes(KeyCode.D);
        public static final NoteBytes E  = new NoteBytes(KeyCode.E);
        public static final NoteBytes F  = new NoteBytes(KeyCode.F);
        public static final NoteBytes G  = new NoteBytes(KeyCode.G);
        public static final NoteBytes H  = new NoteBytes(KeyCode.H);
        public static final NoteBytes I  = new NoteBytes(KeyCode.I);
        public static final NoteBytes J  = new NoteBytes(KeyCode.J);
        public static final NoteBytes K  = new NoteBytes(KeyCode.K);
        public static final NoteBytes L  = new NoteBytes(KeyCode.L);
        public static final NoteBytes M  = new NoteBytes(KeyCode.M);
        public static final NoteBytes N  = new NoteBytes(KeyCode.N);
        public static final NoteBytes O  = new NoteBytes(KeyCode.O);
        public static final NoteBytes P  = new NoteBytes(KeyCode.P);
        public static final NoteBytes Q  = new NoteBytes(KeyCode.Q);
        public static final NoteBytes R  = new NoteBytes(KeyCode.R);
        public static final NoteBytes S  = new NoteBytes(KeyCode.S);
        public static final NoteBytes T  = new NoteBytes(KeyCode.T);
        public static final NoteBytes U  = new NoteBytes(KeyCode.U);
        public static final NoteBytes V  = new NoteBytes(KeyCode.V);
        public static final NoteBytes W  = new NoteBytes(KeyCode.W);
        public static final NoteBytes X  = new NoteBytes(KeyCode.X);
        public static final NoteBytes Y  = new NoteBytes(KeyCode.Y);
        public static final NoteBytes Z  = new NoteBytes(KeyCode.Z);

        // Number row
        public static final NoteBytes DIGIT_1  = new NoteBytes(KeyCode.DIGIT_1);
        public static final NoteBytes DIGIT_2  = new NoteBytes(KeyCode.DIGIT_2);
        public static final NoteBytes DIGIT_3  = new NoteBytes(KeyCode.DIGIT_3);
        public static final NoteBytes DIGIT_4  = new NoteBytes(KeyCode.DIGIT_4);
        public static final NoteBytes DIGIT_5  = new NoteBytes(KeyCode.DIGIT_5);
        public static final NoteBytes DIGIT_6  = new NoteBytes(KeyCode.DIGIT_6);
        public static final NoteBytes DIGIT_7  = new NoteBytes(KeyCode.DIGIT_7);
        public static final NoteBytes DIGIT_8  = new NoteBytes(KeyCode.DIGIT_8);
        public static final NoteBytes DIGIT_9  = new NoteBytes(KeyCode.DIGIT_9);
        public static final NoteBytes DIGIT_0  = new NoteBytes(KeyCode.DIGIT_0);

        // Basics
        public static final NoteBytes ENTER  = new NoteBytes(KeyCode.ENTER);
        public static final NoteBytes ESCAPE  = new NoteBytes(KeyCode.ESCAPE);
        public static final NoteBytes BACKSPACE  = new NoteBytes(KeyCode.BACKSPACE);
        public static final NoteBytes TAB  = new NoteBytes(KeyCode.TAB);
        public static final NoteBytes SPACE  = new NoteBytes(KeyCode.SPACE);

        // Symbols
        public static final NoteBytes MINUS  = new NoteBytes(KeyCode.MINUS);
        public static final NoteBytes EQUALS  = new NoteBytes(KeyCode.EQUALS);
        public static final NoteBytes LEFT_BRACKET  = new NoteBytes(KeyCode.LEFT_BRACKET);
        public static final NoteBytes RIGHT_BRACKET  = new NoteBytes(KeyCode.RIGHT_BRACKET);
        public static final NoteBytes BACKSLASH  = new NoteBytes(KeyCode.BACKSLASH);
        public static final NoteBytes NON_US_HASH  = new NoteBytes(KeyCode.NON_US_HASH);
        public static final NoteBytes SEMICOLON  = new NoteBytes(KeyCode.SEMICOLON);
        public static final NoteBytes APOSTROPHE  = new NoteBytes(KeyCode.APOSTROPHE);
        public static final NoteBytes GRAVE  = new NoteBytes(KeyCode.GRAVE);
        public static final NoteBytes COMMA  = new NoteBytes(KeyCode.COMMA);
        public static final NoteBytes PERIOD  = new NoteBytes(KeyCode.PERIOD);
        public static final NoteBytes SLASH  = new NoteBytes(KeyCode.SLASH);

        // Locks & function keys
        public static final NoteBytes CAPS_LOCK  = new NoteBytes(KeyCode.CAPS_LOCK);
        public static final NoteBytes F1  = new NoteBytes(KeyCode.F1);
        public static final NoteBytes F2  = new NoteBytes(KeyCode.F2);
        public static final NoteBytes F3  = new NoteBytes(KeyCode.F3);
        public static final NoteBytes F4  = new NoteBytes(KeyCode.F4);
        public static final NoteBytes F5  = new NoteBytes(KeyCode.F5);
        public static final NoteBytes F6  = new NoteBytes(KeyCode.F6);
        public static final NoteBytes F7  = new NoteBytes(KeyCode.F7);
        public static final NoteBytes F8  = new NoteBytes(KeyCode.F8);
        public static final NoteBytes F9  = new NoteBytes(KeyCode.F9);
        public static final NoteBytes F10  = new NoteBytes(KeyCode.F10);
        public static final NoteBytes F11  = new NoteBytes(KeyCode.F11);
        public static final NoteBytes F12  = new NoteBytes(KeyCode.F12);

        public static final NoteBytes PRINT_SCREEN  = new NoteBytes(KeyCode.PRINT_SCREEN);
        public static final NoteBytes SCROLL_LOCK   = new NoteBytes(KeyCode.SCROLL_LOCK);
        public static final NoteBytes PAUSE  = new NoteBytes(KeyCode.PAUSE);

        // Navigation block
        public static final NoteBytes INSERT  = new NoteBytes(KeyCode.INSERT);
        public static final NoteBytes HOME  = new NoteBytes(KeyCode.HOME);
        public static final NoteBytes PAGE_UP  = new NoteBytes(KeyCode.PAGE_UP);
        public static final NoteBytes DELETE  = new NoteBytes(KeyCode.DELETE);
        public static final NoteBytes END  = new NoteBytes(KeyCode.END);
        public static final NoteBytes PAGE_DOWN  = new NoteBytes(KeyCode.PAGE_DOWN);

        // Arrows
        public static final NoteBytes RIGHT  = new NoteBytes(KeyCode.RIGHT);
        public static final NoteBytes LEFT   = new NoteBytes(KeyCode.LEFT);
        public static final NoteBytes DOWN   = new NoteBytes(KeyCode.DOWN);
        public static final NoteBytes UP     = new NoteBytes(KeyCode.UP);

        // Keypad
        public static final NoteBytes NUM_LOCK  = new NoteBytes(KeyCode.NUM_LOCK);
        public static final NoteBytes KP_SLASH  = new NoteBytes(KeyCode.KP_SLASH);
        public static final NoteBytes KP_ASTERISK  = new NoteBytes(KeyCode.KP_ASTERISK);
        public static final NoteBytes KP_MINUS  = new NoteBytes(KeyCode.KP_MINUS);
        public static final NoteBytes KP_PLUS  = new NoteBytes(KeyCode.KP_PLUS);
        public static final NoteBytes KP_ENTER  = new NoteBytes(KeyCode.KP_ENTER);
        public static final NoteBytes KP_1  = new NoteBytes(KeyCode.KP_1);
        public static final NoteBytes KP_2  = new NoteBytes(KeyCode.KP_2);
        public static final NoteBytes KP_3  = new NoteBytes(KeyCode.KP_3);
        public static final NoteBytes KP_4  = new NoteBytes(KeyCode.KP_4);
        public static final NoteBytes KP_5  = new NoteBytes(KeyCode.KP_5);
        public static final NoteBytes KP_6  = new NoteBytes(KeyCode.KP_6);
        public static final NoteBytes KP_7  = new NoteBytes(KeyCode.KP_7);
        public static final NoteBytes KP_8  = new NoteBytes(KeyCode.KP_8);
        public static final NoteBytes KP_9  = new NoteBytes(KeyCode.KP_9);
        public static final NoteBytes KP_0  = new NoteBytes(KeyCode.KP_0);
        public static final NoteBytes KP_PERIOD  = new NoteBytes(KeyCode.KP_PERIOD);

        // Extended keys
        public static final NoteBytes NON_US_BACKSLASH  = new NoteBytes(KeyCode.NON_US_BACKSLASH);
        public static final NoteBytes APPLICATION  = new NoteBytes(KeyCode.APPLICATION);
        public static final NoteBytes POWER  = new NoteBytes(KeyCode.POWER);
        public static final NoteBytes KP_EQUALS  = new NoteBytes(KeyCode.KP_EQUALS);

        // F13–F24
        public static final NoteBytes F13  = new NoteBytes(KeyCode.F13);
        public static final NoteBytes F14  = new NoteBytes(KeyCode.F14);
        public static final NoteBytes F15  = new NoteBytes(KeyCode.F15);
        public static final NoteBytes F16  = new NoteBytes(KeyCode.F16);
        public static final NoteBytes F17  = new NoteBytes(KeyCode.F17);
        public static final NoteBytes F18  = new NoteBytes(KeyCode.F18);
        public static final NoteBytes F19  = new NoteBytes(KeyCode.F19);
        public static final NoteBytes F20  = new NoteBytes(KeyCode.F20);
        public static final NoteBytes F21  = new NoteBytes(KeyCode.F21);
        public static final NoteBytes F22  = new NoteBytes(KeyCode.F22);
        public static final NoteBytes F23  = new NoteBytes(KeyCode.F23);
        public static final NoteBytes F24  = new NoteBytes(KeyCode.F24);

        // Media keys
        public static final NoteBytes EXECUTE  = new NoteBytes(KeyCode.EXECUTE);
        public static final NoteBytes HELP  = new NoteBytes(KeyCode.HELP);
        public static final NoteBytes MENU  = new NoteBytes(KeyCode.MENU);
        public static final NoteBytes SELECT  = new NoteBytes(KeyCode.SELECT);
        public static final NoteBytes STOP  = new NoteBytes(KeyCode.STOP);
        public static final NoteBytes AGAIN  = new NoteBytes(KeyCode.AGAIN);
        public static final NoteBytes UNDO  = new NoteBytes(KeyCode.UNDO);
        public static final NoteBytes CUT  = new NoteBytes(KeyCode.CUT);
        public static final NoteBytes COPY  = new NoteBytes(KeyCode.COPY);
        public static final NoteBytes PASTE  = new NoteBytes(KeyCode.PASTE);
        public static final NoteBytes FIND  = new NoteBytes(KeyCode.FIND);
        public static final NoteBytes MUTE  = new NoteBytes(KeyCode.MUTE);
        public static final NoteBytes VOLUME_UP  = new NoteBytes(KeyCode.VOLUME_UP);
        public static final NoteBytes VOLUME_DOWN  = new NoteBytes(KeyCode.VOLUME_DOWN);

        // Modifiers (left/right)
        public static final NoteBytes LEFT_CONTROL   = new NoteBytes(KeyCode.LEFT_CONTROL);
        public static final NoteBytes LEFT_SHIFT     = new NoteBytes(KeyCode.LEFT_SHIFT);
        public static final NoteBytes LEFT_ALT       = new NoteBytes(KeyCode.LEFT_ALT);
        public static final NoteBytes LEFT_META      = new NoteBytes(KeyCode.LEFT_META);
        public static final NoteBytes RIGHT_CONTROL  = new NoteBytes(KeyCode.RIGHT_CONTROL);
        public static final NoteBytes RIGHT_SHIFT    = new NoteBytes(KeyCode.RIGHT_SHIFT);
        public static final NoteBytes RIGHT_ALT      = new NoteBytes(KeyCode.RIGHT_ALT);
        public static final NoteBytes RIGHT_META     = new NoteBytes(KeyCode.RIGHT_META);

        private final static int[] usage = new int[1];
        private static final char[] shiftedNums = "!@#$%^&*".toCharArray();

        public static NoteBytes hidUsageToChar(NoteBytes keycode, boolean shift) {
            usage[0] = keycode.get()[3] & 0xFF;  // HID usage byte
            codepoint[0] = 0;

            // Letters A-Z
            if (usage[0] >= KeyCode.A && usage[0] <= KeyCode.Z) {
                codepoint[0] = shift ? ('A' + usage[0] - KeyCode.A) : ('a' + usage[0] - KeyCode.A);
            }
            // Numbers 1-9
            else if (usage[0] >= KeyCode.DIGIT_1 && usage[0] <= KeyCode.DIGIT_9) {
                codepoint[0] = shift ?  shiftedNums[usage[0] -KeyCode.DIGIT_1] : '1' + (usage[0] - KeyCode.DIGIT_1);
            }
            // Number 0
            else if (usage[0] == KeyCode.DIGIT_0) {
                codepoint[0] = shift ? ')' : '0';
            }
            if (usage[0] >= KeyCode.KP_1 && usage[0] <= KeyCode.KP_9) {
                codepoint[0] = '1' + (usage[0] - KeyCode.KP_1);
            } else if (usage[0] == KeyCode.KP_0) {
                codepoint[0] = '0';
            }
            // Keypad operators
            else if (usage[0] == KeyCode.KP_SLASH) {
                codepoint[0] = '/';
            } else if (usage[0] == KeyCode.KP_ASTERISK) {
                codepoint[0] = '*';
            } else if (usage[0] == KeyCode.KP_MINUS) {
                codepoint[0] = '-';
            } else if (usage[0] == KeyCode.KP_PLUS) {
                codepoint[0] = '+';
            } else if (usage[0] == KeyCode.KP_PERIOD) {
                codepoint[0] = '.';
            } else if(usage[0] == KeyCode.KP_ENTER) {
                codepoint[0] = '\n';
            }
            // Punctuation
            else {
                switch (usage[0]) {
                    case KeyCode.MINUS: codepoint[0] = shift ? '_' : '-'; break;
                    case KeyCode.EQUALS: codepoint[0] = shift ? '+' : '='; break;
                    case KeyCode.LEFT_BRACKET: codepoint[0] = shift ? '{' : '['; break;
                    case KeyCode.RIGHT_BRACKET: codepoint[0] = shift ? '}' : ']'; break;
                    case KeyCode.BACKSLASH: codepoint[0] = shift ? '|' : '\\'; break;
                    case KeyCode.NON_US_HASH: codepoint[0] = shift ? '|' : '#'; break;
                    case KeyCode.SEMICOLON: codepoint[0] = shift ? ':' : ';'; break;
                    case KeyCode.APOSTROPHE: codepoint[0] = shift ? '"' : '\''; break;
                    case KeyCode.GRAVE: codepoint[0] = shift ? '~' : '`'; break;
                    case KeyCode.COMMA: codepoint[0] = shift ? '<' : ','; break;
                    case KeyCode.PERIOD: codepoint[0] = shift ? '>' : '.'; break;
                    case KeyCode.SLASH: codepoint[0] = shift ? '?' : '/'; break;
                    case KeyCode.ENTER: codepoint[0] = '\n'; break;
                    case KeyCode.TAB: codepoint[0] = '\t'; break;
                    case KeyCode.SPACE: codepoint[0] = ' '; break;
                    default: codepoint[0] = 0; // unknown
                }
            }
           
            if (codepoint[0] == 0) return null;

            // Lookup using your index[0] table
            NoteBytes charBytes = CP_TO_CHAR_TABLE[codepoint[0]];
            usage[0] = 0;
            codepoint[0] = 0;
            return charBytes;
        }

        public static NoteBytes getNumeric(int i, boolean numpad){
            if(!numpad){
                switch(i){
                    case 1:
                        return KeyCodeBytes.DIGIT_1;
                    case 2:
                        return KeyCodeBytes.DIGIT_2;
                    case 3:
                        return KeyCodeBytes.DIGIT_3;
                    case 4:
                        return KeyCodeBytes.DIGIT_4;
                    case 5:
                        return KeyCodeBytes.DIGIT_5;
                    case 6:
                        return KeyCodeBytes.DIGIT_6;
                    case 7:
                        return KeyCodeBytes.DIGIT_7;
                    case 8:
                        return KeyCodeBytes.DIGIT_8;
                    case 9:
                        return KeyCodeBytes.DIGIT_9;
                    default:
                        return KeyCodeBytes.DIGIT_0;
                }
            }else{
                switch(i){
                    case 1:
                        return KeyCodeBytes.KP_1;
                    case 2:
                        return KeyCodeBytes.KP_2;
                    case 3:
                        return KeyCodeBytes.KP_3;
                    case 4:
                        return KeyCodeBytes.KP_4;
                    case 5:
                        return KeyCodeBytes.KP_5;
                    case 6:
                        return KeyCodeBytes.KP_6;
                    case 7:
                        return KeyCodeBytes.KP_7;
                    case 8:
                        return KeyCodeBytes.KP_8;
                    case 9:
                        return KeyCodeBytes.KP_9;
                    default:
                        return KeyCodeBytes.KP_0;
                }
            }
        }
    }

} 
