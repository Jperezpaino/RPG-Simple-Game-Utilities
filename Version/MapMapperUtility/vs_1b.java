package es.noa.rad;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * MapMapperUtility - Utilidad para comparar si una imagen contiene
 * pequeñas imágenes de otra.
 *
 * Paso 1: Carga y visualización del tileset base (clear-tiles).
 */
public class MapMapperUtility {

    private static final String TILESET_PATH = "/assets/map/world/tileset/clear-tiles.png";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MapMapperUtility::run);
    }

    private static void run() {
        BufferedImage tilesetImage = loadTileset();
        if (tilesetImage == null) {
            System.err.println("No se pudo cargar el tileset: " + TILESET_PATH);
            return;
        }

        System.out.println("Tileset cargado correctamente.");
        System.out.println("Dimensiones: " + tilesetImage.getWidth() + " x " + tilesetImage.getHeight() + " px");

        showImage(tilesetImage);
    }

    /**
     * Carga la imagen del tileset desde los recursos del classpath.
     *
     * @return BufferedImage con la imagen cargada, o null si hay error.
     */
    private static BufferedImage loadTileset() {
        try (InputStream is = MapMapperUtility.class.getResourceAsStream(TILESET_PATH)) {
            if (is == null) {
                System.err.println("Recurso no encontrado en el classpath: " + TILESET_PATH);
                return null;
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            System.err.println("Error al leer la imagen: " + e.getMessage());
            return null;
        }
    }

    /**
     * Muestra la imagen en una ventana Swing.
     *
     * @param image La imagen a mostrar.
     */
    private static void showImage(BufferedImage image) {
        JFrame frame = new JFrame("MapMapperUtility - Tileset: clear-tiles");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel label = new JLabel(new ImageIcon(image));
        JScrollPane scrollPane = new JScrollPane(label);

        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
