package es.noa.rad;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
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

    /**
     * Número de colores de la paleta a la que se cuantizarán los píxeles antes
     * de comparar. Ajustar según el tileset: 4 para clear-tiles.png.
     */
    private static final int COLOR_COUNT = 4;

    /** Directorio fuente donde se guardan los tiles con nombre (persistencia). */
    private static final String SAVED_TILES_SRC  = "src/main/resources/assets/map/world/tile";
    /** Directorio compilado (classpath) donde también se copian para uso inmediato. */
    private static final String SAVED_TILES_BIN  = "target/classes/assets/map/world/tile";

    /** Paleta global calculada a partir del tileset. */
    private static int[] globalPalette;

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

        // Construir paleta ANTES de detectar duplicados (necesaria para cuantizar)
        globalPalette = buildPalette(tiles, COLOR_COUNT);
        System.out.println("Paleta (" + COLOR_COUNT + " colores):");
        for (int i = 0; i < globalPalette.length; i++) {
            int rgb = globalPalette[i];
            System.out.printf("  Color #%d -> R=%3d G=%3d B=%3d  (#%06X)%n",
                    i + 1, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, rgb & 0xFFFFFF);
        }

        // Detectar tiles duplicadas en el tileset (se visualizarán en la UI)
        java.util.Set<Integer> duplicateIndices = detectDuplicateTiles(tiles);

        // Cargar tiles guardadas (nombres registrados)
        List<TileEntry> savedTiles = loadSavedTiles();
        System.out.println("Tiles guardadas cargadas: " + savedTiles.size());
        for (TileEntry st : savedTiles) {
            System.out.printf("  -> \"%s\"%n", st.name);
        }

        // Deduplicar: si una tile guardada coincide pixel a pixel (cuantizado)
        // con alguna tile del tileset, eliminar la del tileset de la lista y
        // conservar únicamente la guardada (que tiene nombre asignado).
        for (TileEntry saved : savedTiles) {
            BufferedImage qSaved = quantizeImage(saved.image, globalPalette);
            tiles.removeIf(t -> !t.empty && compareTiles(quantizeImage(t.image, globalPalette), qSaved));
        }
        int removedCount = (int) tiles.stream().filter(t -> !t.empty).count();
        tiles.addAll(savedTiles);
        System.out.println("Tiles en lista tras deduplicar: " + tiles.stream().filter(t -> !t.empty).count()
                + "  (tileset: " + removedCount + " + guardadas: " + savedTiles.size() + ")");

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
        List<BufferedImage> unknownChunks = new ArrayList<>();
        TileEntry[][] grid = analyzeMap(mapImage, tiles, unknownChunks);
        BufferedImage analyzedImage = buildAnalyzedImage(grid);

        showUI(tilesetImage, tiles, mapImage, analyzedImage, unknownChunks, duplicateIndices, grid);
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
    // Detección de duplicados en el tileset
    // ---------------------------------------------------------------------------

    /**
     * Compara todas las tiles no-vacías del tileset entre sí (usando cuantización).
     * Devuelve el conjunto de índices de tiles que tienen al menos un duplicado.
     * Los duplicados se muestran visualmente en la UI (sin popup de alerta).
     */
    private static java.util.Set<Integer> detectDuplicateTiles(List<TileEntry> tiles) {
        List<TileEntry> nonEmpty = tiles.stream()
                .filter(t -> !t.empty)
                .collect(Collectors.toList());

        // Pre-cuantizar todas las tiles para la comparación
        List<BufferedImage> quantized = new ArrayList<>();
        for (TileEntry te : nonEmpty) {
            quantized.add(quantizeImage(te.image, globalPalette));
        }

        java.util.Set<Integer> duplicateIndices = new java.util.HashSet<>();
        for (int i = 0; i < nonEmpty.size(); i++) {
            for (int j = i + 1; j < nonEmpty.size(); j++) {
                if (compareTiles(quantized.get(i), quantized.get(j))) {
                    TileEntry a = nonEmpty.get(i);
                    TileEntry b = nonEmpty.get(j);
                    duplicateIndices.add(a.index);
                    duplicateIndices.add(b.index);
                    System.out.printf("⚠ Duplicado: Tile #%02d [col=%d, row=%d]  ==  Tile #%02d [col=%d, row=%d]%n",
                            a.index, a.col, a.row, b.index, b.col, b.row);
                }
            }
        }

        if (!duplicateIndices.isEmpty()) {
            System.out.println("⚠ TOTAL TILES CON DUPLICADOS EN TILESET: " + duplicateIndices.size());
        } else {
            System.out.println("✔ No se encontraron tiles duplicadas en el tileset.");
        }
        return duplicateIndices;
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
    // Tiles guardadas (persistencia en disco)
    // ---------------------------------------------------------------------------

    /**
     * Carga todas las tiles guardadas en el directorio fuente.
     * Cada fichero PNG cuyo nombre (sin extensión) se usa como nombre de la tile.
     */
    private static List<TileEntry> loadSavedTiles() {
        List<TileEntry> result = new ArrayList<>();
        File dir = new File(SAVED_TILES_SRC);
        if (!dir.exists() || !dir.isDirectory()) return result;

        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null) return result;

        int startIndex = 1000; // índices altos para no colisionar con el tileset
        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) continue;
                String name = f.getName().substring(0, f.getName().length() - 4); // quitar .png
                result.add(new TileEntry(startIndex++, -1, -1, img, false, name));
            } catch (IOException e) {
                System.err.println("Error al cargar tile guardada: " + f.getName() + " -> " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Guarda una tile como PNG en el directorio fuente Y en el directorio compilado.
     * @return true si se guardó correctamente en ambos directorios.
     */
    private static boolean saveTileToFile(BufferedImage img, String name) {
        String filename = name.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".png";
        boolean ok = true;

        for (String dirPath : new String[]{ SAVED_TILES_SRC, SAVED_TILES_BIN }) {
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);
            try {
                ImageIO.write(img, "PNG", out);
                System.out.println("Tile guardada en: " + out.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Error al guardar tile en " + dirPath + ": " + e.getMessage());
                ok = false;
            }
        }
        return ok;
    }

    /**
     * Muestra el diálogo "Guardar Como" para asignar nombre y persistir una tile.
     *
     * @param owner  ventana padre
     * @param img    imagen de la tile (16×16)
     * @param label  etiqueta descriptiva mostrada en el diálogo
     */
    private static void showSaveTileDialog(JFrame owner, BufferedImage img, String label) {
        final int PREV_SCALE = 10; // preview a ×10

        javax.swing.JDialog dlg = new javax.swing.JDialog(owner, "Guardar tile: " + label, true);
        dlg.setLayout(new BorderLayout(12, 12));
        dlg.getContentPane().setBackground(new Color(25, 30, 45));

        // --- Preview de la tile ---
        BufferedImage preview = scaleNearest(img, PREV_SCALE);
        JLabel previewLabel = new JLabel(new ImageIcon(preview));
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.CYAN, 2));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // --- Paleta de colores presentes en esta tile ---
        java.util.LinkedHashSet<Integer> presentColors = new java.util.LinkedHashSet<>();
        if (globalPalette != null) {
            // usar colores cuantizados para ser coherente
            BufferedImage qImg = quantizeImage(img, globalPalette);
            for (int py = 0; py < qImg.getHeight(); py++)
                for (int px = 0; px < qImg.getWidth(); px++)
                    presentColors.add(qImg.getRGB(px, py) & 0x00FFFFFF);
        }

        JPanel palettePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        palettePanel.setBackground(new Color(25, 30, 45));
        JLabel palLbl = new JLabel("Colores:");
        palLbl.setForeground(new Color(180, 200, 255));
        palLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        palettePanel.add(palLbl);
        for (int c : presentColors) {
            JPanel swatch = new JPanel();
            swatch.setBackground(new Color(c));
            swatch.setPreferredSize(new Dimension(24, 24));
            swatch.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
            swatch.setToolTipText(String.format("#%06X  R=%d G=%d B=%d",
                    c, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF));
            palettePanel.add(swatch);
        }

        // --- Campo de nombre ---
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        namePanel.setBackground(new Color(25, 30, 45));
        JLabel nameLbl = new JLabel("Nombre:");
        nameLbl.setForeground(new Color(180, 200, 255));
        nameLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        JTextField nameField = new JTextField(18);
        nameField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        nameField.setBackground(new Color(40, 45, 65));
        nameField.setForeground(Color.WHITE);
        nameField.setCaretColor(Color.WHITE);
        nameField.setBorder(BorderFactory.createLineBorder(new Color(80, 100, 160), 1));
        namePanel.add(nameLbl);
        namePanel.add(nameField);

        // --- Centro: preview + paleta + nombre ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(25, 30, 45));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 4, 16));

        JLabel titleLbl = new JLabel(label, SwingConstants.CENTER);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleLbl.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        previewLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        palettePanel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        namePanel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        centerPanel.add(titleLbl);
        centerPanel.add(javax.swing.Box.createVerticalStrut(8));
        centerPanel.add(previewLabel);
        centerPanel.add(javax.swing.Box.createVerticalStrut(10));
        centerPanel.add(palettePanel);
        centerPanel.add(javax.swing.Box.createVerticalStrut(4));
        centerPanel.add(namePanel);

        // --- Botones ---
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        btnPanel.setBackground(new Color(25, 30, 45));
        javax.swing.JButton btnSave = new javax.swing.JButton("Guardar");
        javax.swing.JButton btnCancel = new javax.swing.JButton("Cancelar");

        btnSave.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Introduce un nombre para la tile.",
                        "Nombre vacío", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (saveTileToFile(img, name)) {
                JOptionPane.showMessageDialog(dlg,
                        "Tile guardada como \"" + name + "\".\nEstará disponible al reiniciar la utilidad.",
                        "Guardado", JOptionPane.INFORMATION_MESSAGE);
                dlg.dispose();
            } else {
                JOptionPane.showMessageDialog(dlg, "Error al guardar la tile. Revisa la consola.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Guardar también con Enter en el campo de nombre
        nameField.addActionListener(e -> btnSave.doClick());

        btnCancel.addActionListener(e -> dlg.dispose());
        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);

        dlg.add(centerPanel, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        nameField.requestFocusInWindow();
        dlg.setVisible(true);
    }

    // ---------------------------------------------------------------------------
    // Cuantización de color
    // ---------------------------------------------------------------------------

    /**
     * Extrae los {@code colorCount} colores más frecuentes de todas las tiles
     * no vacías del tileset. La paleta resultante se usa para normalizar los
     * píxeles antes de cualquier comparación, eliminando variaciones mínimas
     * debidas a la compresión PNG.
     */
    private static int[] buildPalette(List<TileEntry> tiles, int colorCount) {
        java.util.Map<Integer, Integer> freq = new java.util.HashMap<>();
        for (TileEntry te : tiles) {
            if (te.empty) continue;
            for (int py = 0; py < te.image.getHeight(); py++) {
                for (int px = 0; px < te.image.getWidth(); px++) {
                    int rgb = te.image.getRGB(px, py) & 0x00FFFFFF; // sin canal alpha
                    freq.merge(rgb, 1, Integer::sum);
                }
            }
        }
        return freq.entrySet().stream()
                .sorted(java.util.Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(colorCount)
                .mapToInt(java.util.Map.Entry::getKey)
                .toArray();
    }

    /**
     * Mapea un valor RGB al color más cercano de la paleta
     * (distancia euclídea en espacio RGB).
     */
    private static int quantizePixel(int rgb, int[] palette) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b =  rgb        & 0xFF;
        int best     = palette[0];
        int bestDist = Integer.MAX_VALUE;
        for (int p : palette) {
            int dr = r - ((p >> 16) & 0xFF);
            int dg = g - ((p >> 8)  & 0xFF);
            int db = b - ( p        & 0xFF);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) { bestDist = dist; best = p; }
        }
        return best | 0xFF000000; // opaco
    }

    /**
     * Devuelve una nueva imagen donde cada píxel ha sido cuantizado
     * al color más cercano de la paleta dada.
     */
    private static BufferedImage quantizeImage(BufferedImage src, int[] palette) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++)
            for (int px = 0; px < w; px++)
                out.setRGB(px, py, quantizePixel(src.getRGB(px, py) & 0x00FFFFFF, palette));
        return out;
    }

    // ---------------------------------------------------------------------------
    // Análisis del mapa
    // ---------------------------------------------------------------------------

    /**
     * Divide el mapa en celdas de TILE_SIZE x TILE_SIZE y para cada celda
     * busca si existe un tile coincidente en el tileset.
     *
     * @param unknownChunks Lista que se rellena con los chunks no reconocidos (sin duplicados).
     * @return Cuadrícula 2D [fila][col] con el TileEntry encontrado o null si no hay coincidencia.
     */
    private static TileEntry[][] analyzeMap(BufferedImage mapImage, List<TileEntry> tiles,
                                            List<BufferedImage> unknownChunks) {
        List<TileEntry> knownTiles = tiles.stream()
                .filter(t -> !t.empty)
                .collect(Collectors.toList());

        // Pre-cuantizar las imágenes de las tiles conocidas
        List<BufferedImage> qKnown = knownTiles.stream()
                .map(t -> quantizeImage(t.image, globalPalette))
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
                BufferedImage chunk  = mapImage.getSubimage(x, y, TILE_SIZE, TILE_SIZE);
                BufferedImage qChunk = quantizeImage(chunk, globalPalette);

                // Buscar coincidencia contra las tiles cuantizadas
                TileEntry match = null;
                for (int k = 0; k < qKnown.size(); k++) {
                    if (compareTiles(qChunk, qKnown.get(k))) {
                        match = knownTiles.get(k);
                        break;
                    }
                }
                grid[row][col] = match;

                if (match != null) {
                    matched++;
                    System.out.printf("  Celda (%2d,%2d) -> Tile #%02d%n", col, row, match.index);
                } else {
                    missing++;
                    System.out.printf("  Celda (%2d,%2d) -> DESCONOCIDA%n", col, row);
                    // Añadir a desconocidos solo si no hay ya uno igual (comparar cuantizados)
                    boolean alreadyPresent = unknownChunks.stream()
                            .anyMatch(uc -> compareTiles(quantizeImage(uc, globalPalette), qChunk));
                    if (!alreadyPresent) {
                        // Guardar el chunk cuantizado para representación consistente
                        unknownChunks.add(qChunk);
                    }
                }
            }
        }

        System.out.println("Celdas reconocidas : " + matched);
        System.out.println("Celdas desconocidas: " + missing);
        System.out.println("Chunks únicos desconocidos: " + unknownChunks.size());
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
     *   - Derecha:    lista tiles conocidas + lista chunks desconocidos.
     */
    private static void showUI(BufferedImage tilesetImage, List<TileEntry> tiles,
                               BufferedImage mapImage, BufferedImage analyzedImage,
                               List<BufferedImage> unknownChunks,
                               java.util.Set<Integer> duplicateIndices,
                               TileEntry[][] grid) {
        JFrame frame = new JFrame("MapMapperUtility - Tileset & Mapa: clear-tiles / clear-map");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- Panel izquierdo: tileset completo ---
        // Escalamos el tileset y luego marcamos los tiles duplicados con un borde naranja + "⚠"
        BufferedImage scaledTileset = scaleTile(tilesetImage);
        if (!duplicateIndices.isEmpty()) {
            Graphics2D gts = scaledTileset.createGraphics();
            gts.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ts = TILE_SIZE * DISPLAY_SCALE;   // tamaño de tile escalado (px en la imagen)
            int step = TILE_STEP * DISPLAY_SCALE; // paso escalado
            int off  = GRID_OFFSET * DISPLAY_SCALE; // offset escalado
            Font warnFont = new Font(Font.SANS_SERIF, Font.BOLD, ts / 2);
            gts.setFont(warnFont);
            FontMetrics wfm = gts.getFontMetrics();
            for (TileEntry te : tiles) {
                if (te.empty || !duplicateIndices.contains(te.index)) continue;
                int dx = off + te.col * step;
                int dy = off + te.row * step;
                // Relleno semitransparente naranja
                gts.setColor(new Color(255, 140, 0, 80));
                gts.fillRect(dx, dy, ts, ts);
                // Borde naranja sólido
                gts.setColor(new Color(255, 160, 0));
                gts.setStroke(new java.awt.BasicStroke(2f));
                gts.drawRect(dx, dy, ts - 1, ts - 1);
                // Símbolo ⚠ en esquina superior-izquierda
                String sym = "!";
                gts.setColor(new Color(255, 220, 0));
                gts.drawString(sym, dx + 2, dy + wfm.getAscent());
            }
            gts.dispose();
        }
        JLabel tilesetLabel = new JLabel(new ImageIcon(scaledTileset));
        JScrollPane leftScroll = new JScrollPane(tilesetLabel);
        leftScroll.setPreferredSize(new Dimension(scaledTileset.getWidth() + 30, 700));
        String dupInfo = duplicateIndices.isEmpty() ? "" : "  ⚠ " + duplicateIndices.size() + " tile(s) duplicada(s)";
        leftScroll.setBorder(BorderFactory.createTitledBorder(
                "Tileset: clear-tiles (" + tilesetImage.getWidth() + "x" + tilesetImage.getHeight() + " px, x" + DISPLAY_SCALE + ")" + dupInfo));

        // --- Panel central superior: mapa original ---
        BufferedImage scaledMap = scaleTile(mapImage);
        JLabel mapLabel = new JLabel(new ImageIcon(scaledMap));
        JScrollPane mapScroll = new JScrollPane(mapLabel);
        mapScroll.setBorder(BorderFactory.createTitledBorder(
                "Mapa original: clear-map (" + mapImage.getWidth() + "x" + mapImage.getHeight() + " px, x" + DISPLAY_SCALE + ")"));
        mapScroll.getVerticalScrollBar().setUnitIncrement(16);
        mapScroll.getHorizontalScrollBar().setUnitIncrement(16);

        // --- Panel central inferior: mapa analizado (con herramienta de selección) ---
        SelectableMapPanel analyzedPanel = new SelectableMapPanel(
                analyzedImage, DISPLAY_SCALE, frame,
                () -> saveAnalyzedImage(analyzedImage),
                () -> saveUnknownTiles(unknownChunks, frame));
        JScrollPane analyzedScroll = new JScrollPane(analyzedPanel);
        analyzedScroll.setBorder(BorderFactory.createTitledBorder(
                "Mapa analizado — arrastrar: selección · clic der. fuera: limpiar · clic der. dentro: exportar"));
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

            boolean isDuplicate = duplicateIndices.contains(entry.index);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            Color rowBg = isDuplicate ? new Color(70, 35, 0) : Color.DARK_GRAY;
            row.setBackground(rowBg);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_SIZE * DISPLAY_SCALE + 20));

            BufferedImage scaled = scaleTile(entry.image);
            JLabel imgLabel = new JLabel(new ImageIcon(scaled));
            imgLabel.setPreferredSize(new Dimension(scaled.getWidth(), scaled.getHeight()));
            imgLabel.setBorder(isDuplicate
                    ? BorderFactory.createLineBorder(new Color(255, 160, 0), 2)
                    : BorderFactory.createLineBorder(Color.GRAY, 1));

            // Etiqueta: mostrar nombre si es una tile guardada, si no las coords
            String infoText = (entry.name != null)
                    ? String.format("✔ \"%s\"", entry.name)
                    : String.format("Tile #%02d  (col %d, fila %d)", entry.index, entry.col, entry.row);
            if (isDuplicate) infoText = "⚠ " + infoText;

            JLabel infoLabel = new JLabel(infoText);
            Color textColor = entry.name != null
                    ? new Color(100, 220, 130)
                    : (isDuplicate ? new Color(255, 180, 60) : Color.WHITE);
            infoLabel.setForeground(textColor);
            infoLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            infoLabel.setHorizontalAlignment(SwingConstants.LEFT);

            // Menú contextual: Guardar Como
            if (entry.name == null) { // solo tiles del tileset sin nombre asignado
                JPopupMenu popupTile = new JPopupMenu();
                JMenuItem saveItem = new JMenuItem("Guardar Como...");
                String dialogLabel = String.format("Tile #%02d  [col %d, fila %d]", entry.index, entry.col, entry.row);
                saveItem.addActionListener(e -> showSaveTileDialog(frame, entry.image, dialogLabel));
                popupTile.add(saveItem);
                imgLabel.setComponentPopupMenu(popupTile);
                row.setComponentPopupMenu(popupTile);
                infoLabel.setComponentPopupMenu(popupTile);
            }

            row.add(imgLabel);
            row.add(infoLabel);
            tilesPanel.add(row);
        }

        JScrollPane rightScroll = new JScrollPane(tilesPanel);
        rightScroll.getVerticalScrollBar().setUnitIncrement(16);

        long nonEmpty = tiles.stream().filter(t -> !t.empty).count();
        JLabel rightTitle = new JLabel(" Tiles conocidas (" + nonEmpty + " / " + tiles.size() + ")");
        rightTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        // --- Segunda lista: chunks desconocidos ---
        JPanel unknownPanel = new JPanel();
        unknownPanel.setLayout(new BoxLayout(unknownPanel, BoxLayout.Y_AXIS));
        unknownPanel.setBackground(new Color(50, 10, 10));

        for (int i = 0; i < unknownChunks.size(); i++) {
            final BufferedImage uc = unknownChunks.get(i);
            final int idx = i;
            JPanel urow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            urow.setBackground(new Color(50, 10, 10));
            urow.setMaximumSize(new Dimension(Integer.MAX_VALUE, TILE_SIZE * DISPLAY_SCALE + 20));

            BufferedImage scaled = scaleTile(uc);
            JLabel imgLabel = new JLabel(new ImageIcon(scaled));
            imgLabel.setPreferredSize(new Dimension(scaled.getWidth(), scaled.getHeight()));
            imgLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 1));

            JLabel infoLabel = new JLabel(String.format("Desconocido #%02d", i + 1));
            infoLabel.setForeground(new Color(255, 120, 120));
            infoLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

            // Menú contextual: comparar con tile del tileset + guardar como
            JPopupMenu popup = new JPopupMenu();
            JMenuItem compareItem = new JMenuItem("Comparar con tile del tileset...");
            compareItem.addActionListener(e -> showTileSelector(frame, uc, idx + 1, tiles));
            JMenuItem saveItem = new JMenuItem("Guardar Como...");
            String saveLabel = String.format("Desconocido #%02d", idx + 1);
            saveItem.addActionListener(e -> showSaveTileDialog(frame, uc, saveLabel));
            popup.add(compareItem);
            popup.addSeparator();
            popup.add(saveItem);

            imgLabel.setComponentPopupMenu(popup);
            urow.setComponentPopupMenu(popup);
            infoLabel.setComponentPopupMenu(popup);

            urow.add(imgLabel);
            urow.add(infoLabel);
            unknownPanel.add(urow);
        }

        JScrollPane unknownScroll = new JScrollPane(unknownPanel);
        unknownScroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel unknownTitle = new JLabel(" Chunks desconocidos únicos (" + unknownChunks.size() + ")");
        unknownTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        unknownTitle.setForeground(new Color(255, 80, 80));

        // Juntar ambas listas en el panel derecho con split vertical
        JPanel knownPanel = new JPanel(new BorderLayout());
        knownPanel.add(rightTitle, BorderLayout.NORTH);
        knownPanel.add(rightScroll, BorderLayout.CENTER);

        JPanel unknownWrapper = new JPanel(new BorderLayout());
        unknownWrapper.add(unknownTitle, BorderLayout.NORTH);
        unknownWrapper.add(unknownScroll, BorderLayout.CENTER);

        JSplitPane listSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, knownPanel, unknownWrapper);
        listSplit.setResizeWeight(0.5);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(280, 700));
        rightPanel.add(listSplit, BorderLayout.CENTER);

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
    // Exportación desde el mapa analizado
    // ---------------------------------------------------------------------------

    /**
     * Guarda la imagen analizada junto al fichero del mapa original.
     * Deriva la carpeta destino a partir de MAP_PATH, buscando tanto en
     * src/main/resources como en target/classes.
     */
    private static void saveAnalyzedImage(BufferedImage analyzedImage) {
        // MAP_PATH = "/assets/map/world/clear-map.png"
        String relDir  = MAP_PATH.substring(1, MAP_PATH.lastIndexOf('/') + 1); // "assets/map/world/"
        String baseName = MAP_PATH.substring(MAP_PATH.lastIndexOf('/') + 1,
                MAP_PATH.lastIndexOf('.'));                                      // "clear-map"
        String filename = baseName + "-analyzed.png";

        String[] dirs = { "src/main/resources/" + relDir, "target/classes/" + relDir };
        boolean saved = false;
        for (String dirPath : dirs) {
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);
            try {
                ImageIO.write(analyzedImage, "PNG", out);
                System.out.println("Imagen analizada guardada en: " + out.getAbsolutePath());
                saved = true;
            } catch (IOException e) {
                System.err.println("Error al guardar imagen analizada en " + dirPath + ": " + e.getMessage());
            }
        }
        if (saved) {
            JOptionPane.showMessageDialog(null,
                    "Imagen analizada guardada como:\n" + filename,
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(null,
                    "No se pudo guardar la imagen analizada.\nRevisa la consola.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Guarda cada chunk desconocido como PNG en la carpeta 'unknown' del tileset.
     * Las rutas derivan de TILESET_PATH:
     *   src/main/resources/assets/map/world/tileset/unknown/
     *   target/classes/assets/map/world/tileset/unknown/
     */
    private static void saveUnknownTiles(List<BufferedImage> unknownChunks, JFrame owner) {
        if (unknownChunks.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                    "No hay tiles desconocidas para guardar.",
                    "Sin tiles desconocidas", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // TILESET_PATH = "/assets/map/world/tileset/clear-tiles.png"
        String relDir = TILESET_PATH.substring(1, TILESET_PATH.lastIndexOf('/') + 1); // "assets/map/world/tileset/"
        String[] dirs = {
                "src/main/resources/" + relDir + "unknown",
                "target/classes/"     + relDir + "unknown"
        };

        int savedCount = 0;
        for (int i = 0; i < unknownChunks.size(); i++) {
            String filename = String.format("unknown_%02d.png", i + 1);
            for (String dirPath : dirs) {
                File dir = new File(dirPath);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, filename);
                try {
                    ImageIO.write(unknownChunks.get(i), "PNG", out);
                    System.out.println("Tile desconocida guardada: " + out.getAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Error al guardar " + filename + " en " + dirPath + ": " + e.getMessage());
                }
            }
            savedCount++;
        }
        JOptionPane.showMessageDialog(owner,
                "Se guardaron " + savedCount + " tile(s) desconocida(s) en:\n"
                + new File("src/main/resources/" + relDir + "unknown").getAbsolutePath(),
                "Tiles desconocidas guardadas", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------------------------------------------------------------------------
    // Herramienta de selección sobre el mapa analizado
    // ---------------------------------------------------------------------------

    /**
     * Panel que muestra la imagen del mapa analizado y permite:
     * - Arrastrar (botón izquierdo) para crear/mover/redimensionar una selección.
     * - Clic derecho fuera de la selección: descarta la selección.
     * - Clic derecho dentro de la selección: menú con "Exportar selección" +
     *   opciones de guardado global.
     */
    private static class SelectableMapPanel extends JPanel {

        private static final int HR = 5;  // radio (half-size) de los tiradores en px
        private static final Color SEL_FILL    = new Color(100, 180, 255, 55);
        private static final Color SEL_BORDER  = new Color(80, 160, 255);
        private static final Color HANDLE_FILL = Color.WHITE;
        private static final Color HANDLE_EDGE = new Color(30, 90, 200);

        private final BufferedImage displayImage;
        private final int scale;
        private final JFrame owner;
        private final Runnable onSaveAnalyzed;
        private final Runnable onSaveUnknowns;
        private final TileEntry[][] grid;

        /** Selección en coordenadas del panel (imagen escalada). */
        private Rectangle sel = null;

        // ---- estado arrastre ----
        private enum DragMode { NONE, NEW, MOVE, RESIZE }
        private DragMode dragMode    = DragMode.NONE;
        private Point    dragAnchor  = null;
        private Rectangle selSnap   = null;  // copia de sel al inicio del drag
        private int resizeHandleIdx  = -1;   // 0=NW 1=N 2=NE 3=E 4=SE 5=S 6=SW 7=W

        SelectableMapPanel(BufferedImage displayImage, int scale, JFrame owner,
                           Runnable onSaveAnalyzed, Runnable onSaveUnknowns,
                           TileEntry[][] grid) {
            this.displayImage   = displayImage;
            this.scale          = scale;
            this.owner          = owner;
            this.onSaveAnalyzed = onSaveAnalyzed;
            this.onSaveUnknowns = onSaveUnknowns;
            this.grid           = grid;
            setPreferredSize(new Dimension(displayImage.getWidth(), displayImage.getHeight()));
            setOpaque(true);
            installMouse();
        }

        // ---- instalación de listeners ----------------------------------------

        private void installMouse() {
            java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    Point p = e.getPoint();
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                        handleRightClick(p);
                        return;
                    }
                    // Botón izquierdo
                    if (sel != null) {
                        int h = hitHandle(p);
                        if (h >= 0) {
                            dragMode       = DragMode.RESIZE;
                            resizeHandleIdx = h;
                            selSnap        = new Rectangle(sel);
                            dragAnchor     = p;
                            return;
                        }
                        if (sel.contains(p)) {
                            dragMode   = DragMode.MOVE;
                            selSnap    = new Rectangle(sel);
                            dragAnchor = p;
                            return;
                        }
                    }
                    // Nueva selección
                    dragMode   = DragMode.NEW;
                    dragAnchor = p;
                    sel        = null;
                    repaint();
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    Point p = e.getPoint();
                    switch (dragMode) {
                        case NEW -> {
                            int x1 = snap(Math.min(dragAnchor.x, p.x));
                            int y1 = snap(Math.min(dragAnchor.y, p.y));
                            int x2 = snap(Math.max(dragAnchor.x, p.x));
                            int y2 = snap(Math.max(dragAnchor.y, p.y));
                            sel = new Rectangle(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
                            repaint();
                        }
                        case MOVE -> {
                            int dx = p.x - dragAnchor.x;
                            int dy = p.y - dragAnchor.y;
                            // Snap de la esquina TL al grid más cercano
                            int newX = snap(selSnap.x + dx);
                            int newY = snap(selSnap.y + dy);
                            sel = new Rectangle(newX, newY, selSnap.width, selSnap.height);
                            clampSel();
                            repaint();
                        }
                        case RESIZE -> {
                            applyResize(p);
                            repaint();
                        }
                        default -> {}
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                        handleRightClick(e.getPoint());
                        return;
                    }
                    // Descartar selección menor que 1 tile en cualquier dimensión
                    if (dragMode == DragMode.NEW && sel != null
                            && (sel.width < tilePx() || sel.height < tilePx())) {
                        sel = null;
                        repaint();
                    }
                    dragMode = DragMode.NONE;
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        // ---- clic derecho -------------------------------------------------------

        private void handleRightClick(Point p) {
            if (sel != null && sel.contains(p)) {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem exportItem = new JMenuItem("Exportar selección...");
                exportItem.addActionListener(ev -> exportSelection());
                menu.add(exportItem);
                menu.addSeparator();
                JMenuItem saveAnalyzed = new JMenuItem("Guardar imagen analizada...");
                saveAnalyzed.addActionListener(ev -> onSaveAnalyzed.run());
                JMenuItem saveUnknown  = new JMenuItem("Guardar tiles desconocidas en 'unknown'...");
                saveUnknown.addActionListener(ev -> onSaveUnknowns.run());
                menu.add(saveAnalyzed);
                menu.add(saveUnknown);
                menu.show(this, p.x, p.y);
            } else {
                sel = null;
                repaint();
            }
        }

        // ---- export -------------------------------------------------------------

        private void exportSelection() {
            if (sel == null) return;
            Rectangle clamped = sel.intersection(
                    new Rectangle(0, 0, displayImage.getWidth(), displayImage.getHeight()));
            if (clamped.width <= 0 || clamped.height <= 0) return;

            // Recortar la imagen escalada
            BufferedImage crop = displayImage.getSubimage(
                    clamped.x, clamped.y, clamped.width, clamped.height);
            // Copia para no retener referencias a subimage
            BufferedImage out = new BufferedImage(
                    crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D gex = out.createGraphics();
            gex.drawImage(crop, 0, 0, null);
            gex.dispose();

            // Nombre de fichero con coordenadas en tiles
            int tx = clamped.x / scale, ty = clamped.y / scale;
            int tw = clamped.width / scale, th = clamped.height / scale;
            String filename = String.format("map-selection_%d_%d_%dx%d.png", tx, ty, tw, th);

            String relDir = MAP_PATH.substring(1, MAP_PATH.lastIndexOf('/') + 1);
            String[] dirs = { "src/main/resources/" + relDir, "target/classes/" + relDir };
            boolean saved = false;
            for (String dirPath : dirs) {
                File dir = new File(dirPath);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, filename);
                try {
                    ImageIO.write(out, "PNG", f);
                    System.out.println("Selección exportada: " + f.getAbsolutePath());
                    saved = true;
                } catch (IOException ex) {
                    System.err.println("Error al exportar selección: " + ex.getMessage());
                }
            }
            if (saved) {
                JOptionPane.showMessageDialog(owner,
                        "Selección exportada como:\n" + filename
                        + "\n\nPosición: (" + tx + ", " + ty + ")  Tamaño: " + tw + " × " + th + " tiles",
                        "Selección exportada", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(owner,
                        "Error al exportar la selección. Revisa la consola.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // ---- lógica de tiradores ------------------------------------------------

        /** Devuelve los 8 centros de tiradores para la selección actual. */
        private Point[] handlePoints() {
            if (sel == null) return new Point[0];
            int x = sel.x, y = sel.y, w = sel.width, h = sel.height;
            int mx = x + w / 2, my = y + h / 2;
            return new Point[]{
                new Point(x,     y),      // 0 NW
                new Point(mx,    y),      // 1 N
                new Point(x + w, y),      // 2 NE
                new Point(x + w, my),     // 3 E
                new Point(x + w, y + h),  // 4 SE
                new Point(mx,    y + h),  // 5 S
                new Point(x,     y + h),  // 6 SW
                new Point(x,     my),     // 7 W
            };
        }

        /** Índice del tirador bajo el punto p, o -1. */
        private int hitHandle(Point p) {
            Point[] pts = handlePoints();
            for (int i = 0; i < pts.length; i++) {
                if (Math.abs(p.x - pts[i].x) <= HR + 3
                        && Math.abs(p.y - pts[i].y) <= HR + 3) return i;
            }
            return -1;
        }

        /** Píxeles por tile en la imagen escalada. */
        private int tilePx() { return TILE_SIZE * scale; }

        /**
         * Redondea una coordenada de panel al límite de tile más cercano.
         * Regla >50%: si la fracción sobrante es ≥ 0.5 tiles, incluye esa tile.
         */
        private int snap(int coord) {
            int tp = tilePx();
            return (int) Math.round((double) coord / tp) * tp;
        }

        private void applyResize(Point p) {
            int x1 = selSnap.x, y1 = selSnap.y;
            int x2 = selSnap.x + selSnap.width, y2 = selSnap.y + selSnap.height;
            switch (resizeHandleIdx) {
                case 0 -> { x1 = snap(p.x); y1 = snap(p.y); }   // NW
                case 1 ->   y1 = snap(p.y);                       // N
                case 2 -> { x2 = snap(p.x); y1 = snap(p.y); }    // NE
                case 3 ->   x2 = snap(p.x);                       // E
                case 4 -> { x2 = snap(p.x); y2 = snap(p.y); }    // SE
                case 5 ->   y2 = snap(p.y);                       // S
                case 6 -> { x1 = snap(p.x); y2 = snap(p.y); }    // SW
                case 7 ->   x1 = snap(p.x);                       // W
            }
            sel = new Rectangle(Math.min(x1, x2), Math.min(y1, y2),
                    Math.max(tilePx(), Math.abs(x2 - x1)), Math.max(tilePx(), Math.abs(y2 - y1)));
        }

        private void clampSel() {
            if (sel == null) return;
            int imgW = displayImage.getWidth(), imgH = displayImage.getHeight();
            int x = Math.max(0, sel.x), y = Math.max(0, sel.y);
            int w = Math.min(sel.width,  imgW - x);
            int h = Math.min(sel.height, imgH - y);
            sel = new Rectangle(x, y, Math.max(1, w), Math.max(1, h));
        }

        // ---- pintado ------------------------------------------------------------

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(displayImage, 0, 0, null);
            if (sel == null || sel.width < 1 || sel.height < 1) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // Relleno semitransparente
            g2.setColor(SEL_FILL);
            g2.fillRect(sel.x, sel.y, sel.width, sel.height);

            // Borde discontinuo
            g2.setColor(SEL_BORDER);
            float[] dash = {6f, 3f};
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.drawRect(sel.x, sel.y, sel.width, sel.height);

            // Tiradores (cuadrados blancos con borde azul)
            g2.setStroke(new BasicStroke(1.5f));
            for (Point hp : handlePoints()) {
                g2.setColor(HANDLE_FILL);
                g2.fillRect(hp.x - HR, hp.y - HR, HR * 2, HR * 2);
                g2.setColor(HANDLE_EDGE);
                g2.drawRect(hp.x - HR, hp.y - HR, HR * 2, HR * 2);
            }

            // Etiqueta de dimensiones en tiles
            int tileW = sel.width  / tilePx();
            int tileH = sel.height / tilePx();
            if (tileW < 1) tileW = 1;
            if (tileH < 1) tileH = 1;
            String dim = tileW + " × " + tileH + " tiles";
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int tx = sel.x + 4, ty = sel.y + fm.getAscent() + 2;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(tx - 2, ty - fm.getAscent() - 1,
                    fm.stringWidth(dim) + 4, fm.getHeight() + 2, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(dim, tx, ty);
        }
    }

    // ---------------------------------------------------------------------------
    // Clases de soporte
    // ---------------------------------------------------------------------------

    /**
     * Abre una ventana modal para seleccionar un tile del tileset y compararlo
     * con el chunk desconocido.
     */
    private static void showTileSelector(JFrame owner, BufferedImage unknownChunk,
                                         int chunkNumber, List<TileEntry> tiles) {
        javax.swing.JDialog dialog = new javax.swing.JDialog(owner,
                "Selecciona tile para comparar con Desconocido #" + String.format("%02d", chunkNumber), true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getContentPane().setBackground(new Color(30, 30, 50));

        JLabel hint = new JLabel("  Haz doble clic o pulsa Enter sobre una tile para comparar:");
        hint.setForeground(new Color(180, 200, 255));
        hint.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        dialog.add(hint, BorderLayout.NORTH);

        // Panel con las tiles conocidas (no vacías)
        List<TileEntry> nonEmpty = tiles.stream()
                .filter(t -> !t.empty)
                .collect(java.util.stream.Collectors.toList());

        int cols = 9;
        int rows = (int) Math.ceil((double) nonEmpty.size() / cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 4, 4));
        grid.setBackground(new Color(30, 30, 50));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        final TileEntry[] selection = {null};

        for (TileEntry te : nonEmpty) {
            BufferedImage sc = scaleTile(te.image);
            JLabel lbl = new JLabel(new ImageIcon(sc));
            lbl.setToolTipText("Tile #" + te.index + "  [" + te.col + "," + te.row + "]");
            lbl.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 120), 1));
            lbl.setBackground(new Color(40, 40, 60));
            lbl.setOpaque(true);

            lbl.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    // Resaltar selección
                    for (java.awt.Component c : grid.getComponents()) {
                        if (c instanceof JLabel) {
                            ((JLabel) c).setBorder(BorderFactory.createLineBorder(new Color(80, 80, 120), 1));
                            ((JLabel) c).setBackground(new Color(40, 40, 60));
                        }
                    }
                    lbl.setBorder(BorderFactory.createLineBorder(Color.CYAN, 2));
                    lbl.setBackground(new Color(0, 60, 80));
                    selection[0] = te;

                    if (e.getClickCount() >= 2) {
                        dialog.dispose();
                        showDiffWindow(owner, unknownChunk, chunkNumber, te);
                    }
                }
            });

            grid.add(lbl);
        }

        JScrollPane sp = new JScrollPane(grid);
        sp.setPreferredSize(new Dimension(460, 320));
        dialog.add(sp, BorderLayout.CENTER);

        // Botones
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(new Color(30, 30, 50));
        javax.swing.JButton btnOk = new javax.swing.JButton("Comparar");
        btnOk.addActionListener(e -> {
            if (selection[0] == null) {
                JOptionPane.showMessageDialog(dialog, "Selecciona primero una tile.",
                        "Sin selección", JOptionPane.WARNING_MESSAGE);
                return;
            }
            dialog.dispose();
            showDiffWindow(owner, unknownChunk, chunkNumber, selection[0]);
        });
        javax.swing.JButton btnCancel = new javax.swing.JButton("Cancelar");
        btnCancel.addActionListener(e -> dialog.dispose());
        btnPanel.add(btnOk);
        btnPanel.add(btnCancel);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Abre una ventana que muestra en 3 columnas:
     * chunk desconocido | imagen de diferencias | tile seleccionada
     */
    private static void showDiffWindow(JFrame owner, BufferedImage unknownChunk,
                                       int chunkNumber, TileEntry selected) {
        final int DIFF_SCALE = 8;

        javax.swing.JDialog diff = new javax.swing.JDialog(owner,
                "Comparación: Desconocido #" + String.format("%02d", chunkNumber)
                + "  vs  Tile #" + selected.index
                + " [" + selected.col + "," + selected.row + "]",
                false);
        diff.setLayout(new BorderLayout(8, 8));
        diff.getContentPane().setBackground(new Color(20, 20, 30));

        BufferedImage diffImg = buildDiffImage(unknownChunk, selected.image);

        // Escalar ×DIFF_SCALE para poder ver cada píxel
        BufferedImage unknownBig = scaleNearest(unknownChunk, DIFF_SCALE);
        BufferedImage diffBig    = scaleNearest(diffImg,      DIFF_SCALE);
        BufferedImage selectedBig = scaleNearest(selected.image, DIFF_SCALE);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        center.setBackground(new Color(20, 20, 30));

        center.add(makeLabeledPanel("Desconocido #" + String.format("%02d", chunkNumber),
                unknownBig, Color.RED));
        center.add(makeLabeledPanel("Diferencias (rojo=distinto, gris=igual)",
                diffBig, Color.YELLOW));
        center.add(makeLabeledPanel("Tile #" + selected.index
                + " [" + selected.col + "," + selected.row + "]",
                selectedBig, Color.CYAN));

        // Leyenda de diferencias
        int diffPixels = countDiffPixels(unknownChunk, selected.image);
        int total      = TILE_SIZE * TILE_SIZE;
        double pct     = 100.0 * diffPixels / total;
        JLabel legend = new JLabel(String.format(
                "  Píxeles diferentes: %d / %d  (%.1f%%)  —  %s",
                diffPixels, total, pct,
                diffPixels == 0 ? "✓ IDÉNTICOS" : "✗ NO COINCIDEN"),
                SwingConstants.CENTER);
        legend.setForeground(diffPixels == 0 ? Color.GREEN : new Color(255, 180, 80));
        legend.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));

        diff.add(center, BorderLayout.CENTER);
        diff.add(legend, BorderLayout.SOUTH);
        diff.pack();
        diff.setLocationRelativeTo(owner);
        diff.setVisible(true);
    }

    /** Crea un JPanel etiquetado con título + imagen escalada. */
    private static JPanel makeLabeledPanel(String title, BufferedImage img, Color borderColor) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBackground(new Color(20, 20, 30));
        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setForeground(new Color(200, 200, 220));
        titleLbl.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        JLabel imgLbl = new JLabel(new ImageIcon(img));
        imgLbl.setBorder(BorderFactory.createLineBorder(borderColor, 2));
        p.add(titleLbl, BorderLayout.NORTH);
        p.add(imgLbl, BorderLayout.CENTER);
        return p;
    }

    /**
     * Construye la imagen de diferencias entre dos tiles del mismo tamaño.
     * Ambas imágenes se cuantizan antes de comparar para evitar falsos positivos
     * por variaciones mínimas de compresión.
     * Píxel igual → gris oscuro (60,60,60), píxel distinto → rojo vivo.
     */
    private static BufferedImage buildDiffImage(BufferedImage a, BufferedImage b) {
        int w = a.getWidth();
        int h = a.getHeight();
        BufferedImage qa  = quantizeImage(a, globalPalette);
        BufferedImage qb  = quantizeImage(b, globalPalette);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgbA = qa.getRGB(x, y);
                int rgbB = qb.getRGB(x, y);
                out.setRGB(x, y, (rgbA == rgbB)
                        ? new Color(60, 60, 60).getRGB()
                        : new Color(255, 40, 40).getRGB());
            }
        }
        return out;
    }

    /** Cuenta el número de píxeles distintos entre dos tiles (usando paleta global). */
    private static int countDiffPixels(BufferedImage a, BufferedImage b) {
        BufferedImage qa = quantizeImage(a, globalPalette);
        BufferedImage qb = quantizeImage(b, globalPalette);
        int count = 0;
        for (int y = 0; y < qa.getHeight(); y++)
            for (int x = 0; x < qa.getWidth(); x++)
                if (qa.getRGB(x, y) != qb.getRGB(x, y)) count++;
        return count;
    }

    /**
     * Escala una imagen con factor entero usando interpolación nearest-neighbor.
     * A diferencia de scaleTile(), usa el factor recibido por parámetro.
     */
    private static BufferedImage scaleNearest(BufferedImage src, int factor) {
        int w = src.getWidth()  * factor;
        int h = src.getHeight() * factor;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Representa un tile extraído del tileset o cargado desde disco. */
    private static class TileEntry {
        final int index;
        final int col;
        final int row;
        final BufferedImage image;
        final boolean empty;
        String name; // null = tile del tileset sin nombre asignado

        TileEntry(int index, int col, int row, BufferedImage image, boolean empty) {
            this.index = index;
            this.col   = col;
            this.row   = row;
            this.image = image;
            this.empty = empty;
            this.name  = null;
        }

        TileEntry(int index, int col, int row, BufferedImage image, boolean empty, String name) {
            this(index, col, row, image, empty);
            this.name = name;
        }
    }
}
