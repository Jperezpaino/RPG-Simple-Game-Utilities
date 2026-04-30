package es.noa.rad;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * MapMapperUtility - Utilidad para comparar si una imagen contiene
 * pequeñas imágenes de otra.
 *
 * Paso 3: Carga del mapa (clear-map) y visualización junto al tileset y tiles.
 *
 * Estructura del tileset:
 *   - Borde exterior de 1px rojo entre cada tile
 *   - Cada tile: 16 x 16 píxeles
 *   - Paso entre tiles (tile + borde): 17 px
 *   - Tiles vacíos: color sólido amarillo
 */
public class MapMapperUtility {

    private static final String TILESET_PATH = "/assets/map/world/tileset/clear-tiles.png";
    private static final String MAP_PATH     = "/assets/map/world/clear-map.png";

    // Tamaño de cada tile en píxeles
    private static final int TILE_SIZE    = 16;
    // Grosor del borde rojo entre tiles
    private static final int BORDER_SIZE  = 1;
    // Paso de grid: tile + borde
    private static final int TILE_STEP    = TILE_SIZE + BORDER_SIZE;
    // Offset inicial (borde exterior izquierdo/superior)
    private static final int GRID_OFFSET  = 1;
    // Factor de escala para mostrar los tiles ampliados
    private static final int DISPLAY_SCALE = 2;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MapMapperUtility::run);
    }

    private static void run() {
        // --- Tileset ---
        BufferedImage tilesetImage = loadImage(TILESET_PATH);
        if (tilesetImage == null) {
            System.err.println("No se pudo cargar el tileset: " + TILESET_PATH);
            return;
        }

        int cols = (tilesetImage.getWidth()  - GRID_OFFSET) / TILE_STEP;
        int rows = (tilesetImage.getHeight() - GRID_OFFSET) / TILE_STEP;

        System.out.println("=== TILESET ===");
        System.out.println("Dimensiones: " + tilesetImage.getWidth() + " x " + tilesetImage.getHeight() + " px");
        System.out.println("Cuadrícula detectada: " + cols + " columnas x " + rows + " filas = " + (cols * rows) + " tiles");

        List<TileEntry> tiles = extractTiles(tilesetImage, cols, rows);

        long nonEmpty = tiles.stream().filter(t -> !t.empty).count();
        System.out.println("Tiles con contenido: " + nonEmpty + " / " + tiles.size());

        // --- Mapa ---
        System.out.println("\n=== MAPA ===");
        BufferedImage mapImage = loadImage(MAP_PATH);
        if (mapImage == null) {
            System.err.println("No se pudo cargar el mapa: " + MAP_PATH);
            return;
        }
        System.out.println("Mapa cargado correctamente.");
        System.out.println("Dimensiones: " + mapImage.getWidth() + " x " + mapImage.getHeight() + " px");

        // --- Análisis ---
        System.out.println("\n=== ANÁLISIS ===");
        TileEntry[][] grid = analyzeMap(mapImage, tiles);
        BufferedImage analyzedImage = buildAnalyzedImage(grid);

        showUI(tilesetImage, tiles, mapImage, analyzedImage);
    }

    // ---------------------------------------------------------------------------
    // Carga
    // ---------------------------------------------------------------------------

    /**
     * Carga una imagen desde el classpath.
     */
    private static BufferedImage loadImage(String path) {
        try (InputStream is = MapMapperUtility.class.getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("Recurso no encontrado en el classpath: " + path);
                return null;
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            System.err.println("Error al leer la imagen '" + path + "': " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------------------
    // Extracción de tiles
    // ---------------------------------------------------------------------------

    /**
     * Extrae todos los tiles de la imagen del tileset.
     * Cada tile ocupa TILE_SIZE x TILE_SIZE píxeles comenzando en
     * (GRID_OFFSET + col*TILE_STEP, GRID_OFFSET + row*TILE_STEP).
     */
    private static List<TileEntry> extractTiles(BufferedImage src, int cols, int rows) {
        List<TileEntry> result = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = GRID_OFFSET + col * TILE_STEP;
                int y = GRID_OFFSET + row * TILE_STEP;

                BufferedImage tile = src.getSubimage(x, y, TILE_SIZE, TILE_SIZE);
                boolean empty = isTileEmpty(tile);
                int index = row * cols + col;

                result.add(new TileEntry(index, col, row, tile, empty));
                System.out.printf("  Tile [%2d] (%d,%d) -> %s%n",
                        index, col, row, empty ? "VACÍA (amarillo)" : "con contenido");
            }
        }
        return result;
    }

    /**
     * Determina si un tile está vacío comprobando si todos sus píxeles
     * son de color sólido amarillo (R>=200, G>=200, B<=80).
     */
    private static boolean isTileEmpty(BufferedImage tile) {
        for (int py = 0; py < tile.getHeight(); py++) {
            for (int px = 0; px < tile.getWidth(); px++) {
                Color c = new Color(tile.getRGB(px, py), true);
                boolean isYellow = c.getRed() >= 200 && c.getGreen() >= 200 && c.getBlue() <= 80;
                if (!isYellow) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Escala una imagen al factor DISPLAY_SCALE usando interpolación vecino más próximo
     * (para mantener el aspecto pixel-art).
     */
    private static BufferedImage scaleTile(BufferedImage src) {
        int w = src.getWidth()  * DISPLAY_SCALE;
        int h = src.getHeight() * DISPLAY_SCALE;
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    // ---------------------------------------------------------------------------
    // Análisis del mapa
    // ---------------------------------------------------------------------------

    /**
     * Divide el mapa en celdas de TILE_SIZE x TILE_SIZE y para cada celda
     * busca si existe un tile coincidente en el tileset.
     *
     * @return Cuadrícula 2D [fila][col] con el TileEntry encontrado o null si no hay coincidencia.
     */
    private static TileEntry[][] analyzeMap(BufferedImage mapImage, List<TileEntry> tiles) {
        List<TileEntry> knownTiles = tiles.stream()
                .filter(t -> !t.empty)
                .collect(Collectors.toList());

        int mapCols = mapImage.getWidth()  / TILE_SIZE;
        int mapRows = mapImage.getHeight() / TILE_SIZE;
        TileEntry[][] grid = new TileEntry[mapRows][mapCols];

        int matched = 0;
        int missing = 0;

        for (int row = 0; row < mapRows; row++) {
            for (int col = 0; col < mapCols; col++) {
                int x = col * TILE_SIZE;
                int y = row * TILE_SIZE;
                BufferedImage chunk = mapImage.getSubimage(x, y, TILE_SIZE, TILE_SIZE);
                TileEntry match = findMatchingTile(chunk, knownTiles);
                grid[row][col] = match;

                if (match != null) {
                    matched++;
                    System.out.printf("  Celda (%2d,%2d) -> Tile #%02d%n", col, row, match.index);
                } else {
                    missing++;
                    System.out.printf("  Celda (%2d,%2d) -> DESCONOCIDA%n", col, row);
                }
            }
        }

        System.out.println("Celdas reconocidas : " + matched);
        System.out.println("Celdas desconocidas: " + missing);
        System.out.println("Total celdas       : " + (matched + missing));
        return grid;
    }

    /**
     * Busca entre las tiles conocidas la que coincide pixel a pixel con el chunk dado.
     */
    private static TileEntry findMatchingTile(BufferedImage chunk, List<TileEntry> knownTiles) {
        for (TileEntry tile : knownTiles) {
            if (compareTiles(chunk, tile.image)) {
                return tile;
            }
        }
        return null;
    }

    /**
     * Compara dos imágenes pixel a pixel. Devuelve true si son idénticas.
     */
    private static boolean compareTiles(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int py = 0; py < a.getHeight(); py++) {
            for (int px = 0; px < a.getWidth(); px++) {
                if (a.getRGB(px, py) != b.getRGB(px, py)) return false;
            }
        }
        return true;
    }

    /**
     * Construye la imagen del resultado del análisis a escala DISPLAY_SCALE.
     * - Tile reconocida: se dibuja ampliada.
     * - Tile desconocida: cuadro magenta con "?" y borde rojo.
     */
    private static BufferedImage buildAnalyzedImage(TileEntry[][] grid) {
        int mapRows    = grid.length;
        int mapCols    = grid[0].length;
        int tileDisplay = TILE_SIZE * DISPLAY_SCALE;
        int w = mapCols * tileDisplay;
        int h = mapRows * tileDisplay;

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int row = 0; row < mapRows; row++) {
            for (int col = 0; col < mapCols; col++) {
                int dx = col * tileDisplay;
                int dy = row * tileDisplay;
                TileEntry entry = grid[row][col];

                if (entry != null) {
                    // Tile reconocida: dibujar escalada
                    g.drawImage(entry.image, dx, dy, tileDisplay, tileDisplay, null);
                } else {
                    // Tile desconocida: fondo magenta + "?" + borde rojo
                    g.setColor(new Color(220, 0, 220));
                    g.fillRect(dx, dy, tileDisplay, tileDisplay);

                    g.setColor(Color.WHITE);
                    Font font = new Font(Font.MONOSPACED, Font.BOLD, tileDisplay / 2);
                    g.setFont(font);
                    FontMetrics fm = g.getFontMetrics();
                    String text = "?";
                    int tx = dx + (tileDisplay - fm.stringWidth(text)) / 2;
                    int ty = dy + (tileDisplay - fm.getHeight()) / 2 + fm.getAscent();
                    g.drawString(text, tx, ty);

                    g.setColor(Color.RED);
                    g.drawRect(dx, dy, tileDisplay - 1, tileDisplay - 1);
                }
            }
        }
        g.dispose();
        return result;
    }

    // ---------------------------------------------------------------------------
    // Visualización
    // ---------------------------------------------------------------------------

    /**
     * Muestra tres paneles:
     *   - Izquierda:  imagen completa del tileset (escalada).
     *   - Centro:     mapa original (arriba) y mapa analizado (abajo).
     *   - Derecha:    lista con cada tile extraída (solo las que tienen contenido).
     */
    private static void showUI(BufferedImage tilesetImage, List<TileEntry> tiles,
                               BufferedImage mapImage, BufferedImage analyzedImage) {
        JFrame frame = new JFrame("MapMapperUtility - Tileset & Mapa: clear-tiles / clear-map");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- Panel izquierdo: tileset completo ---
        BufferedImage scaledTileset = scaleTile(tilesetImage);
        JLabel tilesetLabel = new JLabel(new ImageIcon(scaledTileset));
        JScrollPane leftScroll = new JScrollPane(tilesetLabel);
        leftScroll.setPreferredSize(new Dimension(scaledTileset.getWidth() + 30, 700));
        leftScroll.setBorder(BorderFactory.createTitledBorder(
                "Tileset: clear-tiles (" + tilesetImage.getWidth() + "x" + tilesetImage.getHeight() + " px, x" + DISPLAY_SCALE + ")"));

        // --- Panel central superior: mapa original ---
        BufferedImage scaledMap = scaleTile(mapImage);
        JLabel mapLabel = new JLabel(new ImageIcon(scaledMap));
        JScrollPane mapScroll = new JScrollPane(mapLabel);
        mapScroll.setBorder(BorderFactory.createTitledBorder(
                "Mapa original: clear-map (" + mapImage.getWidth() + "x" + mapImage.getHeight() + " px, x" + DISPLAY_SCALE + ")"));
        mapScroll.getVerticalScrollBar().setUnitIncrement(16);
        mapScroll.getHorizontalScrollBar().setUnitIncrement(16);

        // --- Panel central inferior: mapa analizado ---
        JLabel analyzedLabel = new JLabel(new ImageIcon(analyzedImage));
        JScrollPane analyzedScroll = new JScrollPane(analyzedLabel);
        analyzedScroll.setBorder(BorderFactory.createTitledBorder(
                "Mapa analizado (magenta=tile desconocida)"));
        analyzedScroll.getVerticalScrollBar().setUnitIncrement(16);
        analyzedScroll.getHorizontalScrollBar().setUnitIncrement(16);

        // Sincronizar scroll horizontal/vertical entre original y analizado
        mapScroll.getHorizontalScrollBar().setModel(analyzedScroll.getHorizontalScrollBar().getModel());
        mapScroll.getVerticalScrollBar().setModel(analyzedScroll.getVerticalScrollBar().getModel());

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapScroll, analyzedScroll);
        centerSplit.setResizeWeight(0.5);

        // --- Panel derecho: lista de tiles extraídas ---
        JPanel tilesPanel = new JPanel();
        tilesPanel.setLayout(new BoxLayout(tilesPanel, BoxLayout.Y_AXIS));
        tilesPanel.setBackground(Color.DARK_GRAY);

        for (TileEntry entry : tiles) {
            if (entry.empty) continue;

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            row.setBackground(Color.DARK_GRAY);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_SIZE * DISPLAY_SCALE + 20));

            BufferedImage scaled = scaleTile(entry.image);
            JLabel imgLabel = new JLabel(new ImageIcon(scaled));
            imgLabel.setPreferredSize(new Dimension(scaled.getWidth(), scaled.getHeight()));
            imgLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

            JLabel infoLabel = new JLabel(
                    String.format("Tile #%02d  (col %d, fila %d)", entry.index, entry.col, entry.row));
            infoLabel.setForeground(Color.WHITE);
            infoLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            infoLabel.setHorizontalAlignment(SwingConstants.LEFT);

            row.add(imgLabel);
            row.add(infoLabel);
            tilesPanel.add(row);
        }

        JScrollPane rightScroll = new JScrollPane(tilesPanel);
        rightScroll.setPreferredSize(new Dimension(300, 700));
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        long nonEmpty = tiles.stream().filter(t -> !t.empty).count();
        JLabel rightTitle = new JLabel(" Tiles con contenido (" + nonEmpty + " / " + tiles.size() + ")");
        rightTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(rightTitle, BorderLayout.NORTH);
        rightPanel.add(rightScroll, BorderLayout.CENTER);

        // --- Layout: tileset | [mapa original / mapa analizado] | tiles ---
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightPanel);
        rightSplit.setResizeWeight(0.75);
        JSplitPane mainSplit  = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightSplit);
        mainSplit.setResizeWeight(0.15);

        frame.getContentPane().add(mainSplit, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ---------------------------------------------------------------------------
    // Clases de soporte
    // ---------------------------------------------------------------------------

    /** Representa un tile extraído del tileset. */
    private static class TileEntry {
        final int index;
        final int col;
        final int row;
        final BufferedImage image;
        final boolean empty;

        TileEntry(int index, int col, int row, BufferedImage image, boolean empty) {
            this.index = index;
            this.col   = col;
            this.row   = row;
            this.image = image;
            this.empty = empty;
        }
    }
}
