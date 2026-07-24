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

public class GunfuMetroMod implements ClientModInitializer {
    public static KeyMapping OPEN_MAP_KEY;
    public static KeyMapping TOGGLE_HUD_KEY;
    public static KeyMapping MUSIC_PLAY_KEY;
    public static KeyMapping MUSIC_NEXT_KEY;
    public static KeyMapping MUSIC_PREV_KEY;
    public static KeyMapping LOAD_PLAYLIST_KEY;

    private static boolean textureReady = false;
    private static MetroData.Station nearestStation;
    private static double nearestDistance;
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
            System.out.println("[GunfuMetro] Stopped music on disconnect");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicManager.getInstance().stop();
            System.out.println("[GunfuMetro] Stopped music on client stopping");
        });

        // 额外检测：在 tick 中检查玩家是否为空
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null && MusicManager.getInstance().isPlaying()) {
                MusicManager.getInstance().stop();
                System.out.println("[GunfuMetro] Stopped music because player is null");
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_HUD_KEY.consumeClick()) {
                ModClientConfig config = ModClientConfig.INSTANCE.instance();
                config.showHud = !config.showHud;
                ModClientConfig.INSTANCE.save();
            }
            while (OPEN_MAP_KEY.consumeClick() && textureReady) {
                Minecraft.getInstance().setScreen(new MetroMapScreen());
            }
            while (LOAD_PLAYLIST_KEY.consumeClick()) {
                MusicManager.getInstance().loadPlaylist();
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

            // ---------- 最近站点 HUD（左侧） ----------
            if (ModClientConfig.INSTANCE.instance().showHud) {
                double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
                if (Math.abs(px - lastPlayerX) > 0.1 || Math.abs(py - lastPlayerY) > 0.1
                        || Math.abs(pz - lastPlayerZ) > 0.1) {
                    lastPlayerX = px;
                    lastPlayerY = py;
                    lastPlayerZ = pz;
                    nearestStation = null;
                    double minDist = Double.MAX_VALUE;
                    for (MetroData.Station s : MetroData.STATIONS) {
                        if (s.minPos == null)
                            continue;
                        double dist = distanceToAABB(px, py, pz,
                                s.minPos[0], s.minPos[1], s.minPos[2],
                                s.maxPos[0], s.maxPos[1], s.maxPos[2]);
                        if (dist < minDist) {
                            minDist = dist;
                            nearestStation = s;
                        }
                    }
                    nearestDistance = minDist;
                }
                if (nearestStation != null) {
                    Component nameText = Component.literal(nearestStation.nameZh);
                    Component distText = Component.literal(String.format("%.1f m", nearestDistance));
                    int x = 10;
                    int y = graphics.guiHeight() / 2 - client.font.lineHeight;
                    drawOutlinedText(graphics, client.font, nameText, x, y);
                    drawOutlinedText(graphics, client.font, distText, x, y + client.font.lineHeight + 2);
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

    private static String formatTime(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    private static double distanceToAABB(double px, double py, double pz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double dx = Math.max(0, Math.max(minX - px, px - maxX));
        double dy = Math.max(0, Math.max(minY - py, py - maxY));
        double dz = Math.max(0, Math.max(minZ - pz, pz - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}