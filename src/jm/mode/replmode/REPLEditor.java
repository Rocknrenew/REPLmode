package jm.mode.replmode;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JPanel;

import processing.app.Base;
import processing.app.EditorFooter;
import processing.app.EditorState;
import processing.app.Mode;
import processing.app.Preferences;
import processing.app.RunnerListener;
import processing.app.Sketch;
import processing.app.SketchCode;
import processing.app.SketchException;
import processing.mode.java.JavaBuild;
import processing.mode.java.JavaEditor;
import processing.mode.java.runner.Runner;

/**
 * Main View Class. Handles the editor window including tool bar and menu. Has
 * access to the Sketch. Primarily used to display the REPL/Console toggle
 * buttons and to display the console/REPL pane appropriately. Adapted from
 * DebugEditor class of processing-experimental.
 * 
 * @author Martin Leopold <m@martinleopold.com>
 * @author Manindra Moharana &lt;me@mkmoharana.com&gt;
 * @author Joel Ruben Antony Moniz
 * 
 */

@SuppressWarnings("serial")
public class REPLEditor extends JavaEditor {

  /**
   * Panel with card layout which contains the p5 console and REPL panes
   */
  protected JPanel consoleREPLPane;

  /**
   * REPL/Console Pane
   */
  protected REPLConsolePane replConsole;

  protected Sketch replTempSketch;

  protected File untitledFolderLocation;

  Runner replRuntime;
  REPLRunner runtime;
  
  File srcFolder;
  File binFolder;

  REPLMode replMode;

  protected REPLEditor(Base base, String path, EditorState state, Mode mode) {
    super(base, path, state, mode);

    replMode = (REPLMode) mode;
    replRuntime = null;
    runtime = null;

    try {
      untitledFolderLocation = Base.createTempFolder("untitled", "repl", null);

      (new File(untitledFolderLocation, sketch.getFolder().getName())).mkdirs();
      File subdir = new File(untitledFolderLocation, sketch.getFolder()
          .getName());

      final File tempFile = new File(subdir, subdir.getName() + ".pde");//File.createTempFile("tmp", ".pde", subdir);
      tempFile.createNewFile();
      replTempSketch = new Sketch(tempFile.getAbsolutePath(), this);
      
      srcFolder = replTempSketch.makeTempFolder();
      binFolder = replTempSketch.makeTempFolder();
      
      /*
       * This is needed to add back the document listeners and make the editor
       * show the correct code, since otherwise the line creating a new sketch
       * for replTempSketch make the PDE think that it has switched to a new editor
       */
      this.sketch.reload();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Method to add a footer at the base of the editor with tabs to display the
   * Console, Errors pane and the REPL Console.
   */
  @Override
  public EditorFooter createFooter() {
    replConsole = new REPLConsolePane(this);

    EditorFooter footer = super.createFooter();
    footer.addPanel("REPL", replConsole);

    replConsole.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        replConsole.requestFocus();
      }
    });

    return footer;
  }

  protected void prepareInitialREPLRun(String replCode) {
    // We no longer want the window to close
//    handleREPLStop();
//    internalCloseRunner();
    statusEmpty();

    // do this to advance/clear the terminal window / dos prompt / etc
    for (int i = 0; i < 10; i++)
      System.out.println("");

    // clear the console on each run, unless the user doesn't want to
    if (Preferences.getBoolean("console.auto_clear")) {
      console.clear();
    }

    replTempSketch.getCurrentCode().setProgram(replCode);
  }

  public void handleREPLRun(String code, boolean refresh) {
    new Thread(new Runnable() {
      public void run() {
        // TODO: Check how this is to be called, and where
        prepareInitialREPLRun(code);
        try {
          replRuntime = handleREPLLaunch(replTempSketch, REPLEditor.this, refresh);
        } catch (Exception e) {
          replConsole.getCommandPromptPane().printStatusException(e);
          replConsole.getCommandPromptPane().undoLastStatement();
//          No longer needed, since window doesn't close
//          replConsole.getCommandPromptPane().runTempSketch(false, false);
        }
      }
    }).start();
  }

  /** Handles the standard Java "Run" or "Present" */
  public REPLRunner handleREPLLaunch(Sketch sketch, RunnerListener listener,
                             final boolean refresh) throws SketchException {
    JavaBuild build = new JavaBuild(sketch);
    String appletClassName = build.build(srcFolder, binFolder, false);
    if (appletClassName != null) {
      if (runtime == null || refresh) {
//        System.out.println("VM status at start: " + (runtime.vm() == null));
        handleREPLStop();
        runtime = new REPLRunner(build, listener);
      }
      new Thread(new Runnable() {
        public void run() {
          runtime.launchREPL(refresh); // this blocks until finished
        }
      }).start();
   /*   else {
        new Thread(new Runnable() {
          public void run() {
            runtime.recompileREPL(); // this blocks until finished
  //          replConsole.requestFocus();
          }
        }).start();
      }*/
      return runtime;
    }
    return null;
  }

  /**
   * Event handler called when hitting the stop button. Stops a running debug
   * session or performs standard stop action if not currently debugging.
   */
  public void handleREPLStop() {

    try {
      if (replRuntime != null) {
        replRuntime.close(); // kills the window
        replRuntime = null;
      }
    } catch (Exception e) {
      statusError(e);
    }
  }

  @Override
  public void internalCloseRunner() {
    super.internalCloseRunner();
    handleREPLStop();
  }

  @Override
  protected void setCode(SketchCode code) {
    super.setCode(code);
  }

}
