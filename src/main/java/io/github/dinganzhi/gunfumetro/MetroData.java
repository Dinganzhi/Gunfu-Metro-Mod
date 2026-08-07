package io.github.dinganzhi.gunfumetro;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetroData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetroData.class);
    private static final Gson GSON = new Gson();

    /** 每次加载后整体替换为不可变快照，保证跨线程读取安全 */
    public static List<Line> LINES = List.of();
    public static List<Station> STATIONS = List.of();
    public static Map<String, Line> LINE_MAP = Map.of();
    public static Map<String, Station> STATION_MAP = Map.of();

    private static boolean loaded = false;

    public static void loadAll() {
        if (loaded)
            return; // 只加载一次
        reload();
    }

    /** 强制重新从资源文件加载全部数据（供 reload 命令 / 重新生成使用） */
    public static void reload() {
        try {
            List<Line> lines = loadLines();
            List<Station> stations = loadStations();
            Map<String, Line> lineMap = new HashMap<>();
            Map<String, Station> stationMap = new HashMap<>();
            for (Line line : lines)
                lineMap.put(line.code, line);
            for (Station station : stations)
                stationMap.put(station.id, station);
            // 全部加载成功后再原子替换，避免并发读取到半加载状态
            LINES = Collections.unmodifiableList(lines);
            STATIONS = Collections.unmodifiableList(stations);
            LINE_MAP = Collections.unmodifiableMap(lineMap);
            STATION_MAP = Collections.unmodifiableMap(stationMap);
            loaded = true;
            LOGGER.info("Loaded {} lines and {} stations.", lines.size(), stations.size());
        } catch (Exception e) {
            loaded = false;
            LOGGER.error("Failed to load metro data", e);
        }
    }

    private static List<Line> loadLines() throws Exception {
        try (InputStream is = MetroData.class.getResourceAsStream("/assets/gunfu-metro/data/lines.json")) {
            if (is == null)
                throw new IllegalStateException("lines.json not found in resources");
            LineJson[] entries = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), LineJson[].class);
            List<Line> result = new ArrayList<>(entries.length);
            for (LineJson entry : entries) {
                if (entry.code == null || entry.code.isBlank())
                    continue;
                Line line = new Line();
                line.code = entry.code;
                line.nameZh = nameOf(entry.name, "zh-Hans");
                line.nameZhHant = nameOf(entry.name, "zh-Hant");
                line.nameEn = nameOf(entry.name, "en");
                line.colour = entry.colour;
                if (entry.route != null) {
                    for (RoutePointJson rp : entry.route) {
                        line.route.add(new RoutePoint(rp.x, rp.y, rp.z));
                    }
                }
                result.add(line);
            }
            return result;
        }
    }

    private static List<Station> loadStations() throws Exception {
        try (InputStream is = MetroData.class.getResourceAsStream("/assets/gunfu-metro/data/stations.json")) {
            if (is == null)
                throw new IllegalStateException("stations.json not found in resources");
            StationJson[] entries = GSON.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), StationJson[].class);
            List<Station> result = new ArrayList<>(entries.length);
            for (StationJson entry : entries) {
                if (entry.id == null || entry.id.isBlank())
                    continue;
                Station station = new Station();
                station.id = entry.id;
                station.nameZh = nameOf(entry.name, "zh-Hans");
                station.nameZhHant = nameOf(entry.name, "zh-Hant");
                station.nameEn = nameOf(entry.name, "en");
                if (entry.line != null)
                    station.lines = new ArrayList<>(entry.line);
                if (entry.connections != null) {
                    for (ConnectionJson conn : entry.connections) {
                        station.connections.add(new Connection(conn.to, conn.distance, conn.by));
                    }
                }
                applyExits(station, entry.exit);
                applyLocation(station, entry.location);
                computeInclusiveBounds(station);
                result.add(station);
            }
            return result;
        }
    }

    private static String nameOf(JsonObject name, String lang) {
        if (name == null || !name.has(lang))
            return null;
        return name.get(lang).getAsString();
    }

    /**
     * 将 location 每两个点当作一对对角顶点构成一个长方体（世界坐标 AABB），
     * 多个长方体组成车站范围。并对每个坐标轴做 min/max 规范化。
     */
    private static void applyLocation(Station station, List<LocationJson> location) {
        if (location == null || location.size() < 2)
            return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < location.size(); i += 2) {
            LocationJson a = location.get(i);
            LocationJson b = location.get(i + 1);
            if (a == null || b == null)
                continue;
            double[] box = new double[6];
            box[0] = Math.min(a.x, b.x);
            box[3] = Math.max(a.x, b.x);
            box[1] = Math.min(a.y, b.y);
            box[4] = Math.max(a.y, b.y);
            box[2] = Math.min(a.z, b.z);
            box[5] = Math.max(a.z, b.z);
            station.boxes.add(box);
            minX = Math.min(minX, box[0]);
            maxX = Math.max(maxX, box[3]);
            minY = Math.min(minY, box[1]);
            maxY = Math.max(maxY, box[4]);
            minZ = Math.min(minZ, box[2]);
            maxZ = Math.max(maxZ, box[5]);
        }
        if (station.boxes.isEmpty()) {
            LOGGER.warn("Station {}: no valid location pairs, skipped", station.id);
            return;
        }
        if (location.size() % 2 != 0)
            LOGGER.warn("Station {}: odd number of location points, the last one is ignored", station.id);
        station.minPos = new double[] { minX, minY, minZ };
        station.maxPos = new double[] { maxX, maxY, maxZ };
        station.centerX = (minX + maxX) / 2.0;
        station.centerY = (minY + maxY) / 2.0;
        station.centerZ = (minZ + maxZ) / 2.0;
    }

    /** 解析出入口。坐标缺失/为 null 的条目会被跳过并记日志，不使解析崩溃。 */
    private static void applyExits(Station station, List<ExitJson> exit) {
        if (exit == null)
            return;
        for (ExitJson e : exit) {
            if (e == null) {
                LOGGER.warn("Station {}: null exit entry ignored", station.id);
                continue;
            }
            station.exitNames.add(e.name == null ? "" : e.name);
            station.exits.add(new double[] { e.x, e.y, e.z });
        }
    }

    /**
     * 计算车站整体包围盒（车站长方体 + 全部出入口），
     * 保证任意出入口到玩家的距离都不小于该包围盒到玩家的距离，用于最近站选择的剪枝。
     */
    private static void computeInclusiveBounds(Station station) {
        if (station.minPos == null && station.exits.isEmpty())
            return;
        double minX = station.minPos == null ? Double.MAX_VALUE : station.minPos[0];
        double minY = station.minPos == null ? Double.MAX_VALUE : station.minPos[1];
        double minZ = station.minPos == null ? Double.MAX_VALUE : station.minPos[2];
        double maxX = station.maxPos == null ? -Double.MAX_VALUE : station.maxPos[0];
        double maxY = station.maxPos == null ? -Double.MAX_VALUE : station.maxPos[1];
        double maxZ = station.maxPos == null ? -Double.MAX_VALUE : station.maxPos[2];
        for (double[] e : station.exits) {
            minX = Math.min(minX, e[0]);
            maxX = Math.max(maxX, e[0]);
            minY = Math.min(minY, e[1]);
            maxY = Math.max(maxY, e[1]);
            minZ = Math.min(minZ, e[2]);
            maxZ = Math.max(maxZ, e[2]);
        }
        station.inclusiveBounds = new double[] { minX, minY, minZ, maxX, maxY, maxZ };
    }

    /** 判断点是否位于车站任意一个站体长方体内部 */
    public static boolean isInside(Station station, double x, double y, double z) {
        for (double[] box : station.boxes) {
            if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5])
                return true;
        }
        return false;
    }

    /** 点到 AABB 的平方距离（点在内部时为 0） */
    public static double distToAABBSq(double x, double y, double z,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dy = Math.max(0, Math.max(minY - y, y - maxY));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 根据当前游戏语言返回车站显示名：
     * 简体中文（zh_cn）用 zh-Hans，繁体中文（zh_tw/zh_hk/zh_hant）用 zh-Hant，英文及其它语言一律用 en。
     * 所选语言名称缺失时按 zh-Hans → zh-Hant → en → id 依次回退。
     */
    public static String displayName(Station station) {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        String name;
        if (isSimplifiedChinese(code))
            name = station.nameZh;
        else if (isTraditionalChinese(code))
            name = station.nameZhHant != null ? station.nameZhHant : station.nameZh;
        else
            name = station.nameEn;
        if (name == null || name.isBlank())
            name = station.nameZh;
        if (name == null || name.isBlank())
            name = station.nameZhHant != null ? station.nameZhHant : station.nameZh;
        if (name == null || name.isBlank())
            name = station.nameEn;
        if (name == null || name.isBlank())
            name = station.id;
        return name;
    }

    /** 繁体中文语言码：zh-tw / zh-hk，以及资源系统可能的 zhhant */
    public static boolean isTraditionalChinese(String code) {
        return "zh_tw".equals(code) || "zh_hk".equals(code) || "zhhant".equals(code);
    }

    private static boolean isSimplifiedChinese(String code) {
        return "zh_cn".equals(code) || "zhhans".equals(code);
    }

    // Gson 数据类（与 JSON 文件结构一一对应）
    private static class LineJson {
        String code;
        JsonObject name;
        String colour;
        List<RoutePointJson> route;
    }

    private static class RoutePointJson {
        double x, y, z;
    }

    private static class StationJson {
        String id;
        JsonObject name;
        List<String> line;
        List<ConnectionJson> connections;
        List<LocationJson> location;
        List<ExitJson> exit;
    }

    private static class ConnectionJson {
        String to;
        int distance;
        String by;
    }

    private static class LocationJson {
        double x, y, z;
    }

    private static class ExitJson {
        String name;
        double x, y, z;
    }

    // 对外数据类
    public static class Line {
        public String code;
        public String nameZh;
        public String nameZhHant;
        public String nameEn;
        public String colour;
        public List<RoutePoint> route = new ArrayList<>();
    }

    /** 线路路径上的一个点（x/y/z 为世界坐标，绘制地图时忽略 Y 轴） */
    public record RoutePoint(double x, double y, double z) {
    }

    public static class Station {
        public String id;
        public String nameZh;
        public String nameZhHant;
        public String nameEn;
        public List<String> lines = new ArrayList<>();
        public List<Connection> connections = new ArrayList<>();
        /** 车站范围：location 每两个点为一对对角顶点构成的长方体（{minX,minY,minZ,maxX,maxY,maxZ}） */
        public List<double[]> boxes = new ArrayList<>();
        /** 出入口坐标（{x,y,z}），与 exitNames 一一对应 */
        public List<double[]> exits = new ArrayList<>();
        public List<String> exitNames = new ArrayList<>();
        /** 所有长方体的整体边界（{minX,minY,minZ,maxX,maxY,maxZ}） */
        public double[] minPos, maxPos;
        /** 车站长方体 + 全部出入口的整体包围盒，用于最近站选择剪枝 */
        public double[] inclusiveBounds;
        public double centerX, centerY, centerZ;
    }

    public record Connection(String to, int distance, String by) {
    }
}
