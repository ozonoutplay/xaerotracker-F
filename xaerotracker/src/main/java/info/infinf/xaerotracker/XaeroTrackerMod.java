package info.infinf.xaerotracker;

import info.infinf.xaerotracker.command.XaeroTrackerCommand;
import info.infinf.xaerotracker.util.MessageUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class XaeroTrackerMod implements ModInitializer {

    public static final String MOD_ID = "xaerotracker";
    public static final Logger LOGGER = LoggerFactory.getLogger("XaeroTracker");

    public static final String MINIMAP_CHANNEL  = "xaerominimap:main";
    public static final String WORLDMAP_CHANNEL = "xaeroworldmap:main";

    public static final CustomPacketPayload.Type<MinimapPayload>  MINIMAP_TYPE  =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xaerominimap", "main"));
    public static final CustomPacketPayload.Type<WorldMapPayload> WORLDMAP_TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xaeroworldmap", "main"));

    public static XaeroTrackerMod INSTANCE;

    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private ScheduledExecutorService trackerThread;
    private FilePlayerList trackIgnoreList;
    private FilePlayerList trackBypassList;
    private XaeroConfig config;

    public record MinimapPayload(byte[] data) implements CustomPacketPayload {
        @Override public CustomPacketPayload.Type<MinimapPayload> type() { return MINIMAP_TYPE; }
    }

    public record WorldMapPayload(byte[] data) implements CustomPacketPayload {
        @Override public CustomPacketPayload.Type<WorldMapPayload> type() { return WORLDMAP_TYPE; }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, MinimapPayload> MINIMAP_CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeBytes(p.data()),
            buf -> { byte[] b = new byte[buf.readableBytes()]; buf.readBytes(b); return new MinimapPayload(b); }
        );

    private static final StreamCodec<RegistryFriendlyByteBuf, WorldMapPayload> WORLDMAP_CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeBytes(p.data()),
            buf -> { byte[] b = new byte[buf.readableBytes()]; buf.readBytes(b); return new WorldMapPayload(b); }
        );

    @Override
    public void onInitialize() {
        INSTANCE = this;

        var configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        config = new XaeroConfig(configDir);
        trackIgnoreList = new FilePlayerList(configDir.resolve("track_ignore_list.txt"));
        trackBypassList = new FilePlayerList(configDir.resolve("track_bypass_list.txt"));

        tryRegister(() -> PayloadTypeRegistry.serverboundPlay().register(MINIMAP_TYPE,  MINIMAP_CODEC));
        tryRegister(() -> PayloadTypeRegistry.serverboundPlay().register(WORLDMAP_TYPE, WORLDMAP_CODEC));
        tryRegister(() -> PayloadTypeRegistry.clientboundPlay().register(MINIMAP_TYPE,  MINIMAP_CODEC));
        tryRegister(() -> PayloadTypeRegistry.clientboundPlay().register(WORLDMAP_TYPE, WORLDMAP_CODEC));

        ServerPlayNetworking.registerGlobalReceiver(MINIMAP_TYPE, (payload, context) -> {
            var player = context.player();
            var data = payload.data();
            if (data.length >= 5 && data[0] == 1) {
                int version = ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) |
                              ((data[3] & 0xFF) << 8)  |  (data[4] & 0xFF);
                LOGGER.info("[XT-DEBUG] MINIMAP handshake received: player={} version={}",
                    player.getName().getString(), version);
                trackerThread.submit(() -> {
                    var pd = playerData.computeIfAbsent(player.getUUID(), k -> new PlayerData());
                    pd.setMiniMapNetworkVersion(version);
                    if (pd.hasWorldMap()) sendRaw(player, WORLDMAP_CHANNEL, MessageUtil.getTrackResetMessage());
                    sendRaw(player, MINIMAP_CHANNEL, MessageUtil.getTrackResetMessage());
                    trackOthers(player, MINIMAP_CHANNEL);
                });
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(WORLDMAP_TYPE, (payload, context) -> {
            var player = context.player();
            var data = payload.data();
            if (data.length >= 5 && data[0] == 1) {
                int version = ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) |
                              ((data[3] & 0xFF) << 8)  |  (data[4] & 0xFF);
                LOGGER.info("[XT-DEBUG] WORLDMAP handshake received: player={} version={}",
                    player.getName().getString(), version);
                trackerThread.submit(() -> {
                    var pd = playerData.computeIfAbsent(player.getUUID(), k -> new PlayerData());
                    pd.setWorldMapNetworkVersion(version);
                    if (!pd.hasMiniMap()) trackOthers(player, WORLDMAP_CHANNEL);
                });
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var pd = new PlayerData();
            playerData.put(player.getUUID(), pd);
            sendRaw(player, MINIMAP_CHANNEL,  MessageUtil.getHandshakeMessage());
            sendRaw(player, WORLDMAP_CHANNEL, MessageUtil.getHandshakeMessage());
            if (config.shouldSendLevelId) {
                sendRaw(player, MINIMAP_CHANNEL,  MessageUtil.getLevelIdMessage(config.levelId));
                sendRaw(player, WORLDMAP_CHANNEL, MessageUtil.getLevelIdMessage(config.levelId));
            }
            trackerThread.submit(() -> track(player, pd));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            trackerThread.submit(() -> {
                untrack(player, playerData.get(player.getUUID()));
                playerData.remove(player.getUUID());
            });
        });

        // Fix: player crosses into Nether/End and back, then stops receiving position
        // updates for other players (while others keep receiving updates for them
        // fine) until they reconnect.
        //
        // Root cause (confirmed via client log): Xaero's client mods do their OWN
        // reset of tracked players on every dimension change ("Server side doesn't
        // have XaeroLib installed! Resetting."), independent of anything we send.
        // A partial resync (just trackReset + trackOthers) didn't survive that reset
        // even with a delay, so this mirrors the FULL sequence we normally only send
        // on join (handshake + levelId + trackReset + trackOthers) — in case the
        // client's post-reset state machine expects the same full re-init as a fresh
        // connection, not just a partial nudge.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            LOGGER.info("[XT-DEBUG] dim change fired: player={} origin={} destination={}",
                player.getName().getString(), origin.dimension(), destination.dimension());
            trackerThread.schedule(() -> {
                var pd = playerData.get(player.getUUID());
                if (pd == null) {
                    LOGGER.info("[XT-DEBUG] dim change resync SKIPPED: no PlayerData for {}", player.getName().getString());
                    return;
                }
                LOGGER.info("[XT-DEBUG] dim change FULL resync (delayed): player={} channel={} miniMapVer={} worldMapVer={}",
                    player.getName().getString(), pd.channel, pd.getMiniMapNetworkVersion(), pd.getWorldMapNetworkVersion());
                if (pd.channel == null) {
                    LOGGER.info("[XT-DEBUG] dim change resync SKIPPED: channel is null for {}", player.getName().getString());
                    return;
                }
                sendRaw(player, MINIMAP_CHANNEL,  MessageUtil.getHandshakeMessage());
                sendRaw(player, WORLDMAP_CHANNEL, MessageUtil.getHandshakeMessage());
                if (config.shouldSendLevelId) {
                    sendRaw(player, MINIMAP_CHANNEL,  MessageUtil.getLevelIdMessage(config.levelId));
                    sendRaw(player, WORLDMAP_CHANNEL, MessageUtil.getLevelIdMessage(config.levelId));
                }
                var targets = getTargets(player);
                LOGGER.info("[XT-DEBUG] trackOthers about to run for {}, target count={}",
                    player.getName().getString(), targets.size());
                sendChannel(player, pd.channel, MessageUtil.getTrackResetMessage());
                trackOthers(player, pd.channel);
                LOGGER.info("[XT-DEBUG] trackOthers done for {}", player.getName().getString());
            }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        });

        // Diagnostic: log every handshake packet (data[0] == 1) so we can see whether
        // the Xaero client re-sends its version handshake around dimension changes.
        // (added inside the existing MINIMAP_TYPE/WORLDMAP_TYPE receivers above)

        CommandRegistrationCallback.EVENT.register(XaeroTrackerCommand::register);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            trackerThread = Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(null, r, "XaeroTracker-Thread", 0);
                t.setDaemon(false);
                return t;
            });
            LOGGER.info("XaeroTracker started. Level ID: {}", config.levelId);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (trackerThread != null) { trackerThread.shutdownNow(); trackerThread = null; }
            playerData.clear();
            LOGGER.info("XaeroTracker stopped.");
        });

        LOGGER.info("XaeroTracker Fabric mod initialized.");
    }

    public boolean shouldBeTracked(ServerPlayer player) {
        return !trackIgnoreList.contains(player.getName().getString())
            && !player.isInvisible()
            && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }

    public boolean shouldBeTrackedBypass(ServerPlayer player, ServerPlayer other) {
        return trackBypassList.contains(other.getName().getString());
    }

    private List<ServerPlayer> getTargets(ServerPlayer player) {
        if (config.onlySyncSameWorld) return player.level().players();
        return player.level().getServer().getPlayerList().getPlayers();
    }

    public void track(ServerPlayer player, PlayerData pd) {
        var msg = MessageUtil.getTrackPlayerMessage(player);
        boolean shouldTrack = shouldBeTracked(player);
        byte[] untrackMsg = null;
        for (var other : getTargets(player)) {
            if (other == player) continue;
            var otherData = playerData.get(other.getUUID());
            if (otherData == null || otherData.channel == null) {
                LOGGER.info("[XT-DEBUG] track({}) SKIPPED for observer {}: otherData={} channel={}",
                    player.getName().getString(), other.getName().getString(),
                    otherData == null ? "null" : "present",
                    otherData == null ? "n/a" : otherData.channel);
                continue;
            }
            if (!shouldTrack && !shouldBeTrackedBypass(player, other)) {
                if (pd.lastShouldTrack) {
                    if (untrackMsg == null) untrackMsg = MessageUtil.getUntrackPlayerMessage(player);
                    sendChannel(other, otherData.channel, untrackMsg);
                }
                continue;
            }
            sendChannel(other, otherData.channel, msg);
        }
        pd.lastShouldTrack = shouldTrack;
        pd.lastSyncTime = System.currentTimeMillis();
    }

    public void trackOthers(ServerPlayer player, String channel) {
        for (var other : getTargets(player)) {
            if (other == player) continue;
            if (shouldBeTracked(other) || shouldBeTrackedBypass(other, player)) {
                sendChannel(player, channel, MessageUtil.getTrackPlayerMessage(other));
            }
        }
    }

    public void hideUntracked(ServerPlayer player) {
        var pd = playerData.get(player.getUUID());
        if (pd == null || pd.channel == null) return;
        for (var other : getTargets(player)) {
            if (other != player && !shouldBeTracked(other) && !shouldBeTrackedBypass(other, player)) {
                sendChannel(player, pd.channel, MessageUtil.getUntrackPlayerMessage(other));
            }
        }
    }

    public void untrack(ServerPlayer player, PlayerData pd) {
        if (pd != null) pd.clearSyncSchedule();
        var msg = MessageUtil.getUntrackPlayerMessage(player);
        for (var other : getTargets(player)) {
            if (other == player) continue;
            var otherData = playerData.get(other.getUUID());
            if (otherData != null && otherData.channel != null) {
                sendChannel(other, otherData.channel, msg);
            }
        }
    }

    public void sendChannel(ServerPlayer player, String channel, byte[] data) {
        sendRaw(player, channel, data);
    }

    public void sendRaw(ServerPlayer player, String channel, byte[] data) {
        if (MINIMAP_CHANNEL.equals(channel)) ServerPlayNetworking.send(player, new MinimapPayload(data));
        else if (WORLDMAP_CHANNEL.equals(channel)) ServerPlayNetworking.send(player, new WorldMapPayload(data));
    }

    private void tryRegister(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException ignored) {}
    }

    public void onPlayerMoved(ServerPlayer player) {
        if (trackerThread == null || trackerThread.isShutdown()) return;
        trackerThread.submit(() -> {
            var pd = playerData.get(player.getUUID());
            if (pd == null) return;
            pd.clearSyncSchedule();
            long elapsed = System.currentTimeMillis() - pd.lastSyncTime;
            if (elapsed >= config.syncCooldown) {
                track(player, pd);
            } else {
                pd.syncSchedule = trackerThread.schedule(
                    () -> track(player, pd),
                    config.syncCooldown - elapsed,
                    TimeUnit.MILLISECONDS
                );
            }
        });
    }

    public Map<UUID, PlayerData> getPlayerData() { return playerData; }
    public ScheduledExecutorService getTrackerThread() { return trackerThread; }
    public FilePlayerList getTrackIgnoreList() { return trackIgnoreList; }
    public FilePlayerList getTrackBypassList() { return trackBypassList; }
    public XaeroConfig getConfig() { return config; }
}