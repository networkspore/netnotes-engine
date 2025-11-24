package io.netnotes.engine.io.input;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

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
    }

    public final class CodePointByteRegistry{
        private static final Map<NoteBytesReadOnly, NoteBytesReadOnly> CODEPOINT_TO_CHAR = new HashMap<>();
       
        private static Map<NoteBytesReadOnly, NoteBytesReadOnly> buildRegistry() {

            // Letters: a-z and A-Z
            for (int i = 0; i < 26; i++) {
                char lower = (char) ('a' + i);
                char upper = (char) ('A' + i);

                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) lower), new NoteBytesReadOnly(String.valueOf(lower)));
                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) upper), new NoteBytesReadOnly(String.valueOf(upper)));
            }

            // Number row unshifted
            for (int i = 1; i <= 9; i++) {
                char c = (char) ('0' + i);
                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) c), new NoteBytesReadOnly(String.valueOf(c)));
            }
            CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) '0'), new NoteBytesReadOnly("0"));

            // Number row shifted
            char[] shiftedNums = "!@#$%^&*()".toCharArray();
            for (char c : shiftedNums) {
                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) c), new NoteBytesReadOnly(String.valueOf(c)));
            }

            // Unshifted punctuation
            char[] unshifted = {
                    '-', '=', '[', ']', '\\', ';', '\'', '`', ',', '.', '/', ' ', '\t', '\n'
            };
            for (char c : unshifted) {
                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) c), new NoteBytesReadOnly(String.valueOf(c)));
            }

            // Shifted punctuation
            char[] shifted = {
                    '_', '+', '{', '}', '|', ':', '"', '~', '<', '>', '?'
            };
            for (char c : shifted) {
                CODEPOINT_TO_CHAR.put(new NoteBytesReadOnly((int) c), new NoteBytesReadOnly(String.valueOf(c)));
            }

            return CODEPOINT_TO_CHAR;
        }
        
        public static NoteBytesReadOnly get(NoteBytes key){
            if(CODEPOINT_TO_CHAR.size() == 0){
                buildRegistry();
            }
            return CODEPOINT_TO_CHAR.get(key);
        }
     
    }

    public final class KeyCodeBytes{
        public static final NoteBytesReadOnly NONE  = new NoteBytesReadOnly(0x00);

        public static final NoteBytesReadOnly ERROR_ROLLOVER    = new NoteBytesReadOnly(0x01);
        public static final NoteBytesReadOnly POST_FAIL         = new NoteBytesReadOnly(0x02);
        public static final NoteBytesReadOnly ERROR_UNDEFINED   = new NoteBytesReadOnly(0x03);

        // Letters
        public static final NoteBytesReadOnly A  = new NoteBytesReadOnly(0x04);
        public static final NoteBytesReadOnly B  = new NoteBytesReadOnly(0x05);
        public static final NoteBytesReadOnly C  = new NoteBytesReadOnly(0x06);
        public static final NoteBytesReadOnly D  = new NoteBytesReadOnly(0x07);
        public static final NoteBytesReadOnly E  = new NoteBytesReadOnly(0x08);
        public static final NoteBytesReadOnly F  = new NoteBytesReadOnly(0x09);
        public static final NoteBytesReadOnly G  = new NoteBytesReadOnly(0x0A);
        public static final NoteBytesReadOnly H  = new NoteBytesReadOnly(0x0B);
        public static final NoteBytesReadOnly I  = new NoteBytesReadOnly(0x0C);
        public static final NoteBytesReadOnly J  = new NoteBytesReadOnly(0x0D);
        public static final NoteBytesReadOnly K  = new NoteBytesReadOnly(0x0E);
        public static final NoteBytesReadOnly L  = new NoteBytesReadOnly(0x0F);
        public static final NoteBytesReadOnly M  = new NoteBytesReadOnly(0x10);
        public static final NoteBytesReadOnly N  = new NoteBytesReadOnly(0x11);
        public static final NoteBytesReadOnly O  = new NoteBytesReadOnly(0x12);
        public static final NoteBytesReadOnly P  = new NoteBytesReadOnly(0x13);
        public static final NoteBytesReadOnly Q  = new NoteBytesReadOnly(0x14);
        public static final NoteBytesReadOnly R  = new NoteBytesReadOnly(0x15);
        public static final NoteBytesReadOnly S  = new NoteBytesReadOnly(0x16);
        public static final NoteBytesReadOnly T  = new NoteBytesReadOnly(0x17);
        public static final NoteBytesReadOnly U  = new NoteBytesReadOnly(0x18);
        public static final NoteBytesReadOnly V  = new NoteBytesReadOnly(0x19);
        public static final NoteBytesReadOnly W  = new NoteBytesReadOnly(0x1A);
        public static final NoteBytesReadOnly X  = new NoteBytesReadOnly(0x1B);
        public static final NoteBytesReadOnly Y  = new NoteBytesReadOnly(0x1C);
        public static final NoteBytesReadOnly Z  = new NoteBytesReadOnly(0x1D);

        // Number row
        public static final NoteBytesReadOnly DIGIT_1  = new NoteBytesReadOnly(0x1E);
        public static final NoteBytesReadOnly DIGIT_2  = new NoteBytesReadOnly(0x1F);
        public static final NoteBytesReadOnly DIGIT_3  = new NoteBytesReadOnly(0x20);
        public static final NoteBytesReadOnly DIGIT_4  = new NoteBytesReadOnly(0x21);
        public static final NoteBytesReadOnly DIGIT_5  = new NoteBytesReadOnly(0x22);
        public static final NoteBytesReadOnly DIGIT_6  = new NoteBytesReadOnly(0x23);
        public static final NoteBytesReadOnly DIGIT_7  = new NoteBytesReadOnly(0x24);
        public static final NoteBytesReadOnly DIGIT_8  = new NoteBytesReadOnly(0x25);
        public static final NoteBytesReadOnly DIGIT_9  = new NoteBytesReadOnly(0x26);
        public static final NoteBytesReadOnly DIGIT_0  = new NoteBytesReadOnly(0x27);

        // Basics
        public static final NoteBytesReadOnly ENTER  = new NoteBytesReadOnly(0x28);
        public static final NoteBytesReadOnly ESCAPE  = new NoteBytesReadOnly(0x29);
        public static final NoteBytesReadOnly BACKSPACE  = new NoteBytesReadOnly(0x2A);
        public static final NoteBytesReadOnly TAB  = new NoteBytesReadOnly(0x2B);
        public static final NoteBytesReadOnly SPACE  = new NoteBytesReadOnly(0x2C);

        // Symbols
        public static final NoteBytesReadOnly MINUS  = new NoteBytesReadOnly(0x2D);
        public static final NoteBytesReadOnly EQUALS  = new NoteBytesReadOnly(0x2E);
        public static final NoteBytesReadOnly LEFT_BRACKET  = new NoteBytesReadOnly(0x2F);
        public static final NoteBytesReadOnly RIGHT_BRACKET  = new NoteBytesReadOnly(0x30);
        public static final NoteBytesReadOnly BACKSLASH  = new NoteBytesReadOnly(0x31);
        public static final NoteBytesReadOnly NON_US_HASH  = new NoteBytesReadOnly(0x32);
        public static final NoteBytesReadOnly SEMICOLON  = new NoteBytesReadOnly(0x33);
        public static final NoteBytesReadOnly APOSTROPHE  = new NoteBytesReadOnly(0x34);
        public static final NoteBytesReadOnly GRAVE  = new NoteBytesReadOnly(0x35);
        public static final NoteBytesReadOnly COMMA  = new NoteBytesReadOnly(0x36);
        public static final NoteBytesReadOnly PERIOD  = new NoteBytesReadOnly(0x37);
        public static final NoteBytesReadOnly SLASH  = new NoteBytesReadOnly(0x38);

        // Locks & function keys
        public static final NoteBytesReadOnly CAPS_LOCK  = new NoteBytesReadOnly(0x39);
        public static final NoteBytesReadOnly F1  = new NoteBytesReadOnly(0x3A);
        public static final NoteBytesReadOnly F2  = new NoteBytesReadOnly(0x3B);
        public static final NoteBytesReadOnly F3  = new NoteBytesReadOnly(0x3C);
        public static final NoteBytesReadOnly F4  = new NoteBytesReadOnly(0x3D);
        public static final NoteBytesReadOnly F5  = new NoteBytesReadOnly(0x3E);
        public static final NoteBytesReadOnly F6  = new NoteBytesReadOnly(0x3F);
        public static final NoteBytesReadOnly F7  = new NoteBytesReadOnly(0x40);
        public static final NoteBytesReadOnly F8  = new NoteBytesReadOnly(0x41);
        public static final NoteBytesReadOnly F9  = new NoteBytesReadOnly(0x42);
        public static final NoteBytesReadOnly F10  = new NoteBytesReadOnly(0x43);
        public static final NoteBytesReadOnly F11  = new NoteBytesReadOnly(0x44);
        public static final NoteBytesReadOnly F12  = new NoteBytesReadOnly(0x45);

        public static final NoteBytesReadOnly PRINT_SCREEN  = new NoteBytesReadOnly(0x46);
        public static final NoteBytesReadOnly SCROLL_LOCK   = new NoteBytesReadOnly(0x47);
        public static final NoteBytesReadOnly PAUSE  = new NoteBytesReadOnly(0x48);

        // Navigation block
        public static final NoteBytesReadOnly INSERT  = new NoteBytesReadOnly(0x49);
        public static final NoteBytesReadOnly HOME  = new NoteBytesReadOnly(0x4A);
        public static final NoteBytesReadOnly PAGE_UP  = new NoteBytesReadOnly(0x4B);
        public static final NoteBytesReadOnly DELETE  = new NoteBytesReadOnly(0x4C);
        public static final NoteBytesReadOnly END  = new NoteBytesReadOnly(0x4D);
        public static final NoteBytesReadOnly PAGE_DOWN  = new NoteBytesReadOnly(0x4E);

        // Arrows
        public static final NoteBytesReadOnly RIGHT  = new NoteBytesReadOnly(0x4F);
        public static final NoteBytesReadOnly LEFT   = new NoteBytesReadOnly(0x50);
        public static final NoteBytesReadOnly DOWN   = new NoteBytesReadOnly(0x51);
        public static final NoteBytesReadOnly UP     = new NoteBytesReadOnly(0x52);

        // Keypad
        public static final NoteBytesReadOnly NUM_LOCK  = new NoteBytesReadOnly(0x53);
        public static final NoteBytesReadOnly KP_SLASH  = new NoteBytesReadOnly(0x54);
        public static final NoteBytesReadOnly KP_ASTERISK  = new NoteBytesReadOnly(0x55);
        public static final NoteBytesReadOnly KP_MINUS  = new NoteBytesReadOnly(0x56);
        public static final NoteBytesReadOnly KP_PLUS  = new NoteBytesReadOnly(0x57);
        public static final NoteBytesReadOnly KP_ENTER  = new NoteBytesReadOnly(0x58);
        public static final NoteBytesReadOnly KP_1  = new NoteBytesReadOnly(0x59);
        public static final NoteBytesReadOnly KP_2  = new NoteBytesReadOnly(0x5A);
        public static final NoteBytesReadOnly KP_3  = new NoteBytesReadOnly(0x5B);
        public static final NoteBytesReadOnly KP_4  = new NoteBytesReadOnly(0x5C);
        public static final NoteBytesReadOnly KP_5  = new NoteBytesReadOnly(0x5D);
        public static final NoteBytesReadOnly KP_6  = new NoteBytesReadOnly(0x5E);
        public static final NoteBytesReadOnly KP_7  = new NoteBytesReadOnly(0x5F);
        public static final NoteBytesReadOnly KP_8  = new NoteBytesReadOnly(0x60);
        public static final NoteBytesReadOnly KP_9  = new NoteBytesReadOnly(0x61);
        public static final NoteBytesReadOnly KP_0  = new NoteBytesReadOnly(0x62);
        public static final NoteBytesReadOnly KP_PERIOD  = new NoteBytesReadOnly(0x63);

        // Extended keys
        public static final NoteBytesReadOnly NON_US_BACKSLASH  = new NoteBytesReadOnly(0x64);
        public static final NoteBytesReadOnly APPLICATION  = new NoteBytesReadOnly(0x65);
        public static final NoteBytesReadOnly POWER  = new NoteBytesReadOnly(0x66);
        public static final NoteBytesReadOnly KP_EQUALS  = new NoteBytesReadOnly(0x67);

        // F13–F24
        public static final NoteBytesReadOnly F13  = new NoteBytesReadOnly(0x68);
        public static final NoteBytesReadOnly F14  = new NoteBytesReadOnly(0x69);
        public static final NoteBytesReadOnly F15  = new NoteBytesReadOnly(0x6A);
        public static final NoteBytesReadOnly F16  = new NoteBytesReadOnly(0x6B);
        public static final NoteBytesReadOnly F17  = new NoteBytesReadOnly(0x6C);
        public static final NoteBytesReadOnly F18  = new NoteBytesReadOnly(0x6D);
        public static final NoteBytesReadOnly F19  = new NoteBytesReadOnly(0x6E);
        public static final NoteBytesReadOnly F20  = new NoteBytesReadOnly(0x6F);
        public static final NoteBytesReadOnly F21  = new NoteBytesReadOnly(0x70);
        public static final NoteBytesReadOnly F22  = new NoteBytesReadOnly(0x71);
        public static final NoteBytesReadOnly F23  = new NoteBytesReadOnly(0x72);
        public static final NoteBytesReadOnly F24  = new NoteBytesReadOnly(0x73);

        // Media keys
        public static final NoteBytesReadOnly EXECUTE  = new NoteBytesReadOnly(0x74);
        public static final NoteBytesReadOnly HELP  = new NoteBytesReadOnly(0x75);
        public static final NoteBytesReadOnly MENU  = new NoteBytesReadOnly(0x76);
        public static final NoteBytesReadOnly SELECT  = new NoteBytesReadOnly(0x77);
        public static final NoteBytesReadOnly STOP  = new NoteBytesReadOnly(0x78);
        public static final NoteBytesReadOnly AGAIN  = new NoteBytesReadOnly(0x79);
        public static final NoteBytesReadOnly UNDO  = new NoteBytesReadOnly(0x7A);
        public static final NoteBytesReadOnly CUT  = new NoteBytesReadOnly(0x7B);
        public static final NoteBytesReadOnly COPY  = new NoteBytesReadOnly(0x7C);
        public static final NoteBytesReadOnly PASTE  = new NoteBytesReadOnly(0x7D);
        public static final NoteBytesReadOnly FIND  = new NoteBytesReadOnly(0x7E);
        public static final NoteBytesReadOnly MUTE  = new NoteBytesReadOnly(0x7F);
        public static final NoteBytesReadOnly VOLUME_UP  = new NoteBytesReadOnly(0x80);
        public static final NoteBytesReadOnly VOLUME_DOWN  = new NoteBytesReadOnly(0x81);

        // Modifiers (left/right)
        public static final NoteBytesReadOnly LEFT_CONTROL   = new NoteBytesReadOnly(0xE0);
        public static final NoteBytesReadOnly LEFT_SHIFT     = new NoteBytesReadOnly(0xE1);
        public static final NoteBytesReadOnly LEFT_ALT       = new NoteBytesReadOnly(0xE2);
        public static final NoteBytesReadOnly LEFT_META      = new NoteBytesReadOnly(0xE3);
        public static final NoteBytesReadOnly RIGHT_CONTROL  = new NoteBytesReadOnly(0xE4);
        public static final NoteBytesReadOnly RIGHT_SHIFT    = new NoteBytesReadOnly(0xE5);
        public static final NoteBytesReadOnly RIGHT_ALT      = new NoteBytesReadOnly(0xE6);
        public static final NoteBytesReadOnly RIGHT_META     = new NoteBytesReadOnly(0xE7);
    }

} 
