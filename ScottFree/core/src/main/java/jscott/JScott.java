package jscott;

/*
 **      JScott 1.00
 **      Scott Adams Classic Adventure driver in Java.
 **      Copyright (C) 1998 Vasyl Tsvirkunov.
 */

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.BitSet;
import java.util.Random;
import java.util.Vector;

/// /////////////////////////////////////////////////////////////////////////
// GameDatabase
// Tokenizer-type class for processing InputStream and getting numbers
// and quoted string tokens from it. Standard StreamTokenizer does not
// work here as it cannot retrieve quoted string containing line breaks,
// more, it is declared deprecated in recent version of SDK. This class
// is also able to write files in exactly the same format, so it is
// consistently used for both save and load operations.

class GameDatabase {
  // Input and output streams
  private InputStream input = null;
  private OutputStream output = null;

  // Token types, as returned by nextToken() -- number, single word, quoted
  // string and end of file.
  public static final int TT_NUMBER = 1;
  public static final int TT_WORD = 2;
  public static final int TT_STRING = 3;
  public static final int TT_EOF = 4;

  // Last token value is either in nval or in sval. Publicly accessible
  // variables -- the way it was done in StreamTokenizer.
  public int nval = 0;
  public String sval = null;

  // Internal variables.
  private int peekChar;
  private boolean separatorNeeded = false;

  /* GameDatabase(InputStream is)
  Constructor for reading the stream. All errors are reported as IOException.
  */
  public GameDatabase(InputStream is) throws IOException {
    input = new BufferedInputStream(is, 1024);
    output = null;
    peekChar = input.read();
  }
  //

  /* GameDatabase(OutputStream os)
  Constructor for writing the stream. Doesn't throw any exceptions here.
  */
  public GameDatabase(OutputStream os) {
    input = null;
    output = os;
    separatorNeeded = false;
  }
  //

  /**
   * Get short value from the input stream. Throws IOException on read error,
   * bad token type or value outside the allowed interval.
   */
  public short getShort() throws IOException {
    int token = nextToken();
    if (token != TT_NUMBER ||
        nval < Short.MIN_VALUE || nval > Short.MAX_VALUE) {
      throw new IOException("Bad database format");
    }
    return (short) nval;
  }
  //

  /**
   * Put short value to write stream.
   */
  public void putShort(short val) throws IOException {
    putInt(val);
  }
  //

  /**
   * Get integer value from the input stream. Throws IOException on read error or
   * bad token type.
   */
  public int getInt() throws IOException {
    if (nextToken() != TT_NUMBER) {
      throw new IOException("Bad database format");
    }
    return nval;
  }
  //

  /**
   * Put int value to write stream.
   */
  public void putInt(int val) throws IOException {
    if (separatorNeeded) {
      putRaw(" ");
    }
    putRaw("" + val);
    separatorNeeded = true;
  }
  //

  /**
   * Get string value from the input stream. Throws IOException on read error or
   * bad token type. String may be either quoted or non-quoted (in the last case
   * it cannot contain spaces.
   */
  public String getString() throws IOException {
    int nToken = nextToken();
    if (nToken != TT_STRING && nToken != TT_WORD) {
      throw new IOException("Bad database format");
    }
    return sval;
  }
  //

  /**
   * Put quoted string to write stream.
   */
  public void putString(String str) throws IOException {
    if (separatorNeeded) {
      putRaw(" ");
    }
    putRaw("\"" + str + "\"");
    separatorNeeded = true;
  }
  //

  /**
   * Put new line to write stream.
   */
  public void putNewLine() throws IOException {
    putRaw("\n");
    separatorNeeded = false;
  }
  //

  /**
   * Put string s into output stream without modifications. Internal use only.
   */
  private void putRaw(String s) throws IOException {
    if (output == null) {
      throw new IOException("Cannot write file");
    }

    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == '\n') {
        output.write('\\');
        output.write('n');
      } else {
        output.write(s.charAt(i));
      }
    }
  }
  //

  /**
   * Get next token from the input stream (exception will be thrown on error).
   * Returns type of token. Token value is either in nval or in sval or it is
   * irrelevant, depending on token type. The only implemented character
   * translation is '\n' as CR, however, backslash can prevent interpreting of the
   * next space or quote symbol.
   * If you expect the next token to be of some specific type, use getInt,
   * getShort or getString instead of this one.
   */
  public int nextToken() throws IOException {
    if (input == null) {
      throw new IOException("Cannot read file");
    }

    while (true) {
      switch (peekChar) {
        case ' ':
        case '\n':
        case '\r': // whitespace
          peekChar = input.read();
          break;
        case -1:
          nval = 0;
          sval = null;
          return TT_EOF;
        case '\"':
          sval = "";
          peekChar = input.read();
          while (peekChar != '\"' && peekChar != -1) {
            sval += (char) peekChar;
            peekChar = input.read();
          }
          if (peekChar == '\"') {
            peekChar = input.read();
          }
          nval = 0;
          return TT_STRING;
        default:
          sval = "";
          while (peekChar != ' ' && peekChar != '\n' &&
              peekChar != '\r' && peekChar != '\"' &&
              peekChar != -1) {
            if (peekChar == '\\') {
              peekChar = input.read();
              if (peekChar == 'n') {
                peekChar = '\n';
              }
            }
            sval += (char) peekChar;
            peekChar = input.read();
          }

          try {
            nval = Integer.parseInt(sval);
            return TT_NUMBER;
          } catch (Exception ignored) {
            nval = 0;
            return TT_WORD;
          }
      }
    }
  }
  //
}
/// /////////////////////////////////////////////////////////////////////////

/// /////////////////////////////////////////////////////////////////////////
// LanguageDatabase
// Kind of language database. LanguageDatabase object uses some text file
// containing pairs of quoted strings (or words without spaces). In each
// pair the first string is considered a key and the second one a value.
// get() is used to translate keys to values.

class LanguageDatabase {
  // Vectors of keys and values. It is easier to place these into separate
  // vectors instead of making some two-string structure and create one vector.
  private Vector key = null;
  private Vector data = null;

  /**
   * Read language database from the input stream. GameDatabase class is used
   * to interpret the input stream.
   */
  public LanguageDatabase(InputStream is) throws IOException {
    if (is != null) {
      GameDatabase gdb = new GameDatabase(is);
      key = new Vector();
      data = new Vector();

      while (true) {
        int tokenType = gdb.nextToken();
        if (tokenType != GameDatabase.TT_WORD &&
            tokenType != GameDatabase.TT_STRING) {
          break;
        }
        key.addElement(gdb.sval);
        data.addElement(gdb.getString());
      }
    }
  }
  //

  /* String get(String str)
  Retrieve value by key. If key does not exist in the file, the key itself
  will be returned as a value.
  */
  public String get(String str) {
    if (key != null) {
      // Warning: Java standard implementation of Vector just
      // scans the entire vector sequentially. It is not the best
      // solution for this specific task in case of hundreds or
      // thousands of keys. For only a few keys -- it should work.
      int index = key.indexOf(str);
      if (index != -1) {
        return (String) data.elementAt(index);
      }
    }
    return str; // no translation
  }
  //
}
/// /////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameHeader
// Game header data and load function.

class GameHeader {
  // 12 fields of the game header
  private short unknownHeaderMagic; // Unknown, probably some magic number
  private short nItems;    // Number of items
  private short nActions;    // Number of actions
  private short nWords;    // Number of verbs and nouns
  // (shorter list is padded)
  private short nRooms;    // Number of rooms
  private short maxCarry;    // Maximum player can carry
  private short playerRoom;  // Starting room
  private short nTreasures;  // Total treasures in game
  private short wordLength;  // Word length
  private short lightTime;  // Time light source lasts (-1 if
  // never runs down)
  private short nMessages;  // Number of messages
  private short treasureRoom;  // Room number to store treasures

