package io.github.sunburst.tpspurpur;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sunburst.tpspurpur.protocol.ServuxHudCodec;
import io.github.sunburst.tpspurpur.protocol.ServuxHudRequest;
import io.github.sunburst.tpspurpur.protocol.TpsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class MiniHUDServuxTPS extends JavaPlugin implements Listener, PluginMessageListener {

  private final Map<UUID, Boolean> tpsSubscribers = new ConcurrentHashMap<>();
  private ServuxHudCodec codec;
  private int updateIntervalTicks;
  private boolean debugLogging;

  @Override
  public void onEnable() {
    this.codec = ServuxHudCodec.create();
    if (!this.codec.isAvailable()) {
      getLogger().severe("Unable to resolve runtime NMS classes for Servux HUD encoding. Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    saveDefaultConfig();
    reloadLocalConfig();

    getServer().getMessenger().registerIncomingPluginChannel(this, ServuxHudCodec.CHANNEL_ID, this);
    getServer().getMessenger().registerOutgoingPluginChannel(this, ServuxHudCodec.CHANNEL_ID);
    getServer().getPluginManager().registerEvents(this, this);

    long period = Math.max(1, this.updateIntervalTicks);
    getServer().getScheduler().runTaskTimer(this, this::pushTpsUpdates, period, period);

    getLogger().info("Enabled MiniHUD Servux TPS bridge on channel " + ServuxHudCodec.CHANNEL_ID);
    getLogger().info("MiniHUDServuxTPS debug logging is " + (this.debugLogging ? "enabled" : "disabled"));
  }

  @Override
  public void onDisable() {
    this.tpsSubscribers.clear();
    getServer().getMessenger().unregisterIncomingPluginChannel(this, ServuxHudCodec.CHANNEL_ID, this);
    getServer().getMessenger().unregisterOutgoingPluginChannel(this, ServuxHudCodec.CHANNEL_ID);
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!ServuxHudCodec.CHANNEL_ID.equals(channel)) {
      return;
    }

    try {
      ServuxHudRequest payload = this.codec.decode(message);
      debug("Received " + payload.type() + " from " + player.getName() + ", tps=" + payload.tpsLoggerEnabled());

      switch (payload.type()) {
        case C2S_METADATA_REQUEST -> sendMetadata(player);
        case C2S_SPAWN_DATA_REQUEST -> sendSpawnMetadata(player);
        case C2S_DATA_LOGGER_REQUEST -> refreshLoggers(player, payload.tpsLoggerEnabled());
        case C2S_RECIPE_MANAGER_REQUEST -> debug("Ignoring recipe manager request from " + player.getName());
        default -> debug("Ignoring unsupported packet " + payload.type() + " from " + player.getName());
      }
    } catch (Exception exception) {
      getLogger().warning("Failed to decode MiniHUD payload from " + player.getName() + ": " + exception.getMessage());
    }
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    this.tpsSubscribers.remove(event.getPlayer().getUniqueId());
  }

  private void reloadLocalConfig() {
    this.updateIntervalTicks = Math.max(1, getConfig().getInt("update-interval-ticks", 15));
    this.debugLogging = getConfig().getBoolean("debug-logging", false);
  }

  private void refreshLoggers(Player player, boolean wantsTps) {
    if (wantsTps) {
      this.tpsSubscribers.put(player.getUniqueId(), Boolean.TRUE);
      sendTpsUpdate(player);
    } else {
      this.tpsSubscribers.remove(player.getUniqueId());
    }

    debug("Logger refresh from " + player.getName() + ": tps=" + wantsTps + ", subscribers=" + this.tpsSubscribers.size());
  }

  private void sendMetadata(Player player) {
    debug("Sending S2C_METADATA to " + player.getName());
    send(player, this.codec.encodeMetadata(
        pluginIdentity(),
        resolveSpawnDimension(),
        resolveSpawnLocation(),
        true,
        false
    ));
  }

  private void sendSpawnMetadata(Player player) {
    debug("Sending S2C_SPAWN_DATA to " + player.getName());
    send(player, this.codec.encodeSpawnMetadata(
        pluginIdentity(),
        resolveSpawnDimension(),
        resolveSpawnLocation()
    ));
  }

  private void pushTpsUpdates() {
    if (this.tpsSubscribers.isEmpty()) {
      return;
    }

    TpsSnapshot snapshot = captureSnapshot();

    this.tpsSubscribers.keySet().removeIf(uuid -> {
      Player player = Bukkit.getPlayer(uuid);

      if (player == null || !player.isOnline()) {
        return true;
      }

      sendTpsUpdate(player, snapshot);
      return false;
    });
  }

  private void sendTpsUpdate(Player player) {
    sendTpsUpdate(player, captureSnapshot());
  }

  private void sendTpsUpdate(Player player, TpsSnapshot snapshot) {
    debug("Sending S2C_DATA_LOGGER_TICK to " + player.getName() + " mspt=" + snapshot.mspt() + " tps=" + snapshot.tps());
    send(player, this.codec.encodeTpsLoggerTick(snapshot));
  }

  private void send(Player player, byte[] bytes) {
    player.sendPluginMessage(this, ServuxHudCodec.CHANNEL_ID, bytes);
  }

  private TpsSnapshot captureSnapshot() {
    ServerTickManager tickManager = Bukkit.getServerTickManager();
    boolean frozen = tickManager.isFrozen();
    boolean sprinting = tickManager.isSprinting();
    boolean stepping = tickManager.isStepping();
    double mspt = Bukkit.getAverageTickTime();
    double targetMspt = sprinting ? 0.0D : (1000.0D / tickManager.getTickRate());
    double tps = frozen ? 0.0D : 1000.0D / Math.max(targetMspt, mspt);
    long sprintTicks = sprinting ? 1L : 0L;

    return new TpsSnapshot(mspt, tps, sprintTicks, frozen, sprinting, stepping);
  }

  private String pluginIdentity() {
    return getName() + ' ' + getPluginMeta().getVersion();
  }

  private Location resolveSpawnLocation() {
    World overworld = Bukkit.getWorlds().stream()
        .filter(world -> world.getEnvironment() == World.Environment.NORMAL)
        .findFirst()
        .orElseGet(() -> Bukkit.getWorlds().get(0));

    return overworld.getSpawnLocation();
  }

  private String resolveSpawnDimension() {
    World world = resolveSpawnLocation().getWorld();

    if (world == null) {
      return "minecraft:overworld";
    }

    return switch (world.getEnvironment()) {
      case NETHER -> "minecraft:the_nether";
      case THE_END -> "minecraft:the_end";
      default -> "minecraft:overworld";
    };
  }

  private void debug(String message) {
    if (this.debugLogging) {
      getLogger().info(message);
    }
  }
}
