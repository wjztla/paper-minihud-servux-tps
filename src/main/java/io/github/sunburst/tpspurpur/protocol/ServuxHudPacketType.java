package io.github.sunburst.tpspurpur.protocol;

public enum ServuxHudPacketType {
  S2C_METADATA(1),
  C2S_METADATA_REQUEST(2),
  S2C_SPAWN_DATA(3),
  C2S_SPAWN_DATA_REQUEST(4),
  S2C_WEATHER_TICK(5),
  C2S_RECIPE_MANAGER_REQUEST(6),
  S2C_DATA_LOGGER_TICK(7),
  C2S_DATA_LOGGER_REQUEST(8),
  S2C_NBT_RESPONSE_START(10),
  S2C_NBT_RESPONSE_DATA(11);

  private final int id;

  ServuxHudPacketType(int id) {
    this.id = id;
  }

  public int id() {
    return this.id;
  }

  public static ServuxHudPacketType fromId(int id) {
    for (ServuxHudPacketType type : values()) {
      if (type.id == id) {
        return type;
      }
    }

    throw new IllegalArgumentException("Unknown Servux HUD packet type: " + id);
  }
}
