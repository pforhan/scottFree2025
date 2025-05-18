package jscott;

/**
 * Swing frontend for JScott 1.00
 * Scott Adams Classic Adventure driver in Java.
 * Copyright (C) 1998 Vasyl Tsvirkunov.
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;

////////////////////////////////////////////////////////////////////////////

final public class JScottSwing extends JPanel
    implements UserInterface, ActionListener {

  // UI area is split to three text fields: static non-scrollable room
  // description, vertically scrollable game transcript and user input field.
  private final JTextArea roomDescription;
  private final JTextArea gameTranscript;
  private final JTextField userInput;
  // Main driver object
  private Adventure adventure = null;

  public JScottSwing(String gameName, String languageName) {
    // Initialize ui structure.
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    roomDescription = new JTextArea();
    roomDescription.setEditable(false);
    gameTranscript = new JTextArea();
    gameTranscript.setEditable(false);
    gameTranscript.setFocusable(false);
    DefaultCaret caret = (DefaultCaret) gameTranscript.getCaret();
    caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    userInput = new JTextField();

    setLayout(new BorderLayout(5, 5));
    add(roomDescription, BorderLayout.NORTH);
    add(new JScrollPane(gameTranscript), BorderLayout.CENTER);
    add(userInput, BorderLayout.SOUTH);

    // So we will be notified when user presses Enter.
    userInput.addActionListener(this);

    try {
      // Open game related files as local streams and create game object.
      InputStream gameData = getClass().getResourceAsStream("/" + gameName);
      InputStream languageData = null;
      if (languageName != null && !languageName.isEmpty()) {
        languageData = getClass().getResourceAsStream(languageName);
      }

      adventure = new Adventure(this, languageData, gameData);
      adventure.run(null);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  //

  // ****** ActionListener implementation

  // This will be called when user presses Enter.
  @Override public void actionPerformed(ActionEvent e) {
    // Record user input in transcript
    gameTranscript.append(userInput.getText() + "\n");
    // Interpret it
    adventure.tick();
    // Clear input field
    userInput.setText("");
  }
  //

  // ****** UserInterface implementation

  @Override public void notifyRoomChanged() {
    // Just some formatting stuff.
    StringBuilder strDescription = new StringBuilder(adventure.describeRoom()).append("\n");

    String[] exits = adventure.describeExits();
    if (exits != null) {
      int count = Array.getLength(exits);
      for (int i = 0; i < count; i++) {
        if (i > 1) {
          strDescription.append(", ");
        }
        strDescription.append(exits[i]);
      }
      strDescription.append(".\n");
    }

    String[] items = adventure.describeItems();
    if (items != null) {
      int count = Array.getLength(items);
      for (int i = 0; i < count; i++) {
        if (i > 1) {
          strDescription.append(" - ");
        }
        strDescription.append(items[i]);
      }
      strDescription.append("\n");
    }

    roomDescription.setText(strDescription.toString());
  }

  @Override public void printText(String message) {
    // Message is just appended to the transcript.
    gameTranscript.append(message);
    gameTranscript.setCaretPosition(gameTranscript.getDocument().getLength());
  }

  @Override public void clearScreen() {
    // We have nice scrollable transcript. Why should we destroy it?
    printText("\n---------\n");
  }

  @Override public void doPrompt(String message) {
    printText(message);
  }

  @Override public String getUserInput() {
    return userInput.getText();
  }

  @Override public void delay(int milliseconds) {
    // Straightforward implementation. Don't forget to disable user
    // input... and enable it afterwards.
    userInput.setEnabled(false);

    Timer delayed = new Timer(milliseconds, new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        userInput.setEnabled(true);
        ((Timer) e.getSource()).stop();
        ;
      }
    });
    delayed.start();
  }

  @Override public OutputStream getSaveStream() throws IOException {
    printText("Save file feature is not available in applet.\n");
    return null;
  }

  @Override public InputStream getLoadStream() throws IOException, FileNotFoundException {
    printText("Load file feature is not available in applet.\n");
    return null;
  }

  public static void main(String[] args) {
    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    String gameName = args[0];
    String languageName = null;
    if (args.length > 1) {
      languageName = args[1];
    }
    frame.getContentPane().add(new JScottSwing(gameName, languageName), BorderLayout.CENTER);
    frame.setSize(600, 400);
    frame.setVisible(true);
  }
}

