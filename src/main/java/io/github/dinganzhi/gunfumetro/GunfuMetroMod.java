package io.github.dinganzhi.gunfumetro;

import io.github.dinganzhi.gunfumetro.command.GunfuMetroCommand;
import io.github.dinganzhi.gunfumetro.config.ModClientConfig;
import io.github.dinganzhi.gunfumetro.music.MusicData;
import io.github.dinganzhi.gunfumetro.music.MusicManager;
import io.github.dinganzhi.gunfumetro.screen.MetroMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GunfuMetroMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Gunfu-Metro");
    public static KeyMapping OPEN_MAP_KEY;
    public static KeyMapping TOGGLE_HUD_KEY;
    public static KeyMapping MUSIC_PLAY_KEY;
    public static KeyMapping MUSIC_NEXT_KEY;
    public static KeyMapping MUSIC_PREV_KEY;
    public static KeyMapping LOAD_PLAYLIST_KEY;

    private static boolean textureReady = false;
    private static MetroData.Station nearestStation;
    private static double nearestDistance;
    private static boolean insideStation;
    private static String nearestExitName;
    private static double lastPlayerX, lastPlayerY, lastPlayerZ;

    @Override
    public void onInitializeClient() {
        MetroData.loadAll();
        ModClientConfig.INSTANCE.load();

        OPEN_MAP_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.open", GLFW.GLFW_KEY_G, "category.gunfu-metro.main"));
        TOGGLE_HUD_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.toggle_hud", GLFW.GLFW_KEY_H, "category.gunfu-metro.main"));
        MUSIC_PLAY_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.music_play", GLFW.GLFW_KEY_O, "category.gunfu-metro.music"));
        MUSIC_NEXT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.music_next", GLFW.GLFW_KEY_L, "category.gunfu-metro.music"));
        MUSIC_PREV_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.music_prev", GLFW.GLFW_KEY_K, "category.gunfu-metro.music"));
        LOAD_PLAYLIST_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.gunfu-metro.load_playlist", GLFW.GLFW_KEY_J, "category.gunfu-metro.music"));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            MetroMapTexture.initialize();
            textureReady = true;
        });

        // 退出世界时停止音乐（多种方式确保）
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MusicManager.getInstance().stop();
            LOGGER.info("Stopped music on disconnect");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicManager.getInstance().stop();
            LOGGER.info("Stopped music on client stopping");
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 玩家消失（如退出世界）时停止音乐
            if (client.player == null && MusicManager.getInstance().isPlaying()) {
                MusicManager.getInstance().stop();
                LOGGER.info("Stopped music because player is null");
            }

            while (TOGGLE_HUD_KEY.consumeClick()) {
                ModClientConfig config = ModClientConfig.INSTANCE.instance();
                config.showHud = !config.showHud;
                ModClientConfig.INSTANCE.save();
            }
            while (OPEN_MAP_KEY.consumeClick() && textureReady) {
                Minecraft.getInstance().setScreen(new MetroMapScreen());
            }
            while (LOAD_PLAYLIST_KEY.consumeClick()) {
                // 后台加载，避免网络/IO 阻塞渲染线程
                MusicManager.getInstance().loadPlaylistAsync();
            }
            while (MUSIC_PLAY_KEY.consumeClick()) {
                if (MusicManager.getInstance().isPlaying())
                    MusicManager.getInstance().pause();
                else
                    MusicManager.getInstance().play();
            }
            while (MUSIC_NEXT_KEY.consumeClick())
                MusicManager.getInstance().playNext();
            while (MUSIC_PREV_KEY.consumeClick())
                MusicManager.getInstance().playPrevious();
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            // F1 隐藏界面时同步隐藏模组 HUD
            if (client.options.hideGui)
                return;

            // ---------- 最近站点 HUD（左侧） ----------
            if (ModClientConfig.INSTANCE.instance().showHud) {
                double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
                if (Math.abs(px - lastPlayerX) > 0.1 || Math.abs(py - lastPlayerY) > 0.1
                        || Math.abs(pz - lastPlayerZ) > 0.1) {
                    lastPlayerX = px;
                    lastPlayerY = py;
                    lastPlayerZ = pz;
                    updateNearestStation(px, py, pz);
                }
                if (nearestStation != null) {
                    int x = 10;
                    int y = graphics.guiHeight() / 2 - client.font.lineHeight;
                    int lineHeight = client.font.lineHeight + 2;
                    if (insideStation) {
                        // 站内：在<站点名>内
                        drawOutlinedText(graphics, client.font,
                                Component.translatable("hud.gunfu_metro.inside_station",
                                        MetroData.displayName(nearestStation)), x, y);
                    } else {
                        // 站外：站点名 / 出口名称 / 距离米
                        String name = MetroData.displayName(nearestStation);
                        drawOutlinedText(graphics, client.font, Component.literal(name), x, y);
                        y += lineHeight;
                        String exitName = (nearestExitName == null || nearestExitName.isEmpty())
                                ? (nearestStation.exits.isEmpty() ? null
                                        : Component.translatable("hud.gunfu_metro.exit").getString())
                                : nearestExitName;
                        if (exitName != null) {
                            drawOutlinedText(graphics, client.font, Component.translatable(
                                    "hud.gunfu_metro.exit_name", exitName), x, y);
                            y += lineHeight;
                        }
                        String formatted = String.format("%.1f", nearestDistance);
                        drawOutlinedText(graphics, client.font,
                                Component.translatable("hud.gunfu_metro.distance", formatted), x, y);
                    }
                }
            }

            // ---------- 音乐 HUD（屏幕右侧，垂直居中，无前缀，描边） ----------
            if (ModClientConfig.INSTANCE.instance().showMusicHud) {
                MusicManager music = MusicManager.getInstance();
                MusicData current = music.getCurrentMusic();
                if (current != null && music.isPlaying()) {
                    int screenWidth = graphics.guiWidth();
                    int screenHeight = graphics.guiHeight();
                    var font = client.font;

                    String idLine = current.id;
                    String titleLine = current.title;
                    String authorLine = current.author;
                    long pos = music.getCurrentPosition();
                    long total = music.getTotalLength();
                    String progressLine = String.format("%02d:%02d / %02d:%02d",
                            pos / 60, pos % 60, total / 60, total % 60);

                    int lineHeight = font.lineHeight + 2;
                    int totalHeight = 4 * lineHeight - 2;
                    int rightMargin = 10;
                    int x = screenWidth - rightMargin;
                    int y = (screenHeight - totalHeight) / 2;

                    drawOutlinedTextRight(graphics, font, Component.literal(idLine), x, y);
                    y += lineHeight;
                    drawOutlinedTextRight(graphics, font, Component.literal(titleLine), x, y);
                    y += lineHeight;
                    drawOutlinedTextRight(graphics, font, Component.literal(authorLine), x, y);
                    y += lineHeight;
                    drawOutlinedTextRight(graphics, font, Component.literal(progressLine), x, y);
                }
            }
        });

        GunfuMetroCommand.register();
    }

    private static void drawOutlinedText(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text,
            int x, int y) {
        graphics.drawString(font, text, x - 1, y, 0xFF000000);
        graphics.drawString(font, text, x + 1, y, 0xFF000000);
        graphics.drawString(font, text, x, y - 1, 0xFF000000);
        graphics.drawString(font, text, x, y + 1, 0xFF000000);
        graphics.drawString(font, text, x, y, 0xFFFFFFFF);
    }

    private static void drawOutlinedTextRight(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text,
            int x, int y) {
        int width = font.width(text);
        int drawX = x - width;
        graphics.drawString(font, text, drawX - 1, y, 0xFF000000);
        graphics.drawString(font, text, drawX + 1, y, 0xFF000000);
        graphics.drawString(font, text, drawX, y - 1, 0xFF000000);
        graphics.drawString(font, text, drawX, y + 1, 0xFF000000);
        graphics.drawString(font, text, drawX, y, 0xFFFFFFFF);
    }

    /**
     * 选择要显示的最近车站：
     * 1) 玩家位于某车站站体内部时，直接显示该车站（在车站内）；
     * 2) 否则按「到最近出入口的三维直线距离」（无出入口时以车站中心为参照）选择最近车站。
     * 全程使用平方距离比较，仅在最后开一次根号。
     */
    private static void updateNearestStation(double px, double py, double pz) {
        nearestStation = null;
        insideStation = false;
        nearestExitName = null;
        nearestDistance = 0;

        for (MetroData.Station s : MetroData.STATIONS) {
            if (s.boxes.isEmpty())
                continue;
            if (MetroData.isInside(s, px, py, pz)) {
                nearestStation = s;
                insideStation = true;
                return;
            }
        }

        double minDistSq = Double.MAX_VALUE;
        for (MetroData.Station s : MetroData.STATIONS) {
            if (s.inclusiveBounds == null)
                continue;
            double distSq = Double.MAX_VALUE;
            String bestExit = null;
            if (!s.exits.isEmpty()) {
                for (int i = 0; i < s.exits.size(); i++) {
                    double[] e = s.exits.get(i);
                    double dx = e[0] - px, dy = e[1] - py, dz = e[2] - pz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < distSq) {
                        distSq = d;
                        bestExit = s.exitNames.get(i);
                    }
                }
            }
            if (distSq == Double.MAX_VALUE && s.minPos != null) {
                double dx = s.centerX - px, dy = s.centerY - py, dz = s.centerZ - pz;
                distSq = dx * dx + dy * dy + dz * dz;
            }
            if (distSq < minDistSq) {
                minDistSq = distSq;
                nearestStation = s;
                nearestExitName = bestExit;
            }
        }
        if (nearestStation != null)
            nearestDistance = Math.sqrt(minDistSq);
    }
}