package io.github.dinganzhi.gunfumetro;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.InputStream;
import java.util.*;

public class MetroData {
    public static final List<Line> LINES = new ArrayList<>();
    public static final List<Station> STATIONS = new ArrayList<>();
    public static final Map<String, Line> LINE_MAP = new HashMap<>();
    public static final Map<String, Station> STATION_MAP = new HashMap<>();

    public static void loadAll() {
        if (!LINES.isEmpty())
            return; // 只加载一次
        try {
            loadLines();
            loadStations();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadLines() throws Exception {
        InputStream is = MetroData.class.getResourceAsStream("/assets/gunfu-metro/data/lines.xml");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        NodeList nodes = doc.getElementsByTagName("line");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            Line line = new Line();
            line.code = getText(e, "code");
            line.nameZh = getText(e, "name", "zh-Hans");
            line.nameEn = getText(e, "name", "en");
            line.colour = getText(e, "colour");
            LINES.add(line);
            LINE_MAP.put(line.code, line);
        }
        is.close();
    }

    private static void loadStations() throws Exception {
        InputStream is = MetroData.class.getResourceAsStream("/assets/gunfu-metro/data/stations.xml");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        NodeList nodes = doc.getElementsByTagName("station");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            Station station = new Station();
            station.id = getText(e, "id");
            station.nameZh = getText(e, "name", "zh-Hans");
            station.nameEn = getText(e, "name", "en");
            String lineStr = getText(e, "line");
            if (lineStr != null && !lineStr.isEmpty()) {
                station.lines = Arrays.asList(lineStr.split("[,;]"));
            }
            // 解析坐标
            String locStr = getText(e, "location");
            if (locStr != null) {
                String[] parts = locStr.split(";");
                if (parts.length == 2) {
                    station.minPos = parseVec3(parts[0]);
                    station.maxPos = parseVec3(parts[1]);
                    station.centerX = (station.minPos[0] + station.maxPos[0]) / 2.0;
                    station.centerZ = (station.minPos[2] + station.maxPos[2]) / 2.0;
                }
            }
            // 连接
            NodeList connNodes = e.getElementsByTagName("connection");
            for (int j = 0; j < connNodes.getLength(); j++) {
                Element conn = (Element) connNodes.item(j);
                String to = conn.getAttribute("to");
                int dist = Integer.parseInt(conn.getAttribute("distance"));
                String by = conn.getAttribute("by");
                station.connections.add(new Connection(to, dist, by));
            }
            STATIONS.add(station);
            STATION_MAP.put(station.id, station);
        }
        is.close();
    }

    private static String getText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() > 0)
            return list.item(0).getTextContent().trim();
        return null;
    }

    private static String getText(Element parent, String tag, String lang) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Element nameElem = (Element) list.item(i);
            NodeList langNodes = nameElem.getElementsByTagName(lang);
            if (langNodes.getLength() > 0)
                return langNodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private static double[] parseVec3(String str) {
        String[] parts = str.split(",");
        return new double[] {
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2])
        };
    }

    // 数据类
    public static class Line {
        public String code;
        public String nameZh;
        public String nameEn;
        public String colour;
    }

    public static class Station {
        public String id;
        public String nameZh;
        public String nameEn;
        public List<String> lines = new ArrayList<>();
        public List<Connection> connections = new ArrayList<>();
        public double centerX, centerZ;
        public double[] minPos, maxPos;
    }

    public record Connection(String to, int distance, String by) {
    }
}