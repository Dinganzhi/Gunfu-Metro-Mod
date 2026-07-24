package io.github.dinganzhi.gunfumetro.screen;

import io.github.dinganzhi.gunfumetro.GunfuMetroMod;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import io.github.dinganzhi.gunfumetro.MetroData;
import io.github.dinganzhi.gunfumetro.MetroMapTexture;
import io.github.dinganzhi.gunfumetro.compat.ModMenuCompat;
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
import java.awt.Point;
import java.util.*;

public class MetroMapScreen extends Screen {
    private double panX = 0, panY = 0;
    private boolean initialized = false;
    private static final int TOP_BAR_HEIGHT = 20;
    private static final int MARGIN = 15;
    private static final int BORDER_COLOR = 0xFFAAAAAA;

    public MetroMapScreen() {
        super(Component.translatable("gui.gunfu_metro.map"));
        System.out.println("[MetroMapScreen] Constructor called");
    }

    @Override
    protected void init() {
        super.init();
        System.out.println("[MetroMapScreen] init() called");
        try {
            if (MetroData.STATIONS.isEmpty()) {
                MetroData.loadAll();
                System.out.println("[MetroMapScreen] MetroData loaded, stations: " + MetroData.STATIONS.size());
            }
            if (MetroMapTexture.getTextures().isEmpty()) {
                MetroMapTexture.regenerate();
                System.out.println("[MetroMapScreen] MetroMapTexture regenerated, textures: " + MetroMapTexture.getTextures().size());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.gunfu_metro.export"), btn -> {
            try {
                MetroMapTexture.exportFullImage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).pos(width - 110, 2).size(50, 16).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.gunfu_metro.config"), btn -> {
            try {
                Screen screen = ModMenuCompat.createConfigScreen(this);
                if (screen != null) {
                    Minecraft.getInstance().setScreen(screen);
                } else {
                    System.err.println("ModMenuCompat.createConfigScreen returned null");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).pos(width - 50, 2).size(40, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        System.out.println("[MetroMapScreen] render() called, textures size: " + MetroMapTexture.getTextures().size());
        int mapX = MARGIN, mapY = TOP_BAR_HEIGHT + 2 + 5;
        int mapWidth = width - MARGIN * 2, mapHeight = height - mapY - MARGIN;

        graphics.fill(0, 0, width, height, 0xCC000000);
        graphics.fill(0, TOP_BAR_HEIGHT, width, TOP_BAR_HEIGHT + 2, 0xFF555555);
        List<ResourceLocation> textures = MetroMapTexture.getTextures();
        if (textures.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, Component.literal("地图数据加载中..."), width/2, height/2 - 10, 0xFFFFFFFF);
            graphics.drawCenteredString(font, Component.literal("请确保资源已生成"), width/2, height/2 + 10, 0xAAAAAA);
            if (ModClientConfig.INSTANCE.instance().showTitleBar) {
                graphics.drawString(font, Component.literal("滚服地铁地图"), 10, (TOP_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
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
                panX = (texW - mapWidth) / 2.0;
                panY = 0;
            }
            initialized = true;
        }
        double maxPanX = Math.max(0, texW - mapWidth);
        double maxPanY = Math.max(0, texH - mapHeight);
        panX = Mth.clamp(panX, 0, maxPanX);
        panY = Mth.clamp(panY, 0, maxPanY);
        int u = (int) panX, v = (int) panY;
        int tileSize = 2048;

        // 启用裁剪到地图区域
        graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);

        // 绘制地图纹理
        for (int row = 0; row < tilesY; row++) {
            for (int col = 0; col < tilesX; col++) {
                int tileStartX = col * tileSize;
                int tileStartY = row * tileSize;
                int tileW = Math.min(tileSize, texW - tileStartX);
                int tileH = Math.min(tileSize, texH - tileStartY);
                int intersectX = Math.max(u, tileStartX);
                int intersectW = Math.min(u + mapWidth, tileStartX + tileW) - intersectX;
                if (intersectW <= 0) continue;
                int intersectY = Math.max(v, tileStartY);
                int intersectH = Math.min(v + mapHeight, tileStartY + tileH) - intersectY;
                if (intersectH <= 0) continue;
                int sliceU = intersectX - tileStartX;
                int sliceV = intersectY - tileStartY;
                int drawX = mapX + (intersectX - u);
                int drawY = mapY + (intersectY - v);
                int index = row * tilesX + col;
                graphics.blit(RenderPipelines.GUI_TEXTURED, textures.get(index),
                        drawX, drawY, sliceU, sliceV, intersectW, intersectH, tileW, tileH);
            }
        }

        // 绘制站名（在裁剪区域内，超出边框自动裁剪）
        List<MetroData.Station> validStations = new ArrayList<>();
        for (MetroData.Station s : MetroData.STATIONS) if (s.minPos != null) validStations.add(s);
        for (MetroData.Station s : validStations) {
            Point pt = MetroMapTexture.getStationTexPoint(s.id);
            if (pt == null) continue;
            int sx = mapX + (pt.x - u);
            int sy = mapY + (pt.y - v);
            graphics.drawString(Minecraft.getInstance().font, Component.literal(s.nameZh), sx + 2, sy - 9, 0xFFFFFFFF);
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
            Font font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.literal("滚服地铁地图"), 10, (TOP_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) { panX -= deltaX; panY -= deltaY; return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (GunfuMetroMod.OPEN_MAP_KEY.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        System.out.println("[MetroMapScreen] onClose() called");
        super.onClose();
    }

    @Override
    public void removed() {
        System.out.println("[MetroMapScreen] removed() called");
        super.removed();
    }
}