  // Access to individual fields -- read only (so-called "inquisitor functions").
  public short getItemCount() {
    return nItems;
  }

  public short getActionCount() {
    return nActions;
  }

  public short getWordCount() {
    return nWords;
  }

  public short getRoomCount() {
    return nRooms;
  }

  public short getMaxCarry() {
    return maxCarry;
  }

  public short getStartingRoom() {
    return playerRoom;
  }

  public short getTreasureCount() {
    return nTreasures;
  }

  public short getWordLength() {
    return wordLength;
  }

  public short getLightTime() {
    return lightTime;
  }

  public short getMessageCount() {
    return nMessages;
  }

  public short getTreasureRoom() {
    return treasureRoom;
  }

  /* static GameHeader fromDatabase(GameDatabase gdb)
  Create and load header object from the game database. It is assumed that
  stream pointer is at the beginning of the header (i.e. at the beginning of
  the file). Implementation is obvious.
  */
  public static GameHeader fromDatabase(GameDatabase gdb)
      throws IOException {
    GameHeader h = new GameHeader();

    h.unknownHeaderMagic = gdb.getShort();
    h.nItems = gdb.getShort();
    h.nActions = gdb.getShort();
    h.nWords = gdb.getShort();
    h.nRooms = gdb.getShort();
    h.maxCarry = gdb.getShort();
    h.playerRoom = gdb.getShort();
    h.nTreasures = gdb.getShort();
    h.wordLength = gdb.getShort();
    h.lightTime = gdb.getShort();
    h.nMessages = gdb.getShort();
    h.treasureRoom = gdb.getShort();
    // Due to the anomaly in the original interpreter
    h.nItems++;
    h.nActions++;
    h.nWords++;
    h.nRooms++;
    h.nTreasures++;
    h.nMessages++;

    return h;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameAction
// Single game action line and appropriate load function. Doesn't include
// interpreter logic for the line.

class GameAction {
  // Structure of the line, unpacked and pre-interpreted. Action parameters
  // (arguments of condition code 0) are retrieved and placed into additional
  // array.
  private short verb;              // Verb for this action
  private short noun;              // Noun for this action
  private short condition[];       // Array of condition codes
  private short parameter[];       // Conditions' parameters
  private short action[];          // Array of action codes
  private short actionParameter[]; // Actions' parameters

  // Inquisitor functions.
  public short getVerb() {
    return verb;
  }

  public short getNoun() {
    return noun;
  }

  public short getCondition(int n) {
    return condition[n];
  }

  public short getParameter(int n) {
    return parameter[n];
  }

  public short getAction(int n) {
    return action[n];
  }

  public short getActionParameter(int n) {
    return actionParameter[n];
  }

  /* static GameAction fromDatabase(GameDatabase gdb)
  Create and load single action line from the game database. It is assumed that
  stream pointer is at the beginning of the line data. The line is unpacked.
  */
  public static GameAction fromDatabase(GameDatabase gdb)
      throws IOException {
    GameAction a = new GameAction();
    // Read vocabulary information (verb/noun numbers)
    // int is used instead of short here to eliminate risk of sign
    // overflow
    int inputVal;
    inputVal = gdb.getShort();
    a.verb = (short) (inputVal / 150);
    a.noun = (short) (inputVal % 150);
    // Allocate memory for conditions
    a.condition = new short[5];
    a.parameter = new short[5];
    a.actionParameter = new short[5];
    // Read and unpack conditions
    int curAct = 0;
    for (int i = 0; i < 5; i++) {
      inputVal = gdb.getInt();
      a.condition[i] = (short) (inputVal % 20);
      a.parameter[i] = (short) (inputVal / 20);
      // Retrieve actions parameters
      if (a.condition[i] == 0) {
        a.actionParameter[curAct++] =
            a.parameter[i];
      }
    }
    // Allocate memory for actions
    a.action = new short[4];
    // Read and unpack actions
    inputVal = gdb.getInt();
    a.action[0] = (short) (inputVal / 150);
    a.action[1] = (short) (inputVal % 150);
    inputVal = gdb.getInt();
    a.action[2] = (short) (inputVal / 150);
    a.action[3] = (short) (inputVal % 150);

    return a;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameRoom
// Game room data and load function.

class GameRoom {
  // Some predefined locations:
  public final static int CARRIED_ALT = -1;
  public final static int CARRIED = 255;
  public final static int DESTROYED = 0;

  // Room data
  private short roomExit[];
  private String description;

  // Inquisitor functions. Note that exits are numbered starting from 1.
  public String getDescription() {
    return description;
  }

  public short getExit(int n) {
    return roomExit[n - 1];
  }

  /* static GameRoom fromDatabase(GameDatabase gdb)
  Create and load room data from the game database. It is assumed that
  stream pointer is at the beginning of the room data.
  */
  public static GameRoom fromDatabase(GameDatabase gdb)
      throws IOException {
    GameRoom r = new GameRoom();
    r.roomExit = new short[6];
    for (int i = 0; i < 6; i++)
      r.roomExit[i] = gdb.getShort();
    r.description = gdb.getString();
    return r;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameWord
// Vocabulary entry data and load function. Also word compare functions.

class GameWord {
  // Some predefined word indexes.
  public final static int AUTO = 0; // verb #0
  public final static int ANY = 0; // noun #0
  // By design: GO=1, GET=10, PUT=18, NORTH=1, SOUTH=2, EAST=3, WEST=4, UP=5,
  // DOWN=6.
  public final static int GO = 1;
  public final static int GET = 10;
  public final static int PUT = 18;
  public final static int FIRSTDIR = 1;
  public final static int LASTDIR = 6;
  public final static int BAD = -1; // Just a bad value

  // Word data -- the word itself, its compare length and flag if it is
  // a synonym for the previous word in the vocabulary.
  // [Funny thing happened here. As compare length is the same for all words in
  // the vocabulary, I implemented it as static. Then I started two applets in
  // IE4 and loaded two different games (namely, Adventureland and Waxworks).
  // The second applet worked fine, but the first one had strange problems...
  // Finally I discovered that static all Java programs run in the same memory
  // space and different instances of the same class share static fields between
  // different applets. Well, so much for process security. If it is a bug, then
  // it is bad bug, but if it is a feature... it is very very bad feature.]
  private String text;
  private int lengthToCompare;
  private boolean synonym;

  // Inquisitor functions
  public String getText() {
    return text;
  }

  public boolean isSynonym() {
    return synonym;
  }

  /* boolean matches(String word)
  Check if this word matches word specified by string. Significant length is
  used for comparing.
  */
  public boolean matches(String word) {
    return match(text, word, lengthToCompare);
  }
  //

  /* static boolean match(String word1, String word2)
  Check if two words match. Significant length is used for comparing.
  */
  public static boolean match(String word1, String word2, int lengthToCompare) {
    if (word1 == null || word2 == null ||
        word1.length() == 0 || word2.length() == 0) {
      return false;
    }

    if (word1.length() < lengthToCompare) {
      if (word1.length() == word2.length()) {
        return word1.regionMatches(true, 0, word2, 0,
            word1.length());
      } else {
        return false;
      }
    } else {
      return word1.regionMatches(true, 0, word2, 0,
          lengthToCompare);
    }
  }
  //

  /* static GameWord fromDatabase(GameDatabase gdb)
  Create and load single word from the game database. It is assumed that
  stream pointer is at the beginning of the word data.
  */
  public static GameWord fromDatabase(GameDatabase gdb, int lengthToCompare)
      throws IOException {
    GameWord w = new GameWord();
    w.lengthToCompare = lengthToCompare;
    w.text = gdb.getString();
    // Star symbol at the beginning of the word means that this word
    // is a synonym entry.
    if (w.text.startsWith("*")) {
      w.text = w.text.substring(1);
      w.synonym = true;
    } else {
      w.synonym = false;
    }
    return w;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameItem
// Game item data and loading function.

class GameItem {
  // Predefined item number -- lamp is always item #9. Why 9?
  public final static int LAMP = 9;

  // Item data -- name, word to use with GET/PUT (auto-pick) and initial
  // location.
  private String description;     // Item description
  private String autoPick;        // Auto-pick name
  private short initialLocation;   // Initial location

  // Inquisitor functions.
  public String getDescription() {
    return description;
  }

  public short getLocation() {
    return initialLocation;
  }

  public String getAutoPick() {
    return autoPick;
  }

  /* boolean isTreasure()
  By design: if item name starts with '*', this item is a treasure. Not very
  flexible.
  */
  public boolean isTreasure() {
    return description.startsWith("*");
  }
  //

  /* static GameItem fromDatabase(GameDatabase gdb)
  Create and load single item data from the game database. It is assumed that
  stream pointer is at the beginning of the item data. Data is somewhat
  unpacked by this function.
  */
  public static GameItem fromDatabase(GameDatabase gdb)
      throws IOException {
    GameItem i = new GameItem();
    i.description = gdb.getString();
    i.initialLocation = gdb.getShort();
    i.autoPick = null;
    // This is a trick. If item name ends with /something/ than this
    // something is a word to use with GET or PUT in addition to common
    // vocabulary entries. Sounds a little odd, but it is exactly how it
    // was originally designed. I am sure it was a good idea that time.
    if (i.description.endsWith("/")) {
      int start = i.description.indexOf('/');
      if (start != -1 && start < i.description.length() - 2) {
        i.autoPick = i.description.substring(start + 1,
            i.description.length() - 1);
        i.description = i.description.substring(0, start);
      }
    }

    return i;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameMessage
// Game message data and loading function.

class GameMessage {
  // Message data, err, string.
  private String text;

  // Inquisitor function
  public String getText() {
    return text;
  }

  /* static GameMessage fromDatabase(GameDatabase gdb)
  Create and load message from the game database. It is assumed that
  stream pointer is at the beginning of the message. Not much to say
  */
  public static GameMessage fromDatabase(GameDatabase gdb)
      throws IOException {
    GameMessage m = new GameMessage();
    m.text = gdb.getString();
    return m;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameComment
// Action comment data and loading function. I have no idea how driver is
// supposed to use these comments, but they may be useful for debugging (as
// if anybody cares).

class GameComment {
  // Comment data
  private String text;

  // Inquisitor function
  public String getText() {
    return text;
  }

  /* static GameComment fromDatabase(GameDatabase gdb)
  Create and load comment from the game database. It is assumed that
  stream pointer is at the beginning of the comment.
  */
  public static GameComment fromDatabase(GameDatabase gdb)
      throws IOException {
    GameComment c = new GameComment();
    c.text = gdb.getString();
    return c;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameTail
// Game tailer data and loading function. For some reasons unknown, game
// database contains both header and tailer. Not a big deal, just odd.

class GameTail {
  // Tailer structure data
  private short version;          // Version number
  private short adventureNumber;  // Adventure number
  private short unknownTailMagic;  // Unknows, some magic number (?)

  // Inquisitor functions
  public short getVersion() {
    return version;
  }

  public short getAdventureNumber() {
    return adventureNumber;
  }

  /* static GameTail fromDatabase(GameDatabase gdb)
  Create and load tailer data from the game database. It is assumed that
  stream pointer is at the beginning of the tailer data. Implementation is
  straightforward.
  */
  public static GameTail fromDatabase(GameDatabase gdb)
      throws IOException {
    GameTail t = new GameTail();
    t.version = gdb.getShort();
    t.adventureNumber = gdb.getShort();
    t.unknownTailMagic = gdb.getShort();
    return t;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// GameState
// Game runtime state -- flags, counters, current room number and item
// locations. Also load and save logic. Interpreter changes state of this
// object performing actions.

class GameState {
  // Predefined flag number
  public final static int DARK = 15;

  // Link to game header data (not the header itself)
  private GameHeader header;

  // Runtime data
  private short currentRoom;  // current room number
  private short singleRoomStore;  // current room storage...
  private short roomStore[];      // ... multiple

  private short itemLocation[]; // item locations

  // Some comment about flags and counters counts. The original game interpreter
  // worked only with 16-bit values and saved all flags in one word, so it is
  // safe to assume that there are no more than 16 flags. Another hint is that
  // flag 15 is reserved for darkness bit. It is so typical to reserve the last
  // available element for something... Most games address no more than 8
  // counters but I think 16 is much safer value. ScottFree uses this value as
  // well.
  private BitSet flags;          // game flags
  private short counter[];        // game counters
  private short currentCounter;   // current (easily accessible) counter

  private short lightTime;        // time of lamp charge left

  private boolean endGame;        // "game over" flag
  private boolean roomChanged;    // "room state changed" flag

  // Inquisitor functions
  public short getCurrentRoom() {
    return currentRoom;
  }

  public short getItemLocation(int n) {
    return itemLocation[n];
  }

  public short getCurrentCounter() {
    return currentCounter;
  }

  public short getCounter(int n) {
    return counter[n];
  }

  public boolean getFlag(int n) {
    return flags.get(n);
  }

  public short getLightTime() {
    return lightTime;
  }

  public boolean isEndGame() {
    return endGame;
  }

  public boolean isRoomChanged() {
    return roomChanged;
  }

  /* constructor
  Allocate memory and reset state to the default. Needs header and array of
  items for initialization.
  */
  public GameState(GameHeader hdr, GameItem item[]) {
    header = hdr;

    flags = new BitSet(16);
    counter = new short[16];
    currentCounter = 0;

    singleRoomStore = 0;
    roomStore = new short[16];

    lightTime = header.getLightTime();
    currentRoom = header.getStartingRoom();

    itemLocation = new short[header.getItemCount()];

    for (int i = 0; i < header.getItemCount(); i++)
      itemLocation[i] = item[i].getLocation();

    endGame = false;
  }
  //

  /* void save(OutputStream os)
  Save game runtime state to output stream. Format is compatible with ScottFree.
  */
  public void save(OutputStream os) throws IOException {
    GameDatabase gdb = new GameDatabase(os);

    for (int i = 0; i < 16; i++) {
      gdb.putShort(counter[i]);
      gdb.putShort(roomStore[i]);
      gdb.putNewLine();
    }
    int flagWord = 0; // int is to prevent sign overflow
    for (int i = 0; i < 16; i++)
      if (flags.get(i)) {
        flagWord |= (1 << i);
      }
    gdb.putInt(flagWord);
    gdb.putShort((isDark() ? (short) 1 : (short) 0));
    gdb.putShort(currentRoom);
    gdb.putShort(currentCounter);
    gdb.putShort(singleRoomStore);
    gdb.putShort(lightTime);
    gdb.putNewLine();
    for (int i = 0; i < header.getItemCount(); i++) {
      gdb.putShort(itemLocation[i]);
      gdb.putNewLine();
    }
  }
  //

  /**
   * Load game runtime state from the input stream.
   */
  public void load(InputStream is) throws IOException {
    GameDatabase gdb = new GameDatabase(is);

    for (int i = 0; i < 16; i++) {
      counter[i] = gdb.getShort();
      roomStore[i] = gdb.getShort();
    }
    int flagWord = gdb.getInt();  // int is to prevent sign overflow
    for (int i = 0; i < 16; i++) {
      if ((flagWord & (1 << i)) != 0) {
        flags.set(i);
      } else {
        flags.clear(i);
      }
    }
    if (gdb.getInt() != 0) {
      setDark();
    } else {
      clearDark();
    }
    currentRoom = gdb.getShort();
    currentCounter = gdb.getShort();
    singleRoomStore = gdb.getShort();
    lightTime = gdb.getShort();
    for (int i = 0; i < header.getItemCount(); i++)
      itemLocation[i] = gdb.getShort();

    roomChanged = true;
  }
  //

  /**
   * Move to specified room.
   */
  public void setCurrentRoom(short room) {
    currentRoom = room;
    roomChanged = true;
  }
  //

  /**
   * Swap current room number and the value stored in single room save slot.
   */
  public void swapRoom() {
    if (currentRoom != singleRoomStore) {
      short tmp = currentRoom;
      currentRoom = singleRoomStore;
      singleRoomStore = tmp;
      roomChanged = true;
    }
  }
  //

  /**
   * Swap current room number and the value stored in specified room save slot.
   */
  public void swapRoom(short slot) {
    if (currentRoom != roomStore[slot]) {
      short tmp = currentRoom;
      currentRoom = roomStore[slot];
      roomStore[slot] = tmp;
      roomChanged = true;
    }
  }
  //

  /**
   * Set specified flag to true.
   */
  public void setFlag(int n) {
    if (!flags.get(n)) {
      flags.set(n);
      if (n == DARK) {
        roomChanged = true;
      }
    }
  }
  //

  /**
   * Clear specified flag.
   */
  public void clearFlag(int n) {
    if (flags.get(n)) {
      flags.clear(n);
      if (n == DARK) {
        roomChanged = true;
      }
    }
  }
  //

  /**
   * Set DARK flag
   */
  public void setDark() {
    flags.set(DARK);
  }
  //

  /**
   * Clear DARK flag
   */
  public void clearDark() {
    flags.clear(DARK);
  }
  //

  /**
   * Check DARK flag value.
   */
  public boolean isDark() {
    return flags.get(DARK);
  }
  //

  /**
   * Check if current room is actually dark (DARK flag is set and there is no
   * lamp here.
   */
  public boolean isReallyDark() {
    return isDark() && !isLampHere() && lightTime <= 0;
  }
  //

  /**
   * Set value of the current counter.
   */
  public void setCurrentCounter(short ctr) {
    currentCounter = ctr;
  }
  //

  /**
   * Swap current counter value and value in the specified counter slot.
   */
  public void selectCounter(short slot) {
    short tmp = currentCounter;
    currentCounter = counter[slot];
    counter[slot] = tmp;
  }
  //

  /**
   * Move item to specified room.
   */
  public void setItemLocation(int n, short loc) {
    if (itemLocation[n] != loc) {
      if (itemLocation[n] == currentRoom ||
          loc == currentRoom) {
        roomChanged = true;
      }
      itemLocation[n] = loc;
    }
  }
  //

  /**
   * Check if item is in current room (not carried).
   */
  public boolean isItemInCurrentRoom(int n) {
    return itemLocation[n] == currentRoom;
  }
  //

  /**
   * Check if item is being carried.
   */
  public boolean isItemCarried(int n) {
    return itemLocation[n] == GameRoom.CARRIED || itemLocation[n] == GameRoom.CARRIED_ALT;
  }
  //

  /**
   * Check if item is destroyed.
   */
  public boolean isItemDestroyed(int n) {
    return itemLocation[n] == GameRoom.DESTROYED;
  }
  //

  /**
   * Check if item is in current room or being carried.
   */
  public boolean isItemHere(int n) {
    return isItemCarried(n) || isItemInCurrentRoom(n);
  }
  //

  /**
   * Move item to current room.
   */
  public void moveItemToCurrentRoom(int n) {
    setItemLocation(n, currentRoom);
  }
  //

  /**
   * Mark item as being carried.
   */
  public void carryItem(int n) {
    setItemLocation(n, (short) GameRoom.CARRIED);
  }
  //

  /**
   * Mark item as destroyed
   */
  public void destroyItem(int n) {
    setItemLocation(n, (short) GameRoom.DESTROYED);
  }
  //

  /**
   * Swap two items locations
   */
  public void swapItems(int n1, int n2) {
    if (itemLocation[n1] != itemLocation[n2]) {
      if (itemLocation[n1] == currentRoom ||
          itemLocation[n2] == currentRoom) {
        roomChanged = true;
      }
      short loc = itemLocation[n1];
      itemLocation[n1] = itemLocation[n2];
      itemLocation[n2] = loc;
    }
  }
  //

  /**
   * Carry LAMP item.
   */
  public void carryLamp() {
    carryItem(GameItem.LAMP);
  }
  //

  /**
   * Destroy LAMP item.
   */
  public void destroyLamp() {
    destroyItem(GameItem.LAMP);
  }
  //

  /**
   * Check if LAMP item is in current room.
   */
  public boolean isLampInCurrentRoom() {
    return isItemInCurrentRoom(GameItem.LAMP);
  }
  //

  /**
   * Check if LAMP item is being carried.
   */
  public boolean isLampCarried() {
    return isItemCarried(GameItem.LAMP);
  }
  //

  /**
   * Check if LAMP item is destroyed.
   */
  public boolean isLampDestroyed() {
    return isItemDestroyed(GameItem.LAMP);
  }
  //

  /**
   * Check if LAMP item is in current room or being carried.
   */
  public boolean isLampHere() {
    return isItemHere(GameItem.LAMP);
  }
  //

  /**
   * Count items that are marked as being carried.
   */
  public int countCarried() {
    int ctr = 0;
    for (int i = 0; i < header.getItemCount(); i++)
      if (isItemCarried(i)) {
        ctr++;
      }
    return ctr;
  }
  //

  /**
   * Check if it is possible to carry more items.
   */
  public boolean canCarryMore() {
    return countCarried() < header.getMaxCarry();
  }
  //

  /**
   * Set lamp charge.
   */
  public void setLightTime(short t) {
    if (lightTime != t) {
      if (lightTime == 0 || t == 0) {
        roomChanged = true;
      }
      lightTime = t;
    }
    if (lightTime < 0) {
      lightTime = 0;
    }
  }
  //

  /**
   * Clear "Room Changed" flag.
   */
  public void clearRoomChanged() {
    roomChanged = false;
  }
  //

  /**
   * Set "Room Changed" flag.
   */
  public void setRoomChanged() {
    roomChanged = true;
  }
  //

  /**
   * Set "Game Over" state.
   */
  public void setEndGame() {
    endGame = true;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// Adventure
// Main interpreter class containing the interpreter itself together with
// all relevant data. Data is structured using additional interpreter
// classes -- GameHeader, GameAction, etc.

class Adventure {
  // Global objects
  UserInterface ui = null; // user interface class
  LanguageDatabase text = null;     // language database

  // Game database
  GameHeader header;    // Header
  GameAction[] action;    // Array of game actions
  GameWord[] verb;    // Array of verbs
  GameWord[] noun;    // Array of nouns
  GameRoom[] room;    // Array of rooms
  GameMessage[] message;    // Array of messages
  GameItem[] item;    // Array of items
  GameComment[] comment;    // Array of comments
  GameTail tail;                  // Tailer

  // Current session data
  GameState gameState;
  Random random;

  /* constructor
  Accepts the following parameters -- interface (frontend) object, language
  database input stream (null for none) and game script input stream.
  */
  Adventure(UserInterface saui, InputStream language, InputStream is)
      throws IOException {
    text = new LanguageDatabase(language);
    ui = saui;
    random = new Random();
    readDatabase(is);
    restart();
  }
  //

  /* void readDatabase(InputStream is)
  Read game database from the specified input stream. Bad formed stream will
  result in IOException.
  Side note: For some obscure reasons the game ignores entry #0 in almost all
  arrays -- room #0, message #0, word #0 are never used...
  */
  private void readDatabase(InputStream is) throws IOException {
    GameDatabase gdb =
        new GameDatabase(new BufferedInputStream(is, 1024));

    // Read header
    header = GameHeader.fromDatabase(gdb);

    // Read action table
    action = new GameAction[header.getActionCount()];
    for (int i = 0; i < header.getActionCount(); i++)
      action[i] = GameAction.fromDatabase(gdb);

    // Read vocabulary
    verb = new GameWord[header.getWordCount()];
    noun = new GameWord[header.getWordCount()];
    for (int i = 0; i < header.getWordCount(); i++) {
      verb[i] = GameWord.fromDatabase(gdb, header.getWordLength());
      noun[i] = GameWord.fromDatabase(gdb, header.getWordLength());
    }

    // Read rooms
    room = new GameRoom[header.getRoomCount()];
    for (int i = 0; i < header.getRoomCount(); i++)
      room[i] = GameRoom.fromDatabase(gdb);

    // Read messages
    message = new GameMessage[header.getMessageCount()];
    for (int i = 0; i < header.getMessageCount(); i++)
      message[i] = GameMessage.fromDatabase(gdb);

    // Read items
    item = new GameItem[header.getItemCount()];
    for (int i = 0; i < header.getItemCount(); i++)
      item[i] = GameItem.fromDatabase(gdb);

    // Read comments (one for each action, not very useful)
    comment = new GameComment[header.getActionCount()];
    for (int i = 0; i < header.getActionCount(); i++)
      comment[i] = GameComment.fromDatabase(gdb);

    // Read tail information
    tail = GameTail.fromDatabase(gdb);
  }
  //

  /* boolean debugCommand(String command)
  Check if entered command is debugging command (available only in this version
  of the interpreter. Return true if command is a debugging one. Currently
  implemented commands are:
  #flags - print values of all flags
  #counters - print values of all counters
  #room - current room number (cheat!)
  #dump - all of the above
  #words - list of all words in the game (cheat!)
  */
  private boolean debugCommand(String command) {
    if (!command.startsWith(text.get("#"))) {
      return false; // non-debug command
    }

    boolean completeDump = command.startsWith(text.get("#dump"));
    if (completeDump || command.startsWith(text.get("#room"))) {
      ui.printText(text.get("In room ") +
          gameState.getCurrentRoom());
      ui.printText(text.get(". North ") + room[gameState.getCurrentRoom()].getExit(1));
      ui.printText(text.get(", South ") + room[gameState.getCurrentRoom()].getExit(2));
      ui.printText(text.get(", East ") + room[gameState.getCurrentRoom()].getExit(3));
      ui.printText(text.get(", West ") + room[gameState.getCurrentRoom()].getExit(4));
      ui.printText(text.get(", Up ") + room[gameState.getCurrentRoom()].getExit(5));
      ui.printText(text.get(", Down ") + room[gameState.getCurrentRoom()].getExit(6));
      ui.printText(text.get(".\n"));
    }
    if (completeDump || command.startsWith(text.get("#flags"))) {
      ui.printText(text.get("Flags:"));
      for (int i = 0; i < 16; i++)
        ui.printText(text.get(" ") + i + text.get(":") +
            (gameState.getFlag(i) ? text.get("T") : text.get("F")));
      ui.printText(text.get("\n"));
    }
    if (completeDump || command.startsWith(text.get("#counters"))) {
      ui.printText(text.get("Counters:"));
      for (int i = 0; i < 8; i++)
        ui.printText(text.get(" ") + i + text.get(":") +
            gameState.getCounter(i));
      ui.printText(text.get("\n"));
    }
    if (completeDump || command.startsWith(text.get("#items"))) {
      ui.printText(text.get("Items:"));
      for (GameItem gameItem : item)
        ui.printText(text.get(" ") + gameItem.getDescription() + text.get(" in ") +
            gameItem.getLocation() + "\n");
      ui.printText(text.get("\n"));
    }

    if (command.startsWith(text.get("#words"))) {
      ui.printText(text.get("Verbs:  "));
      for (int i = 0; i < header.getWordCount(); i++) {
        if (verb[i].getText().trim().length() > 0) {
          ui.printText(verb[i].getText().trim() + "  ");
        }
      }
      ui.printText(text.get("\nNouns:  "));
      for (int i = 0; i < header.getWordCount(); i++) {
        if (noun[i].getText().trim().length() > 0) {
          ui.printText(noun[i].getText().trim() + "  ");
        }
      }
      ui.printText(text.get("\n"));
    }
    return true; // debug command is processed
  }
  //

  /* int itemByNoun(String word)
  Find item specified by noun. Item must be in current room or being carried.
  There is some weird logic here, but it is the way how it works in ScottFree.
  I tried to play with that resriction on the item location, but it appears to
  be necessary in case when there is more than one item with the same name.
  */
  private int itemByNoun(String word) {
    int index = whichWord(word, noun);
    String realName = (index == GameWord.BAD) ? word : noun[index].getText();
    for (int i = 0; i < header.getItemCount(); i++) {
      if (gameState.isItemHere(i) &&
          GameWord.match(realName, item[i].getAutoPick(), header.getWordLength())) {
        return i;
      }
    }
    return GameWord.BAD;
  }
  //

  /* int whichWord(String word, GameWord vocabulary[])
  Find given word in specified vocabulary. Return value is GameWord.BAD if the
  word is not found.
  */
  private int whichWord(String word, GameWord vocabulary[]) {
    if (word != null) {
      for (int i = 0; i < header.getWordCount(); i++) {
        if (vocabulary[i].matches(word)) {
          for (int j = i; j >= 0; j--)
            if (!vocabulary[j].isSynonym()) {
              return j;
            }
          return GameWord.BAD;
        }
      }
    }
    return GameWord.BAD;
  }
  //

  /* String translateAbbreaviations(String word)
  Translate one letter abbreviations for directions. Also supports Howard's
  extension -- abbreviation for INVENTORY. Returns expanded abbreviation if
  translation exists or the original word if not.
  */
  private final static String strAbbreviations[] =
      { "NORTH", "SOUTH", "EAST", "WEST", "UP", "DOWN", "INVENTORY" };

  private String translateAbbreviations(String word) {
    if (word.length() == 1) {
      char c = Character.toUpperCase(word.charAt(0));
      for (int i = 0; i < Array.getLength(strAbbreviations); i++)
        if (c == strAbbreviations[i].charAt(0)) {
          return text.get(strAbbreviations[i]);
        }
    }
    return word;
  }
  //

  /* boolean getInput()
  Get input line and interpret it. Return result is true if line has at least
  the first word properly interpreted - it is enough for this type of game.
  It also produces some side effect (weird, side effect in Java code) changing
  four private variables. These variable contain last entered verb and noun
  (in interpreter meaning) and interpreted values for these verb and noun.
  */
  private String lastEnteredVerb = null;
  private String lastEnteredNoun = null;
  private int nVerb = GameWord.BAD;
  private int nNoun = GameWord.BAD;

  private boolean getInput() {
    String input;
    do {
      input = ui.getUserInput().trim();
      if (debugCommand(input)) {
        return false;
      }
    }
    while (input.isEmpty());

    int ind = input.indexOf(' ');
    if (ind == -1) {
      lastEnteredVerb = input;
      lastEnteredNoun = null;
    } else {
      lastEnteredVerb = input.substring(0, ind);
      input = input.substring(ind).trim();
      ind = input.indexOf(' ');
      if (ind == -1) {
        lastEnteredNoun = input;
      } else {
        lastEnteredNoun = input.substring(0, ind);
      }
    }

    if (lastEnteredNoun == null) {
      lastEnteredVerb = translateAbbreviations(lastEnteredVerb);
    }

    nNoun = whichWord(lastEnteredVerb, noun);
    if (nNoun >= GameWord.FIRSTDIR && nNoun <= GameWord.LASTDIR) {
      nVerb = GameWord.GO; // i.e. GO NORTH
    } else {
      nVerb = whichWord(lastEnteredVerb, verb);
      nNoun = whichWord(lastEnteredNoun, noun);
    }

    if (nVerb == GameWord.BAD) {
      ui.printText(text.get("\"") + lastEnteredVerb +
          text.get("\" is a word I don't know...sorry!\n"));
    }
    return nVerb != -1;
  }
  //

  /* void restart()
  Reset game to the initial state. All necessary structures are recreated and
  properly initialized. Technically that means that GameState is recreated and
  all items are moved to initial locations.
  */
  public void restart() {
    gameState = new GameState(header, item);
  }
  //

  /* void loadGame()
  Load game state. Queries interface for the file input stream. Saved games
  are compatible with ScottFree. Note that loadGame() completely resets game
  state, so restart() is not necessary before loading.
  */
  public void loadGame() {
    try {
      InputStream is = ui.getLoadStream();
      gameState.load(is);
    } catch (FileNotFoundException ex) // no such file, non-fatal
    {
      ui.printText(text.get("Unable to restore game.\n"));
    } catch (IOException ex) // cannot properly read data
    {
      ui.printText(text.get("Unable to restore game.\n"));
      restart(); // internal structure is probably damaged
    }
  }
  //

  /* void saveGame()
  Save game state. Queries interface for the file output stream. Saved games
  are compatible with ScottFree.
  */
  public void saveGame() {
    try {
      OutputStream os = ui.getSaveStream();
      gameState.save(os);
      os.flush();
    } catch (IOException ex) {
      ui.printText(text.get("Unable to create save file.\n"));
      return;
    }
    ui.printText(text.get("\nSaved.\n"));
  }
  //

  /* String describeRoom()
  Returns current room description as it appears in the upper window in
  original games. No CR added at the end of the string. Room description can
  be an empty string, but it cannot be null.
  */
  public String describeRoom() {
    String result;
    if (gameState.isReallyDark()) {
      result = text.get("You can't see. It is too dark!");
    } else {
      result = room[gameState.getCurrentRoom()].getDescription();
      if (result.startsWith(text.get("*"))) {
        result = result.substring(text.get("*").length());
      } else {
        result = text.get("You are in a ") + result;
      }
    }
    return result;
  }
  //

  /* String[] describeExits()
  Returns list of obvious exits from the current room. If the room is dark,
  null will be returned. List is represented by an array of String objects.
  The first element of the array contains standard text "Obvious exits: ".
  Frontend object must take care about output formatting.
  */
  private final static String strExitName[] =
      { "North", "South", "East", "West", "Up", "Down" };

  public String[] describeExits() {
    if (!gameState.isReallyDark()) {
      int count = 0;
      for (int i = GameWord.FIRSTDIR; i <= GameWord.LASTDIR; i++)
        if (room[gameState.getCurrentRoom()].getExit(i) != 0) {
          count++;
        }

      String result[];
      if (count != 0) {
        result = new String[count + 1];
        result[0] = text.get("Obvious exits: ");
        count = 1;
        for (int i = GameWord.FIRSTDIR; i <= GameWord.LASTDIR; i++)
          if (room[gameState.getCurrentRoom()].getExit(i) != 0) {
            result[count++] = text.get(strExitName[i - 1]);
          }
      } else {
        result = new String[2];
        result[0] = text.get("Obvious exits: ");
        result[1] = text.get("none");
      }
      return result;
    } else {
      return null;
    }
  }
  //

  /* String[] describeItems()
  Returns list of visible items in current room. If nothing is visible, null
  will be returned. List is represented by an array of String objects. The
  first element of the array contains standard text "You can also see: ".
  Frontend object must take care about output formatting.
  */
  public String[] describeItems() {
    if (!gameState.isReallyDark()) {
      int count = 0;
      for (int i = 0; i < header.getItemCount(); i++)
        if (gameState.isItemInCurrentRoom(i)) {
          count++;
        }

      if (count > 0) {
        // The first entry contains the standard text
        String result[] = new String[count + 1];
        result[0] = text.get("You can also see: ");

        count = 1;
        for (int i = 0; i < header.getItemCount(); i++) {
          if (gameState.isItemInCurrentRoom(i)) {
            result[count++] =
                item[i].getDescription();
          }
        }
        return result;
      }
    }
    return null;
  }
  //

  /* boolean evalLine(int n)
  The first part of the interpreter core. Evaluates conditions in action line
  n and returns true if all conditions are true.
  */
  private boolean evalLine(int n) {
    boolean failed = false;
    for (int i = 0; i < 5 && !failed; i++) {
      short p = action[n].getParameter(i);

      switch (action[n].getCondition(i)) {
        case 0:
          break;
        case 1: /* HAS <arg> */
          failed = !gameState.isItemCarried(p);
          break;
        case 2: /* IS_IN_AR <arg> */
          failed = !gameState.isItemInCurrentRoom(p);
          break;
        case 3: /* IS_AVAIL <arg> */
          failed = !gameState.isItemHere(p);
          break;
        case 4: /* PLAYER_IN <arg> */
          failed = (gameState.getCurrentRoom() != p);
          break;
        case 5: /* IS_NOT_IN_AR <arg> */
          failed = gameState.isItemInCurrentRoom(p);
          break;
        case 6: /* HAS_NOT <arg> */
          failed = gameState.isItemCarried(p);
          break;
        case 7: /* PLAYER_NOT_IN <arg> */
          failed = (gameState.getCurrentRoom() == p);
          break;
        case 8: /* SET_BIT <arg> */
          failed = !gameState.getFlag(p);
          break;
        case 9: /* CLEARED_BIT <arg> */
          failed = gameState.getFlag(p);
          break;
        case 10:/* HAS_SOMETHING <arg> */
          failed = (gameState.countCarried() == 0);
          break;
        case 11:/* HAS_NOTHING <arg> */
          failed = (gameState.countCarried() != 0);
          break;
        case 12:/* IS_NOT_AVAIL <arg> */
          failed = gameState.isItemHere(p);
          break;
        case 13:/* IS_NOT_IN_ROOM0 <arg> */
          failed = gameState.isItemDestroyed(p);
          break;
        case 14:/* IS_IN_ROOM0 <arg> */
          failed = !gameState.isItemDestroyed(p);
          break;
        case 15:/* COUNTER <= <arg> */
          failed = (gameState.getCurrentCounter() > p);
          break;
        case 16:/* COUNTER > <arg> */
          failed = (gameState.getCurrentCounter() <= p);
          break;
        case 17:/* IS_IN_ORIGROOM <arg> */
          failed = (gameState.getItemLocation(p) != item[p].getLocation());
          break;
        case 18:/* IS_NOT_IN_ORIGROOM <arg> */
          failed = (gameState.getItemLocation(p) == item[p].getLocation());
          break;
        case 19:/* COUNTER == <arg> */
          failed = (gameState.getCurrentCounter() != p);
          break;
        // Note that there are no unknown condition codes...
      }
    }

    return !failed;
  }
  //

  /* boolean interpretLine(int n, boolean recursive)
  The interpreter core. Tries to execute line n. This function call evalLine
  to check the line. As the function can call itself (continuation lines), it
  has the second parameter which must be set to true in recursive calls.
  */
  private boolean interpretLine(int n, boolean recursive) {
    if (gameState.isEndGame() || !evalLine(n)) {
      return false;
    }

    boolean continuation = false;

    int pnum = 0; // Current parameter number

    for (int i = 0; i < 4; i++) {
      short a = action[n].getAction(i);
      if (a >= 1 && a <= 51) /* PRINT <a> */ {
        ui.printText(message[a].getText() + text.get("\n"));
        continue;
      } else if (a > 101) /* PRINT <a-50> */ {
        ui.printText(message[a - 50].getText() + text.get("\n"));
        continue;
      } else {
        switch (a) {
          case 0: /* NOP */
            break;
          case 52: /* GET <arg> */
            if (!gameState.canCarryMore()) {
              ui.printText(text.get("You are carrying too much.\n"));
            } else {
              gameState.carryItem(action[n].getActionParameter(pnum++));
            }
            break;
          case 53: /* MOVE_INTO_AR <arg> */
            gameState.moveItemToCurrentRoom(action[n].getActionParameter(pnum));
            break;
          case 54: /* GOTO <arg> */
            gameState.setCurrentRoom(action[n].getActionParameter(pnum++));
            break;
          case 55: /* REMOVE <arg> */
            gameState.destroyItem(action[n].getActionParameter(pnum++));
            break;
          case 56: /* SET_NIGHT */
            gameState.setDark();
            break;
          case 57: /* SET_DAY */
            gameState.clearDark();
            break;
          case 58: /* SET_BIT <arg> */
            gameState.setFlag(action[n].getActionParameter(pnum++));
            break;
          case 59: /* REMOVE <arg> (see 55) */
            gameState.destroyItem(action[n].getActionParameter(pnum++));
            break;
          case 60: /* CLEAR_BIT <arg> */
            gameState.clearFlag(action[n].getActionParameter(pnum++));
            break;
          case 61: /* KILL_PLAYER */
            ui.printText(text.get("You are dead.\n"));
            gameState.clearDark();
            gameState.setCurrentRoom((short) (header.getRoomCount() - 1));
            break;
          case 62: /* MOVE_X_INTO_Y <arg1> <arg2> */ {
            short inum = action[n].getActionParameter(pnum++);
            gameState.setItemLocation(inum, action[n].getActionParameter(pnum++));
          }
          break;
          case 63: /* QUIT */
            ui.printText(text.get("The game is now over.\n"));
            gameState.setEndGame();
            break;
          case 64: /* LOOK */
            gameState.setRoomChanged();
            break;
          case 65: /* SCORE */ {
            int storedTreasures = 0;
            for (int it = 0; it < header.getItemCount(); it++) {
              if (gameState.getItemLocation(it) == header.getTreasureRoom() &&
                  item[it].isTreasure()) {
                storedTreasures++;
              }
            }

            ui.printText(text.get("You have stored ") + storedTreasures +
                text.get(" treasures. On a scale of 0 to 100, that rates ") +
                (storedTreasures * 100 / header.getTreasureCount()) +
                text.get(".\n"));
            if (storedTreasures == header.getTreasureCount()) {
              ui.printText(text.get("Well done.\n"));
              gameState.setEndGame();
            }
          }
          break;
          case 66: /* INVENTORY */ {
            boolean found = false;
            ui.printText(text.get("You are carrying:\n"));
            for (int it = 0; it < header.getItemCount(); it++) {
              if (gameState.isItemCarried(it)) {
                if (found) {
                  ui.printText(text.get(" - "));
                }
                found = true;
                ui.printText(item[it].getDescription());
              }
            }
            if (!found) {
              ui.printText(text.get("Nothing"));
            }
            ui.printText(text.get(".\n"));
          }
          break;
          case 67: /* SET_BIT 0 */
            gameState.setFlag(0);
            break;
          case 68: /* CLEAR_BIT 0 */
            gameState.clearFlag(0);
            break;
          case 69: /* FILL_LAMP */
            gameState.setLightTime(header.getLightTime());
            gameState.carryLamp();
            break;
          case 70: /* CLS */
            ui.clearScreen();
            break;
          case 71: /* VE */
            saveGame();
            break;
          case 72: /* SWAP_ITEMS <arg1> <arg2> */ {
            short i1 = action[n].getActionParameter(pnum++);
            short i2 = action[n].getActionParameter(pnum++);
            gameState.swapItems(i1, i2);
          }
          break;
          case 73: /* CONTINUE: */
            continuation = true;
            break;
          case 74: /* GET_ALWAYS <arg> */
            gameState.carryItem(action[n].getActionParameter(pnum++));
            break;
          case 75: /* PUT_X_WITH_Y <arg1> <arg2> */ {
            short it1 = action[n].getActionParameter(pnum++);
            short it2 = action[n].getActionParameter(pnum++);
            gameState.setItemLocation(it1, gameState.getItemLocation(it2));
          }
          break;
          case 76: /* LOOK - see 64 */
            gameState.setRoomChanged();
            break;
          case 77: /* COUNTER -= 1 */
            if (gameState.getCurrentCounter() >= 0) {
              gameState.setCurrentCounter((short) (gameState.getCurrentCounter() - 1));
            }
            break;
          case 78: /* PRINT_COUNTER */
            ui.printText("" + gameState.getCurrentCounter());
            break;
          case 79: /* COUNTER = <arg> */
            gameState.setCurrentCounter(action[n].getActionParameter(pnum++));
            break;
          case 80: /* SWAP_LOC_RV */
            gameState.swapRoom();
            break;
          case 81: /* SWAP_COUNTER <arg> */
            gameState.selectCounter(action[n].getActionParameter(pnum++));
            break;
          case 82: /* COUNTER += <arg> */
            gameState.setCurrentCounter(
                (short) (gameState.getCurrentCounter() + action[n].getActionParameter(pnum++)));
            break;
          case 83: /* COUNTER -= <arg> */
            gameState.setCurrentCounter(
                (short) (gameState.getCurrentCounter() - action[n].getActionParameter(pnum++)));
            if (gameState.getCurrentCounter() < -1) {
              gameState.setCurrentCounter((short) -1);
            }
            break;
          case 84: /* ECHO_NOUN */
            ui.printText(lastEnteredNoun);
            break;
          case 85: /* ECHO_NOUN_CR */
            ui.printText(lastEnteredNoun + text.get("\n"));
            break;
          case 86: /* CR */
            ui.printText(text.get("\n"));
            break;
          case 87: /* SELECT_RV <arg>*/
            gameState.swapRoom(action[n].getActionParameter(pnum++));
            break;
          case 88: /* DELAY <arg> */
            ui.delay(2000);
            break;
          case 89: /* SHOW_PIC <arg> */
            pnum++;
            break;
          default:
            ui.printText(text.get("WARNING: Unknown action code #") + a +
                text.get(" at line ") + n + text.get("\n"));
            break;
        }
      }

      if (gameState.isEndGame()) {
        break;
      }
    }

    if (!recursive && continuation) {
      for (int line = n; line < header.getActionCount() - 1 &&
          action[line + 1].getVerb() == 0 &&
          action[line + 1].getNoun() == 0; line++) {
        interpretLine(line + 1, true);
      }
    }

    return true;
  }
  //

  /* boolean doGo(int direction)
  Try to apply GO direction command. Returns true if no further processing is
  necessary and all necessary messages are printed. Messages are not formatted
  and end with carriage return. This function can significantly change game
  state (sure it can!).
  */
  private boolean doGo(int direction) {
    if (direction == GameWord.BAD) // GO without direction
    {
      ui.printText(text.get("Give me a direction too.\n"));
      return true;
    } else if (direction >= GameWord.FIRSTDIR && direction <= GameWord.LASTDIR) {
      if (gameState.isReallyDark()) {
        ui.printText(text.get("Dangerous to move in the dark!\n"));
      }

      short dest = room[gameState.getCurrentRoom()].getExit(direction);
      if (dest != 0) {
        gameState.setCurrentRoom(dest);
        ui.printText(text.get("O.K.\n"));
      } else if (gameState.isReallyDark()) {
        ui.printText(text.get("You fell down and broke your neck.\n"));
        gameState.setEndGame();
      } else {
        ui.printText(text.get("You can't go in that direction.\n"));
      }
      return true;
    } else {
      return false;
    }
  }
  //

  /* boolean doGet(int item)
  Try to GET items. Returns true if no further processing is necessary and all
  necessary messages are printed (always). Messages are not formatted and end
  with carriage return.
  */
  private boolean doGet(int nItem) {
    if (lastEnteredNoun.equalsIgnoreCase(text.get("ALL"))) {
      if (gameState.isReallyDark()) {
        ui.printText(text.get("It is dark.\n"));
      } else {
        boolean taken = false;
        for (int it = 0; it < header.getItemCount(); it++) {
          if (gameState.isItemInCurrentRoom(it) &&
              item[it].getAutoPick() != null) {
            int doNoun = whichWord(item[it].getAutoPick(), noun);
            doActions(GameWord.GET, doNoun, true);
            if (!gameState.canCarryMore()) {
              ui.printText(text.get("You are carrying too much.\n"));
              break;
            } else {
              gameState.carryItem(it);
              ui.printText(item[it].getDescription() + text.get(": O.K.\n"));
              taken = true;
            }
          }
        }
        if (!taken) {
          ui.printText(text.get("Nothing taken.\n"));
        }
      }
    } else if (nItem == GameWord.BAD) {
      ui.printText(text.get("What ?\n"));
    } else if (!gameState.canCarryMore()) {
      ui.printText(text.get("You are carrying too much.\n"));
    } else {
      int it = itemByNoun(lastEnteredNoun);
      if (it == GameWord.BAD || !gameState.isItemInCurrentRoom(it)) {
        ui.printText(text.get("It is beyond your power to do that.\n"));
      } else {
        gameState.carryItem(it);
        ui.printText(text.get("O.K.\n"));
      }
    }
    return true;
  }
  //

  /* boolean doPut(int nItem)
  Try to PUT items. Returns true if no further processing is necessary and all
  necessary messages are printed (always). Messages are not formatted and end
  with carriage return.
  */
  private boolean doPut(int nItem) {
    if (lastEnteredNoun.equalsIgnoreCase(text.get("ALL"))) {
      boolean dropped = false;
      for (int it = 0; it < header.getItemCount(); it++) {
        if (gameState.isItemCarried(it) &&
            item[it].getAutoPick() != null) {
          int doNoun = whichWord(item[it].getAutoPick(), noun);
          doActions(GameWord.PUT, doNoun, true);
          gameState.moveItemToCurrentRoom(it);
          ui.printText(item[it].getDescription() + text.get(": O.K.\n"));
          dropped = true;
        }
      }
      if (!dropped) {
        ui.printText(text.get("Nothing dropped.\n"));
      }
    } else if (nItem == GameWord.BAD) {
      ui.printText(text.get("What ?\n"));
    } else {
      int it = itemByNoun(lastEnteredNoun);
      if (it == GameWord.BAD || !gameState.isItemCarried(it)) {
        ui.printText(text.get("It is beyond your power to do that.\n"));
      } else {
        gameState.moveItemToCurrentRoom(it);
        ui.printText(text.get("O.K.\n"));
      }
    }
    return true;
  }
  //

  /* boolean doActions(int actVerb, int actNoun, boolean recursiveCall)
  Perform actions specified by verb and noun numbers. Also used to perform
  automatic actions. The last parameter used to specify if the call is
  recursive (the function can be called from inside doGet or doPut). Return
  value is true if game is over.
  */
  private boolean doActions(int actVerb, int actNoun, boolean recursiveCall) {
    boolean somethingDone = false;
    boolean somethingTried = false;

    if (!recursiveCall) {
      gameState.clearRoomChanged();
    }

    if (actVerb == GameWord.GO && doGo(actNoun)) {
      somethingDone = true;
    } else {
      int line = 0;
      while (line < header.getActionCount()) {
        int v = action[line].getVerb();
        int n = action[line].getNoun();
        if (v == actVerb) {
          if ((v != GameWord.AUTO && (n == actNoun || n == GameWord.ANY)) ||
              (v == GameWord.AUTO && (Math.abs(random.nextInt()) % 100) < n)) {
            somethingTried = true;
            somethingDone |=
                interpretLine(line, false);
            if (somethingDone && v != GameWord.AUTO) {
              break;
            }
          }
        }
        line++;
      }
    }

    if (!recursiveCall) {
      if (actVerb == GameWord.GET && !somethingDone) {
        somethingDone = doGet(actNoun);
      } else if (actVerb == GameWord.PUT && !somethingDone) {
        somethingDone = doPut(actNoun);
      }

      if (somethingDone) {
        if (gameState.isRoomChanged()) {
          ui.notifyRoomChanged();
        }
      } else if (actVerb != GameWord.AUTO) {
        if (somethingTried) {
          ui.printText(text.get("I can't do that yet.\n"));
        } else {
          ui.printText(text.get("I don't understand your command.\n"));
        }
      }
    }

    return gameState.isEndGame();
  }
  //

  /* void lampTick()
  Process lamp.
  TODO: old lamp behavior
  */
  private void lampTick() {
    if (!gameState.isLampDestroyed() && header.getLightTime() != -1) {
      gameState.setLightTime((short) (gameState.getLightTime() - 1));
      if (gameState.getLightTime() < 1) {
        if (gameState.isLampCarried()) {
          ui.printText(text.get("Your light has run out. "));
        }
      } else if (gameState.getLightTime() < 25) {
        if (gameState.isLampHere()) {
          if (gameState.getLightTime() % 5 == 0) {
            ui.printText(text.get("Your light is growing dim. "));
          }
        }
      }
    }
  }
  //

  /* void run(InputStream is)
  The first entry point for the main game loop. Initializes all structures.
  Optionally can load saved game from input stream 'is'.
  */
  private boolean bFinished;

  public void run(InputStream is) {
    // (Optional) load game
    if (is != null) {
      try {
        gameState.load(is);
      } catch (IOException ex) {
        ui.printText(text.get("Unable to restore game.\n"));
        restart();
      }
    }

    // Initial redraw
    ui.notifyRoomChanged();

    // Banner text
    ui.printText(text.get(
        "* JScott - Version 1.00.\n" +
            "* A Scott Adams Classic Adventure driver in Java.\n" +
            "* Copyright (C) 1998 Vasyl Tsvirkunov.\n" +
            "* Bug reports, questions, comments to VTsvirkunov@maxis.com\n" +
            "* This program is distributed as freeware.\n\n"));

    bFinished = false;

    bFinished = doActions(GameWord.AUTO, GameWord.ANY, false);

    if (!bFinished) {
      ui.doPrompt(text.get("\nTell me what to do ? "));
    }
  }

  /* boolean tick()
  Main game loop - single pass. Should be called every time when the user input
  is available or can be requested. Return value of true means that the game
  is in progress. false means that the game is terminated.
  */
  public boolean tick() {
    // Do not continue after game is finished
    if (bFinished) {
      return false;
    }

    // Get user input and interpret it
    if (getInput()) {
      // Perform user actions
      if (doActions(nVerb, nNoun, false)) {
        bFinished = true;
        return false;
      }

      // Perform lamp logic
      lampTick();

      // Perform automatic actions
      if (doActions(GameWord.AUTO, GameWord.ANY, false)) {
        bFinished = true;
        return false;
      }
    }

    // Next prompt
    ui.doPrompt(text.get("\nTell me what to do ? "));

    return true;
  }
  //
}
////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////
// UserInterface
// Interface for frontend objects. This is the way how interpreter
// communicates with player.

interface UserInterface {
  /**
   * Notification called from inside game to force refresh of the non-scrolling
   * section of the screen. Proper implementation retrieves room, exits and objects
   * descriptions and renders these to screen or window (or whatever).
   */
  public void notifyRoomChanged();

  /**
   * Output string message to the scrolling section of the screen. Apply
   * formatting if necessary. Message may or may not contain carriage return.
   */
  public void printText(String message);

  /**
   * Clear scrolling section of the screen. This one is optional as many real
   * drivers didn't implement this.
   */
  public void clearScreen();

  /**
   * Show user prompt 'message'. Most frontends should just call printText.
   */
  public void doPrompt(String message);

  /**
   * Get input string and return it. Return value can be null or empty string.
   * Underlying code is fail-safe and doesn't rely on any particular structure
   * or the input string. Badly built strings will be simply rejected.
   */
  public String getUserInput();

  /**
   * Delay execution for the specified time period.
   */
  public void delay(int milliseconds);

  /**
   * Query for save game stream. Report errors as IOException.
   */
  public OutputStream getSaveStream() throws IOException;

  /**
   * Query for load game stream.
   */
  public InputStream getLoadStream() throws IOException, FileNotFoundException;
}
////////////////////////////////////////////////////////////////////////////

