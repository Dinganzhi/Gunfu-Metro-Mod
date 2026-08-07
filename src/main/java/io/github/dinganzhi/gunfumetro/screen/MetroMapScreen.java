package io.github.dinganzhi.gunfumetro.screen;

import io.github.dinganzhi.gunfumetro.GunfuMetroMod;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import io.github.dinganzhi.gunfumetro.MetroData;
import io.github.dinganzhi.gunfumetro.MetroMapTexture;
import io.github.dinganzhi.gunfumetro.config.GunfuMetroConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class MetroMapScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetroMapScreen.class);
    private static final int TOP_BAR_HEIGHT = 20;
    private static final int MARGIN = 15;
    private static final int BORDER_COLOR = 0xFFAAAAAA;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 8.0;
    private static final double ZOOM_STEP = 1.2;

    /** 视口左上角对应的纹理坐标（double，允许缩放时亚像素移动） */
    private double panX = 0, panY = 0;
    private double zoom = 1.0;
    private int mapX, mapY, mapWidth, mapHeight;
    private boolean initialized = false;
    private List<MetroData.Station> validStations = new ArrayList<>();

    public MetroMapScreen() {
        super(Component.translatable("gui.gunfu_metro.map"));
    }

    @Override
    protected void init() {
        super.init();
        try {
            if (MetroData.STATIONS.isEmpty()) {
                MetroData.loadAll();
            }
            if (MetroMapTexture.getTextures().isEmpty()) {
                MetroMapTexture.regenerate();
            }
            validStations = new ArrayList<>();
            for (MetroData.Station s : MetroData.STATIONS)
                if (s.minPos != null)
                    validStations.add(s);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize map screen", e);
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.gunfu_metro.export"), btn -> {
            try {
                MetroMapTexture.exportFullImage();
            } catch (Exception e) {
                LOGGER.error("Failed to export map image", e);
            }
        }).pos(width - 110, 2).size(50, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.gunfu_metro.config"), btn -> {
            try {
                Screen screen = GunfuMetroConfigScreen.create(this);
                if (screen != null) {
                    Minecraft.getInstance().setScreen(screen);
                } else {
                    LOGGER.warn("GunfuMetroConfigScreen.create returned null");
                }
            } catch (Exception e) {
                LOGGER.error("Failed to open config screen", e);
            }
        }).pos(width - 50, 2).size(40, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        mapX = MARGIN;
        mapY = TOP_BAR_HEIGHT + 2 + 5;
        mapWidth = width - MARGIN * 2;
        mapHeight = height - mapY - MARGIN;

        graphics.fill(0, 0, width, height, 0xCC000000);
        graphics.fill(0, TOP_BAR_HEIGHT, width, TOP_BAR_HEIGHT + 2, 0xFF555555);
        List<ResourceLocation> textures = MetroMapTexture.getTextures();
        Font font = Minecraft.getInstance().font;
        if (textures.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.gunfu_metro.loading"),
                    width / 2, height / 2 - 10, 0xFFFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("gui.gunfu_metro.loading_hint"),
                    width / 2, height / 2 + 10, 0xAAAAAA);
            if (ModClientConfig.INSTANCE.instance().showTitleBar) {
                graphics.drawString(font, Component.translatable("gui.gunfu_metro.map_title"),
                        10, (TOP_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
            }
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }
        int texW = MetroMapTexture.getTextureWidth(), texH = MetroMapTexture.getTextureHeight();
        int tilesX = MetroMapTexture.getTilesX();
        int tilesY = MetroMapTexture.getTilesY();
        if (!initialized) {
            Point a001 = MetroMapTexture.getStationTexPoint("A001");
            if (a001 != null) {
                panX = a001.x - mapWidth / 2.0;
                panY = a001.y - mapHeight / 2.0;
            } else {
                panX = (texW - mapWidth / zoom) / 2.0;
                panY = (texH - mapHeight / zoom) / 2.0;
            }
            initialized = true;
        }
        clampPan();

        // 启用裁剪到地图区域
        graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);

        // 视口对应的纹理坐标范围（double）
        double vx0 = panX, vy0 = panY;
        double vx1 = panX + mapWidth / zoom;
        double vy1 = panY + mapHeight / zoom;
        int tileSize = 2048;

        // 绘制地图纹理。所有瓦片共用同一坐标变换（screen = round((tex - pan) * zoom) + map），
        // 相邻瓦片的共享边用同一公式取整，保证缩放时切片严丝合缝、不产生缝隙或错位。
        for (int row = 0; row < tilesY; row++) {
            for (int col = 0; col < tilesX; col++) {
                int tileStartX = col * tileSize;
                int tileStartY = row * tileSize;
                int tileW = Math.min(tileSize, texW - tileStartX);
                int tileH = Math.min(tileSize, texH - tileStartY);
                if (tileW <= 0 || tileH <= 0)
                    continue;
                double srcX0 = Math.max(tileStartX, vx0);
                double srcX1 = Math.min(tileStartX + tileW, vx1);
                if (srcX1 <= srcX0)
                    continue;
                double srcY0 = Math.max(tileStartY, vy0);
                double srcY1 = Math.min(tileStartY + tileH, vy1);
                if (srcY1 <= srcY0)
                    continue;
                int drawX = Math.round((float) ((srcX0 - panX) * zoom)) + mapX;
                int drawY = Math.round((float) ((srcY0 - panY) * zoom)) + mapY;
                int drawX2 = Math.round((float) ((srcX1 - panX) * zoom)) + mapX;
                int drawY2 = Math.round((float) ((srcY1 - panY) * zoom)) + mapY;
                int drawW = drawX2 - drawX;
                int drawH = drawY2 - drawY;
                if (drawW <= 0 || drawH <= 0)
                    continue;
                int index = row * tilesX + col;
                graphics.blit(RenderPipelines.GUI_TEXTURED, textures.get(index),
                        drawX, drawY,
                        (float) (srcX0 - tileStartX), (float) (srcY0 - tileStartY),
                        drawW, drawH,
                        (int) (srcX1 - srcX0), (int) (srcY1 - srcY0),
                        tileW, tileH);
            }
        }

        // 绘制站名（按当前语言显示，在裁剪区域内自动裁剪）
        for (MetroData.Station s : validStations) {
            Point pt = MetroMapTexture.getStationTexPoint(s.id);
            if (pt == null)
                continue;
            int sx = Math.round((float) ((pt.x - panX) * zoom)) + mapX;
            int sy = Math.round((float) ((pt.y - panY) * zoom)) + mapY;
            graphics.drawString(font, Component.literal(MetroData.displayName(s)), sx + 2, sy - 9, 0xFFFFFFFF);
        }

        // 禁用裁剪
        graphics.disableScissor();

        // 绘制边框（在裁剪之后，确保边框始终可见）
        graphics.fill(mapX, mapY, mapX + mapWidth, mapY + 1, BORDER_COLOR);
        graphics.fill(mapX, mapY + mapHeight - 1, mapX + mapWidth, mapY + mapHeight, BORDER_COLOR);
        graphics.fill(mapX, mapY, mapX + 1, mapY + mapHeight, BORDER_COLOR);
        graphics.fill(mapX + mapWidth - 1, mapY, mapX + mapWidth, mapY + mapHeight, BORDER_COLOR);

        super.render(graphics, mouseX, mouseY, delta);
        if (ModClientConfig.INSTANCE.instance().showTitleBar) {
            graphics.drawString(font, Component.translatable("gui.gunfu_metro.map_title"),
                    10, (TOP_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            panX -= deltaX / zoom;
            panY -= deltaY / zoom;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0 || MetroMapTexture.getTextures().isEmpty())
            return false;
        double oldZoom = zoom;
        zoom = Mth.clamp(zoom * (verticalAmount > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
        if (zoom == oldZoom)
            return true;
        // 以鼠标位置为缩放中心：保持鼠标所指的纹理坐标不动
        double texX = (mouseX - mapX) / oldZoom + panX;
        double texY = (mouseY - mapY) / oldZoom + panY;
        panX = texX - (mouseX - mapX) / zoom;
        panY = texY - (mouseY - mapY) / zoom;
        clampPan();
        return true;
    }

    /** 将平移限制在地图范围内；地图小于视口时居中显示 */
    private void clampPan() {
        int texW = MetroMapTexture.getTextureWidth();
        int texH = MetroMapTexture.getTextureHeight();
        if (texW <= 0 || texH <= 0)
            return;
        double viewW = mapWidth / zoom;
        double viewH = mapHeight / zoom;
        if (texW <= viewW)
            panX = (texW - viewW) / 2.0;
        else
            panX = Mth.clamp(panX, 0, texW - viewW);
        if (texH <= viewH)
            panY = (texH - viewH) / 2.0;
        else
            panY = Mth.clamp(panY, 0, texH - viewH);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (GunfuMetroMod.OPEN_MAP_KEY.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
