package io.netnotes.engine.io.input;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;

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
        public static final NoteBytesReadOnly NONE  = new NoteBytesReadOnly(0x00);

        public static final NoteBytesReadOnly ERROR_ROLLOVER    = new NoteBytesReadOnly(KeyCode.ERROR_ROLLOVER);
        public static final NoteBytesReadOnly POST_FAIL         = new NoteBytesReadOnly(KeyCode.POST_FAIL);
        public static final NoteBytesReadOnly ERROR_UNDEFINED   = new NoteBytesReadOnly(KeyCode.ERROR_UNDEFINED);

        // Letters
        public static final NoteBytesReadOnly A  = new NoteBytesReadOnly(KeyCode.A);
        public static final NoteBytesReadOnly B  = new NoteBytesReadOnly(KeyCode.B);
        public static final NoteBytesReadOnly C  = new NoteBytesReadOnly(KeyCode.C);
        public static final NoteBytesReadOnly D  = new NoteBytesReadOnly(KeyCode.D);
        public static final NoteBytesReadOnly E  = new NoteBytesReadOnly(KeyCode.E);
        public static final NoteBytesReadOnly F  = new NoteBytesReadOnly(KeyCode.F);
        public static final NoteBytesReadOnly G  = new NoteBytesReadOnly(KeyCode.G);
        public static final NoteBytesReadOnly H  = new NoteBytesReadOnly(KeyCode.H);
        public static final NoteBytesReadOnly I  = new NoteBytesReadOnly(KeyCode.I);
        public static final NoteBytesReadOnly J  = new NoteBytesReadOnly(KeyCode.J);
        public static final NoteBytesReadOnly K  = new NoteBytesReadOnly(KeyCode.K);
        public static final NoteBytesReadOnly L  = new NoteBytesReadOnly(KeyCode.L);
        public static final NoteBytesReadOnly M  = new NoteBytesReadOnly(KeyCode.M);
        public static final NoteBytesReadOnly N  = new NoteBytesReadOnly(KeyCode.N);
        public static final NoteBytesReadOnly O  = new NoteBytesReadOnly(KeyCode.O);
        public static final NoteBytesReadOnly P  = new NoteBytesReadOnly(KeyCode.P);
        public static final NoteBytesReadOnly Q  = new NoteBytesReadOnly(KeyCode.Q);
        public static final NoteBytesReadOnly R  = new NoteBytesReadOnly(KeyCode.R);
        public static final NoteBytesReadOnly S  = new NoteBytesReadOnly(KeyCode.S);
        public static final NoteBytesReadOnly T  = new NoteBytesReadOnly(KeyCode.T);
        public static final NoteBytesReadOnly U  = new NoteBytesReadOnly(KeyCode.U);
        public static final NoteBytesReadOnly V  = new NoteBytesReadOnly(KeyCode.V);
        public static final NoteBytesReadOnly W  = new NoteBytesReadOnly(KeyCode.W);
        public static final NoteBytesReadOnly X  = new NoteBytesReadOnly(KeyCode.X);
        public static final NoteBytesReadOnly Y  = new NoteBytesReadOnly(KeyCode.Y);
        public static final NoteBytesReadOnly Z  = new NoteBytesReadOnly(KeyCode.Z);

        // Number row
        public static final NoteBytesReadOnly DIGIT_1  = new NoteBytesReadOnly(KeyCode.DIGIT_1);
        public static final NoteBytesReadOnly DIGIT_2  = new NoteBytesReadOnly(KeyCode.DIGIT_2);
        public static final NoteBytesReadOnly DIGIT_3  = new NoteBytesReadOnly(KeyCode.DIGIT_3);
        public static final NoteBytesReadOnly DIGIT_4  = new NoteBytesReadOnly(KeyCode.DIGIT_4);
        public static final NoteBytesReadOnly DIGIT_5  = new NoteBytesReadOnly(KeyCode.DIGIT_5);
        public static final NoteBytesReadOnly DIGIT_6  = new NoteBytesReadOnly(KeyCode.DIGIT_6);
        public static final NoteBytesReadOnly DIGIT_7  = new NoteBytesReadOnly(KeyCode.DIGIT_7);
        public static final NoteBytesReadOnly DIGIT_8  = new NoteBytesReadOnly(KeyCode.DIGIT_8);
        public static final NoteBytesReadOnly DIGIT_9  = new NoteBytesReadOnly(KeyCode.DIGIT_9);
        public static final NoteBytesReadOnly DIGIT_0  = new NoteBytesReadOnly(KeyCode.DIGIT_0);

        // Basics
        public static final NoteBytesReadOnly ENTER  = new NoteBytesReadOnly(KeyCode.ENTER);
        public static final NoteBytesReadOnly ESCAPE  = new NoteBytesReadOnly(KeyCode.ESCAPE);
        public static final NoteBytesReadOnly BACKSPACE  = new NoteBytesReadOnly(KeyCode.BACKSPACE);
        public static final NoteBytesReadOnly TAB  = new NoteBytesReadOnly(KeyCode.TAB);
        public static final NoteBytesReadOnly SPACE  = new NoteBytesReadOnly(KeyCode.SPACE);

        // Symbols
        public static final NoteBytesReadOnly MINUS  = new NoteBytesReadOnly(KeyCode.MINUS);
        public static final NoteBytesReadOnly EQUALS  = new NoteBytesReadOnly(KeyCode.EQUALS);
        public static final NoteBytesReadOnly LEFT_BRACKET  = new NoteBytesReadOnly(KeyCode.LEFT_BRACKET);
        public static final NoteBytesReadOnly RIGHT_BRACKET  = new NoteBytesReadOnly(KeyCode.RIGHT_BRACKET);
        public static final NoteBytesReadOnly BACKSLASH  = new NoteBytesReadOnly(KeyCode.BACKSLASH);
        public static final NoteBytesReadOnly NON_US_HASH  = new NoteBytesReadOnly(KeyCode.NON_US_HASH);
        public static final NoteBytesReadOnly SEMICOLON  = new NoteBytesReadOnly(KeyCode.SEMICOLON);
        public static final NoteBytesReadOnly APOSTROPHE  = new NoteBytesReadOnly(KeyCode.APOSTROPHE);
        public static final NoteBytesReadOnly GRAVE  = new NoteBytesReadOnly(KeyCode.GRAVE);
        public static final NoteBytesReadOnly COMMA  = new NoteBytesReadOnly(KeyCode.COMMA);
        public static final NoteBytesReadOnly PERIOD  = new NoteBytesReadOnly(KeyCode.PERIOD);
        public static final NoteBytesReadOnly SLASH  = new NoteBytesReadOnly(KeyCode.SLASH);

        // Locks & function keys
        public static final NoteBytesReadOnly CAPS_LOCK  = new NoteBytesReadOnly(KeyCode.CAPS_LOCK);
        public static final NoteBytesReadOnly F1  = new NoteBytesReadOnly(KeyCode.F1);
        public static final NoteBytesReadOnly F2  = new NoteBytesReadOnly(KeyCode.F2);
        public static final NoteBytesReadOnly F3  = new NoteBytesReadOnly(KeyCode.F3);
        public static final NoteBytesReadOnly F4  = new NoteBytesReadOnly(KeyCode.F4);
        public static final NoteBytesReadOnly F5  = new NoteBytesReadOnly(KeyCode.F5);
        public static final NoteBytesReadOnly F6  = new NoteBytesReadOnly(KeyCode.F6);
        public static final NoteBytesReadOnly F7  = new NoteBytesReadOnly(KeyCode.F7);
        public static final NoteBytesReadOnly F8  = new NoteBytesReadOnly(KeyCode.F8);
        public static final NoteBytesReadOnly F9  = new NoteBytesReadOnly(KeyCode.F9);
        public static final NoteBytesReadOnly F10  = new NoteBytesReadOnly(KeyCode.F10);
        public static final NoteBytesReadOnly F11  = new NoteBytesReadOnly(KeyCode.F11);
        public static final NoteBytesReadOnly F12  = new NoteBytesReadOnly(KeyCode.F12);

        public static final NoteBytesReadOnly PRINT_SCREEN  = new NoteBytesReadOnly(KeyCode.PRINT_SCREEN);
        public static final NoteBytesReadOnly SCROLL_LOCK   = new NoteBytesReadOnly(KeyCode.SCROLL_LOCK);
        public static final NoteBytesReadOnly PAUSE  = new NoteBytesReadOnly(KeyCode.PAUSE);

        // Navigation block
        public static final NoteBytesReadOnly INSERT  = new NoteBytesReadOnly(KeyCode.INSERT);
        public static final NoteBytesReadOnly HOME  = new NoteBytesReadOnly(KeyCode.HOME);
        public static final NoteBytesReadOnly PAGE_UP  = new NoteBytesReadOnly(KeyCode.PAGE_UP);
        public static final NoteBytesReadOnly DELETE  = new NoteBytesReadOnly(KeyCode.DELETE);
        public static final NoteBytesReadOnly END  = new NoteBytesReadOnly(KeyCode.END);
        public static final NoteBytesReadOnly PAGE_DOWN  = new NoteBytesReadOnly(KeyCode.PAGE_DOWN);

        // Arrows
        public static final NoteBytesReadOnly RIGHT  = new NoteBytesReadOnly(KeyCode.RIGHT);
        public static final NoteBytesReadOnly LEFT   = new NoteBytesReadOnly(KeyCode.LEFT);
        public static final NoteBytesReadOnly DOWN   = new NoteBytesReadOnly(KeyCode.DOWN);
        public static final NoteBytesReadOnly UP     = new NoteBytesReadOnly(KeyCode.UP);

        // Keypad
        public static final NoteBytesReadOnly NUM_LOCK  = new NoteBytesReadOnly(KeyCode.NUM_LOCK);
        public static final NoteBytesReadOnly KP_SLASH  = new NoteBytesReadOnly(KeyCode.KP_SLASH);
        public static final NoteBytesReadOnly KP_ASTERISK  = new NoteBytesReadOnly(KeyCode.KP_ASTERISK);
        public static final NoteBytesReadOnly KP_MINUS  = new NoteBytesReadOnly(KeyCode.KP_MINUS);
        public static final NoteBytesReadOnly KP_PLUS  = new NoteBytesReadOnly(KeyCode.KP_PLUS);
        public static final NoteBytesReadOnly KP_ENTER  = new NoteBytesReadOnly(KeyCode.KP_ENTER);
        public static final NoteBytesReadOnly KP_1  = new NoteBytesReadOnly(KeyCode.KP_1);
        public static final NoteBytesReadOnly KP_2  = new NoteBytesReadOnly(KeyCode.KP_2);
        public static final NoteBytesReadOnly KP_3  = new NoteBytesReadOnly(KeyCode.KP_3);
        public static final NoteBytesReadOnly KP_4  = new NoteBytesReadOnly(KeyCode.KP_4);
        public static final NoteBytesReadOnly KP_5  = new NoteBytesReadOnly(KeyCode.KP_5);
        public static final NoteBytesReadOnly KP_6  = new NoteBytesReadOnly(KeyCode.KP_6);
        public static final NoteBytesReadOnly KP_7  = new NoteBytesReadOnly(KeyCode.KP_7);
        public static final NoteBytesReadOnly KP_8  = new NoteBytesReadOnly(KeyCode.KP_8);
        public static final NoteBytesReadOnly KP_9  = new NoteBytesReadOnly(KeyCode.KP_9);
        public static final NoteBytesReadOnly KP_0  = new NoteBytesReadOnly(KeyCode.KP_0);
        public static final NoteBytesReadOnly KP_PERIOD  = new NoteBytesReadOnly(KeyCode.KP_PERIOD);

        // Extended keys
        public static final NoteBytesReadOnly NON_US_BACKSLASH  = new NoteBytesReadOnly(KeyCode.NON_US_BACKSLASH);
        public static final NoteBytesReadOnly APPLICATION  = new NoteBytesReadOnly(KeyCode.APPLICATION);
        public static final NoteBytesReadOnly POWER  = new NoteBytesReadOnly(KeyCode.POWER);
        public static final NoteBytesReadOnly KP_EQUALS  = new NoteBytesReadOnly(KeyCode.KP_EQUALS);

        // F13–F24
        public static final NoteBytesReadOnly F13  = new NoteBytesReadOnly(KeyCode.F13);
        public static final NoteBytesReadOnly F14  = new NoteBytesReadOnly(KeyCode.F14);
        public static final NoteBytesReadOnly F15  = new NoteBytesReadOnly(KeyCode.F15);
        public static final NoteBytesReadOnly F16  = new NoteBytesReadOnly(KeyCode.F16);
        public static final NoteBytesReadOnly F17  = new NoteBytesReadOnly(KeyCode.F17);
        public static final NoteBytesReadOnly F18  = new NoteBytesReadOnly(KeyCode.F18);
        public static final NoteBytesReadOnly F19  = new NoteBytesReadOnly(KeyCode.F19);
        public static final NoteBytesReadOnly F20  = new NoteBytesReadOnly(KeyCode.F20);
        public static final NoteBytesReadOnly F21  = new NoteBytesReadOnly(KeyCode.F21);
        public static final NoteBytesReadOnly F22  = new NoteBytesReadOnly(KeyCode.F22);
        public static final NoteBytesReadOnly F23  = new NoteBytesReadOnly(KeyCode.F23);
        public static final NoteBytesReadOnly F24  = new NoteBytesReadOnly(KeyCode.F24);

        // Media keys
        public static final NoteBytesReadOnly EXECUTE  = new NoteBytesReadOnly(KeyCode.EXECUTE);
        public static final NoteBytesReadOnly HELP  = new NoteBytesReadOnly(KeyCode.HELP);
        public static final NoteBytesReadOnly MENU  = new NoteBytesReadOnly(KeyCode.MENU);
        public static final NoteBytesReadOnly SELECT  = new NoteBytesReadOnly(KeyCode.SELECT);
        public static final NoteBytesReadOnly STOP  = new NoteBytesReadOnly(KeyCode.STOP);
        public static final NoteBytesReadOnly AGAIN  = new NoteBytesReadOnly(KeyCode.AGAIN);
        public static final NoteBytesReadOnly UNDO  = new NoteBytesReadOnly(KeyCode.UNDO);
        public static final NoteBytesReadOnly CUT  = new NoteBytesReadOnly(KeyCode.CUT);
        public static final NoteBytesReadOnly COPY  = new NoteBytesReadOnly(KeyCode.COPY);
        public static final NoteBytesReadOnly PASTE  = new NoteBytesReadOnly(KeyCode.PASTE);
        public static final NoteBytesReadOnly FIND  = new NoteBytesReadOnly(KeyCode.FIND);
        public static final NoteBytesReadOnly MUTE  = new NoteBytesReadOnly(KeyCode.MUTE);
        public static final NoteBytesReadOnly VOLUME_UP  = new NoteBytesReadOnly(KeyCode.VOLUME_UP);
        public static final NoteBytesReadOnly VOLUME_DOWN  = new NoteBytesReadOnly(KeyCode.VOLUME_DOWN);

        // Modifiers (left/right)
        public static final NoteBytesReadOnly LEFT_CONTROL   = new NoteBytesReadOnly(KeyCode.LEFT_CONTROL);
        public static final NoteBytesReadOnly LEFT_SHIFT     = new NoteBytesReadOnly(KeyCode.LEFT_SHIFT);
        public static final NoteBytesReadOnly LEFT_ALT       = new NoteBytesReadOnly(KeyCode.LEFT_ALT);
        public static final NoteBytesReadOnly LEFT_META      = new NoteBytesReadOnly(KeyCode.LEFT_META);
        public static final NoteBytesReadOnly RIGHT_CONTROL  = new NoteBytesReadOnly(KeyCode.RIGHT_CONTROL);
        public static final NoteBytesReadOnly RIGHT_SHIFT    = new NoteBytesReadOnly(KeyCode.RIGHT_SHIFT);
        public static final NoteBytesReadOnly RIGHT_ALT      = new NoteBytesReadOnly(KeyCode.RIGHT_ALT);
        public static final NoteBytesReadOnly RIGHT_META     = new NoteBytesReadOnly(KeyCode.RIGHT_META);

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
