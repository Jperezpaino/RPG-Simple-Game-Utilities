package es.noa.rad;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

  /**
   * Unit tests for {@link Application}.
   */
  @DisplayName(
    "Application"
  )
  class ApplicationTest {

    @Nested
    @DisplayName(
      "launch()"
    )
    class LaunchTest {

      @Test
      @DisplayName(
        "launch() does not throw any exception"
      )
      void launch_doesNotThrow() {
        Assertions.assertDoesNotThrow(
          () -> SwingUtilities.invokeAndWait(
            Application::launch
          )
        );
      }

      @Test
      @DisplayName(
        "launch() creates a JFrame with the correct title"
      )
      void launch_createsFrameWithCorrectTitle() throws Exception {
        final AtomicReference<String> title
          = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
          () -> {
            Application.launch();
            for (final Window window : Window.getWindows()) {
              if (window instanceof JFrame frame) {
                title.set(frame.getTitle());
                frame.dispose();
              }
            }
          }
        );

        Assertions.assertEquals(
          "RPG Simple Game",
          title.get()
        );
      }

      @Test
      @DisplayName(
        "launch() creates a JFrame with size (1280, 720)"
      )
      void launch_createsFrameWithCorrectSize() throws Exception {
        final AtomicReference<Rectangle> bounds
          = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
          () -> {
            Application.launch();
            for (final Window window : Window.getWindows()) {
              if (window instanceof JFrame frame) {
                bounds.set(frame.getBounds());
                frame.dispose();
              }
            }
          }
        );

        Assertions.assertAll(
          () -> Assertions.assertEquals(
            1280,
            bounds.get().width
          ),
          () -> Assertions.assertEquals(
            720,
            bounds.get().height
          )
        );
      }

      @Test
      @DisplayName(
        "launch() creates a JFrame centered on screen"
      )
      void launch_createsFrameCenteredOnScreen() throws Exception {
        final AtomicReference<Rectangle> bounds
          = new AtomicReference<>();

        SwingUtilities.invokeAndWait(
          () -> {
            Application.launch();
            for (final Window window : Window.getWindows()) {
              if (window instanceof JFrame frame) {
                bounds.set(frame.getBounds());
                frame.dispose();
              }
            }
          }
        );

        final Rectangle workArea
          = GraphicsEnvironment
              .getLocalGraphicsEnvironment()
              .getMaximumWindowBounds();
        final int expectedX
          = workArea.x + (workArea.width  - bounds.get().width)  / 2;
        final int expectedY
          = workArea.y + (workArea.height - bounds.get().height) / 2;

        Assertions.assertAll(
          () -> Assertions.assertEquals(
            expectedX,
            bounds.get().x
          ),
          () -> Assertions.assertEquals(
            expectedY,
            bounds.get().y
          )
        );
      }

    }

    @Nested
    @DisplayName(
      "main()"
    )
    class MainTest {

      @Test
      @DisplayName(
        "main() does not throw any exception"
      )
      void main_doesNotThrow() {
        Assertions.assertDoesNotThrow(
          () -> Application.main()
        );
      }

    }

  }
