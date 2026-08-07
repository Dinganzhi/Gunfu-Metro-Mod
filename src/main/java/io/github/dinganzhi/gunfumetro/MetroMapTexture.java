package io.github.dinganzhi.gunfumetro;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class MetroMapTexture {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetroMapTexture.class);
    private static final List<DynamicTexture> textures = new ArrayList<>();
    private static final List<ResourceLocation> textureIds = new ArrayList<>();
    private static int textureWidth, textureHeight;
    private static final int TILE_SIZE = 2048;
    private static int tilesX, tilesY;

    private static double mapMinX, mapMaxX, mapMinZ, mapMaxZ;
    private static double scale;
    private static final Map<String, Point> stationTexCoords = new HashMap<>();
    private static BufferedImage fullImage;

    public static List<ResourceLocation> getTextures() {
        return textureIds;
    }

    public static int getTextureWidth() {
        return textureWidth;
    }

    public static int getTextureHeight() {
        return textureHeight;
    }

    public static int getTilesX() {
        return tilesX;
    }

    public static int getTilesY() {
        return tilesY;
    }

    public static Point getStationTexPoint(String stationId) {
        return stationTexCoords.get(stationId);
    }

    public static void initialize() {
        if (!textures.isEmpty())
            return;
        MetroData.loadAll();
        List<MetroData.Station> validStations = new ArrayList<>();
        for (MetroData.Station s : MetroData.STATIONS) {
            if (s.minPos != null && s.maxPos != null)
                validStations.add(s);
        }
        if (validStations.isEmpty())
            return;

        mapMinX = Double.MAX_VALUE;
        mapMaxX = -Double.MAX_VALUE;
        mapMinZ = Double.MAX_VALUE;
        mapMaxZ = -Double.MAX_VALUE;
        for (MetroData.Station s : validStations) {
            if (s.centerX < mapMinX)
                mapMinX = s.centerX;
            if (s.centerX > mapMaxX)
                mapMaxX = s.centerX;
            if (s.centerZ < mapMinZ)
                mapMinZ = s.centerZ;
            if (s.centerZ > mapMaxZ)
                mapMaxZ = s.centerZ;
        }
        // 线路路径点也可能超出站点范围，需要一并纳入地图边界
        for (MetroData.Line line : MetroData.LINES) {
            for (MetroData.RoutePoint p : line.route) {
                if (p.x() < mapMinX)
                    mapMinX = p.x();
                if (p.x() > mapMaxX)
                    mapMaxX = p.x();
                if (p.z() < mapMinZ)
                    mapMinZ = p.z();
                if (p.z() > mapMaxZ)
                    mapMaxZ = p.z();
            }
        }
        // 先固定原尺寸再统一向两侧扩展，避免扩展量被重复计算（每侧 10% 空白）
        double pad = 0.10;
        double padX = (mapMaxX - mapMinX) * pad;
        double padZ = (mapMaxZ - mapMinZ) * pad;
        mapMinX -= padX;
        mapMaxX += padX;
        mapMinZ -= padZ;
        mapMaxZ += padZ;

        double worldW = mapMaxX - mapMinX;
        double worldH = mapMaxZ - mapMinZ;
        if (worldW <= 0 || Double.isInfinite(worldW) || Double.isNaN(worldW))
            worldW = 1;
        if (worldH <= 0 || Double.isInfinite(worldH) || Double.isNaN(worldH))
            worldH = 1;
        scale = 1.0; // 1 像素 = 1 MC 方块（坐标），按 1:1 绘制
        textureWidth = Math.max(1, (int) Math.ceil(worldW * scale));
        textureHeight = Math.max(1, (int) Math.ceil(worldH * scale));
        tilesX = (int) Math.ceil((double) textureWidth / TILE_SIZE);
        tilesY = (int) Math.ceil((double) textureHeight / TILE_SIZE);

        fullImage = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = fullImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setBackground(new Color(0, 0, 0, 0));
        g2d.clearRect(0, 0, textureWidth, textureHeight);

        Map<String, List<MetroData.Station>> lineStations = buildLineStations(validStations);
        for (MetroData.Line line : MetroData.LINES) {
            // 有路径数据则按路径点依次连线绘制（忽略 Y 轴）
            if (line.route.size() >= 2) {
                drawRoutePath(g2d, line, line.colour);
                continue;
            }
            // 没有路径数据时回退为直接连接该线路的站点
            List<MetroData.Station> stations = lineStations.get(line.code);
            if (stations == null || stations.size() < 2)
                continue;
            drawLinePath(g2d, stations, line.colour);
        }
        for (MetroData.Station station : validStations) {
            int sx = worldToTexX(station.centerX);
            int sy = worldToTexY(station.centerZ);
            stationTexCoords.put(station.id, new Point(sx, sy));
            drawStationSymbol(g2d, sx, sy);
        }
        g2d.dispose();

        for (int row = 0; row < tilesY; row++) {
            for (int col = 0; col < tilesX; col++) {
                int xStart = col * TILE_SIZE;
                int yStart = row * TILE_SIZE;
                int tileW = Math.min(TILE_SIZE, textureWidth - xStart);
                int tileH = Math.min(TILE_SIZE, textureHeight - yStart);
                BufferedImage tile = fullImage.getSubimage(xStart, yStart, tileW, tileH);
                // 一次性批量取出 ARGB 像素（避免逐像素 native 调用），再填充 NativeImage
                int[] argb = tile.getRGB(0, 0, tileW, tileH, null, 0, tileW);
                NativeImage ni = new NativeImage(tileW, tileH, false);
                int pixel = 0;
                for (int y = 0; y < tileH; y++) {
                    for (int x = 0; x < tileW; x++) {
                        ni.setPixel(x, y, argb[pixel++]);
                    }
                }
                final int idx = row * tilesX + col;
                DynamicTexture dt = new DynamicTexture(() -> "gunfu_metro_map_" + idx, ni);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gunfu-metro", "map_" + idx);
                Minecraft.getInstance().getTextureManager().register(id, dt);
                textures.add(dt);
                textureIds.add(id);
            }
        }
    }

    public static void regenerate() {
        for (DynamicTexture dt : textures)
            dt.close();
        textures.clear();
        textureIds.clear();
        stationTexCoords.clear();
        fullImage = null;
        tilesX = 0;
        tilesY = 0;
        initialize();
    }

    public static void exportFullImage() {
        if (fullImage == null)
            return;
        try {
            BufferedImage export = new BufferedImage(
                    fullImage.getWidth(), fullImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = export.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.drawImage(fullImage, 0, 0, null);
            g2d.setFont(exportLabelFont());
            for (MetroData.Station station : MetroData.STATIONS) {
                Point p = stationTexCoords.get(station.id);
                if (p == null)
                    continue;
                drawOutlinedLabel(g2d, MetroData.displayName(station), p.x + 6, p.y + 4);
            }
            g2d.dispose();

            Path exportDir = Minecraft.getInstance().gameDirectory.toPath().resolve("gunfu-metro");
            Files.createDirectories(exportDir);
            File out = exportDir.resolve("gunfu_metro_export.png").toFile();
            ImageIO.write(export, "PNG", out);
            openFolder(exportDir.toFile());
        } catch (IOException e) {
            LOGGER.error("Failed to export map image", e);
        }
    }

    /** 优先选择支持中日韩文字的字体，保证导出图上的站名不变成方框。 */
    private static Font exportLabelFont() {
        Set<String> families = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : new String[] { "Microsoft YaHei", "SimHei", "SimSun", "DengXian",
                "PingFang SC", "Noto Sans CJK SC", "Source Han Sans SC" }) {
            if (families.contains(name))
                return new Font(name, Font.PLAIN, 14);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    }

    /** 绘制带黑色描边、白色填充的文本，保证在任意底色上都清晰可读。 */
    private static void drawOutlinedLabel(Graphics2D g2d, String text, int x, int y) {
        g2d.setColor(Color.BLACK);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0)
                    continue;
                g2d.drawString(text, x + dx, y + dy);
            }
        }
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, y);
    }

    private static void openFolder(File folder) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win"))
                new ProcessBuilder("explorer", folder.getAbsolutePath()).start();
            else if (os.contains("mac"))
                new ProcessBuilder("open", folder.getAbsolutePath()).start();
            else
                new ProcessBuilder("xdg-open", folder.getAbsolutePath()).start();
        } catch (IOException e) {
            LOGGER.warn("Failed to open export folder", e);
        }
    }

    private static void drawStationSymbol(Graphics2D g2d, int sx, int sy) {
        g2d.setColor(Color.WHITE);
        g2d.fillRect(sx - 3, sy - 3, 7, 7);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(sx - 3, sy - 3, 6, 6);
        g2d.setColor(new Color(0, 0, 0, 0));
        g2d.fillRect(sx - 3, sy - 3, 1, 1);
        g2d.fillRect(sx + 2, sy - 3, 1, 1);
        g2d.fillRect(sx - 3, sy + 2, 1, 1);
        g2d.fillRect(sx + 2, sy + 2, 1, 1);
    }

    /** 按线路路径点依次连线绘制（忽略 Y 轴，只取 X/Z 投影到地图平面）。 */
    private static void drawRoutePath(Graphics2D g2d, MetroData.Line line, String colourCode) {
        int n = line.route.size();
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            MetroData.RoutePoint p = line.route.get(i);
            xs[i] = worldToTexX(p.x());
            ys[i] = worldToTexY(p.z());
        }
        drawColouredPolyline(g2d, xs, ys, colourCode);
    }

    private static void drawLinePath(Graphics2D g2d, List<MetroData.Station> stations, String colourCode) {
        int[] xs = new int[stations.size()], ys = new int[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            xs[i] = worldToTexX(stations.get(i).centerX);
            ys[i] = worldToTexY(stations.get(i).centerZ);
        }
        drawColouredPolyline(g2d, xs, ys, colourCode);
    }

    private static void drawColouredPolyline(Graphics2D g2d, int[] xs, int[] ys, String colourCode) {
        switch (colourCode) {
            case "!rail":
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawPolyline(g2d, xs, ys);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(
                        new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[] { 8, 8 }, 0));
                drawPolyline(g2d, xs, ys);
                break;
            case "!subrail":
                g2d.setColor(new Color(0x898989));
                g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawPolyline(g2d, xs, ys);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawPolyline(g2d, xs, ys);
                break;
            default:
                Color c = parseColor(colourCode);
                g2d.setColor(c);
                g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawPolyline(g2d, xs, ys);
        }
    }

    private static void drawPolyline(Graphics2D g2d, int[] xs, int[] ys) {
        Path2D path = new Path2D.Float();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++)
            path.lineTo(xs[i], ys[i]);
        g2d.draw(path);
    }

    private static Color parseColor(String hex) {
        if (hex != null && hex.startsWith("#")) {
            try {
                return new Color(Integer.parseInt(hex.substring(1), 16));
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid line colour: '{}'", hex);
            }
        }
        return Color.BLACK;
    }

    private static Map<String, List<MetroData.Station>> buildLineStations(List<MetroData.Station> validStations) {
        Map<String, List<MetroData.Station>> result = new HashMap<>();
        Set<String> validIds = new HashSet<>();
        for (MetroData.Station s : validStations)
            validIds.add(s.id);

        for (MetroData.Line line : MetroData.LINES) {
            List<MetroData.Station> lineStations = new ArrayList<>();
            for (MetroData.Station s : validStations) {
                if (s.lines.contains(line.code))
                    lineStations.add(s);
            }
            if (lineStations.size() < 2) {
                if (!lineStations.isEmpty())
                    result.put(line.code, lineStations);
                continue;
            }

            Map<String, MetroData.Station> idMap = new HashMap<>();
            for (MetroData.Station s : lineStations)
                idMap.put(s.id, s);
            Map<String, List<String>> adj = new HashMap<>();
            for (MetroData.Station s : lineStations) {
                adj.computeIfAbsent(s.id, k -> new ArrayList<>());
                for (MetroData.Connection conn : s.connections) {
                    if (conn.by().equals(line.code) && idMap.containsKey(conn.to())) {
                        adj.get(s.id).add(conn.to());
                    }
                }
            }

            String startId = lineStations.get(0).id;
            for (String id : adj.keySet()) {
                if (adj.get(id).size() == 1) {
                    startId = id;
                    break;
                }
            }

            List<MetroData.Station> ordered = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(startId);
            while (!stack.isEmpty()) {
                String cur = stack.pop();
                if (visited.contains(cur))
                    continue;
                visited.add(cur);
                ordered.add(idMap.get(cur));
                for (String next : adj.getOrDefault(cur, Collections.emptyList())) {
                    if (!visited.contains(next))
                        stack.push(next);
                }
            }
            result.put(line.code, ordered);
        }
        return result;
    }

    private static int worldToTexX(double worldX) {
        return (int) ((worldX - mapMinX) * scale);
    }

    private static int worldToTexY(double worldZ) {
        return (int) ((worldZ - mapMinZ) * scale);
    }
}