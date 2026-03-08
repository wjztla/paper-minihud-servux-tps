package io.github.sunburst.tpspurpur.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;

public final class ServuxHudCodec {

  public static final String CHANNEL_ID = "servux:hud_metadata";
  public static final int PROTOCOL_VERSION = 2;
  private static final String PROVIDER_NAME = "hud_data";

  private static final int TAG_END = 0;
  private static final int TAG_BYTE = 1;
  private static final int TAG_SHORT = 2;
  private static final int TAG_INT = 3;
  private static final int TAG_LONG = 4;
  private static final int TAG_FLOAT = 5;
  private static final int TAG_DOUBLE = 6;
  private static final int TAG_BYTE_ARRAY = 7;
  private static final int TAG_STRING = 8;
  private static final int TAG_LIST = 9;
  private static final int TAG_COMPOUND = 10;
  private static final int TAG_INT_ARRAY = 11;
  private static final int TAG_LONG_ARRAY = 12;

  public static ServuxHudCodec create() {
    return new ServuxHudCodec();
  }

  public boolean isAvailable() {
    return true;
  }

  public ServuxHudRequest decode(byte[] message) {
    try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
      int packetId = readVarInt(input);
      ServuxHudPacketType type = ServuxHudPacketType.fromId(packetId);
      boolean tpsLoggerEnabled = false;

      if (readsNbt(type)) {
        NbtCompound compound = readRootCompound(input);
        if (type == ServuxHudPacketType.C2S_DATA_LOGGER_REQUEST) {
          tpsLoggerEnabled = compound.getBooleanIgnoreCaseOrDefault("tps", false);
        }
      }

      return new ServuxHudRequest(type, tpsLoggerEnabled);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to decode Servux HUD payload", exception);
    }
  }

  public byte[] encodeMetadata(String servuxIdentity,
                               String spawnDimension,
                               Location spawnLocation,
                               boolean tpsSupported,
                               boolean mobCapsSupported) {
    NbtCompound metadata = createSpawnCompound(servuxIdentity, spawnDimension, spawnLocation)
        .putString("name", PROVIDER_NAME)
        .putCompound("Loggers", new NbtCompound()
            .putBoolean("tps", tpsSupported)
            .putBoolean("mob_caps", mobCapsSupported));

    return encode(ServuxHudPacketType.S2C_METADATA, metadata);
  }

  public byte[] encodeSpawnMetadata(String servuxIdentity, String spawnDimension, Location spawnLocation) {
    return encode(ServuxHudPacketType.S2C_SPAWN_DATA, createSpawnCompound(servuxIdentity, spawnDimension, spawnLocation));
  }

  public byte[] encodeTpsLoggerTick(TpsSnapshot snapshot) {
    NbtCompound tpsData = new NbtCompound()
        .putDouble("mspt", snapshot.mspt())
        .putDouble("tps", snapshot.tps())
        .putLong("sprintTicks", snapshot.sprintTicks())
        .putBoolean("frozen", snapshot.frozen())
        .putBoolean("sprinting", snapshot.sprinting())
        .putBoolean("stepping", snapshot.stepping());

    return encode(ServuxHudPacketType.S2C_DATA_LOGGER_TICK, new NbtCompound().putCompound("tps", tpsData));
  }

  private static NbtCompound createSpawnCompound(String servuxIdentity,
                                                 String spawnDimension,
                                                 Location spawnLocation) {
    return new NbtCompound()
        .putString("id", CHANNEL_ID)
        .putString("servux", servuxIdentity)
        .putInt("version", PROTOCOL_VERSION)
        .putString("spawnDimension", spawnDimension)
        .putInt("spawnPosX", spawnLocation.getBlockX())
        .putInt("spawnPosY", spawnLocation.getBlockY())
        .putInt("spawnPosZ", spawnLocation.getBlockZ());
  }

  private static byte[] encode(ServuxHudPacketType type, NbtCompound compound) {
    try {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      DataOutputStream output = new DataOutputStream(byteStream);
      writeVarInt(output, type.id());

      if (readsNbt(type)) {
        writeRootCompound(output, compound);
      }

      output.flush();
      return byteStream.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to encode Servux HUD payload", exception);
    }
  }

  private static NbtCompound readRootCompound(DataInputStream input) throws IOException {
    int tagType = input.readUnsignedByte();

    if (tagType == TAG_END) {
      return new NbtCompound();
    }

    if (tagType != TAG_COMPOUND) {
      throw new IOException("Expected root compound tag but received tag id " + tagType);
    }

    return readCompoundPayload(input);
  }

  private static NbtCompound readCompoundPayload(DataInputStream input) throws IOException {
    NbtCompound compound = new NbtCompound();

    while (true) {
      int tagType = input.readUnsignedByte();
      if (tagType == TAG_END) {
        return compound;
      }

      String name = readString(input);
      compound.putRaw(name, readPayload(input, tagType));
    }
  }

  private static Object readPayload(DataInputStream input, int tagType) throws IOException {
    return switch (tagType) {
      case TAG_BYTE -> input.readByte();
      case TAG_SHORT -> input.readShort();
      case TAG_INT -> input.readInt();
      case TAG_LONG -> input.readLong();
      case TAG_FLOAT -> input.readFloat();
      case TAG_DOUBLE -> input.readDouble();
      case TAG_BYTE_ARRAY -> {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        yield bytes;
      }
      case TAG_STRING -> readString(input);
      case TAG_LIST -> {
        int elementType = input.readUnsignedByte();
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          skipPayload(input, elementType);
        }
        yield SkippedTag.INSTANCE;
      }
      case TAG_COMPOUND -> readCompoundPayload(input);
      case TAG_INT_ARRAY -> {
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          input.readInt();
        }
        yield SkippedTag.INSTANCE;
      }
      case TAG_LONG_ARRAY -> {
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          input.readLong();
        }
        yield SkippedTag.INSTANCE;
      }
      default -> throw new IOException("Unsupported NBT tag id " + tagType);
    };
  }

  private static void skipPayload(DataInputStream input, int tagType) throws IOException {
    switch (tagType) {
      case TAG_BYTE -> input.readByte();
      case TAG_SHORT -> input.readShort();
      case TAG_INT -> input.readInt();
      case TAG_LONG -> input.readLong();
      case TAG_FLOAT -> input.readFloat();
      case TAG_DOUBLE -> input.readDouble();
      case TAG_BYTE_ARRAY -> {
        int length = input.readInt();
        byte[] bytes = new byte[length];
        input.readFully(bytes);
      }
      case TAG_STRING -> readString(input);
      case TAG_LIST -> {
        int elementType = input.readUnsignedByte();
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          skipPayload(input, elementType);
        }
      }
      case TAG_COMPOUND -> {
        while (true) {
          int nestedType = input.readUnsignedByte();
          if (nestedType == TAG_END) {
            break;
          }
          readString(input);
          skipPayload(input, nestedType);
        }
      }
      case TAG_INT_ARRAY -> {
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          input.readInt();
        }
      }
      case TAG_LONG_ARRAY -> {
        int length = input.readInt();
        for (int index = 0; index < length; index++) {
          input.readLong();
        }
      }
      default -> throw new IOException("Unsupported NBT tag id " + tagType);
    }
  }

  private static void writeRootCompound(DataOutputStream output, NbtCompound compound) throws IOException {
    output.writeByte(TAG_COMPOUND);
    writeCompoundPayload(output, compound);
  }

  private static void writeCompoundPayload(DataOutputStream output, NbtCompound compound) throws IOException {
    for (Map.Entry<String, Object> entry : compound.entries()) {
      writeNamedTag(output, entry.getKey(), entry.getValue());
    }

    output.writeByte(TAG_END);
  }

  private static void writeNamedTag(DataOutputStream output, String name, Object value) throws IOException {
    int tagType = tagTypeOf(value);
    output.writeByte(tagType);
    writeString(output, name);
    writePayload(output, tagType, value);
  }

  private static void writePayload(DataOutputStream output, int tagType, Object value) throws IOException {
    switch (tagType) {
      case TAG_BYTE -> {
        if (value instanceof Boolean bool) {
          output.writeByte(bool ? 1 : 0);
        } else {
          output.writeByte(((Number) value).intValue());
        }
      }
      case TAG_SHORT -> output.writeShort(((Number) value).intValue());
      case TAG_INT -> output.writeInt(((Number) value).intValue());
      case TAG_LONG -> output.writeLong(((Number) value).longValue());
      case TAG_FLOAT -> output.writeFloat(((Number) value).floatValue());
      case TAG_DOUBLE -> output.writeDouble(((Number) value).doubleValue());
      case TAG_STRING -> writeString(output, (String) value);
      case TAG_COMPOUND -> writeCompoundPayload(output, (NbtCompound) value);
      case TAG_BYTE_ARRAY -> {
        byte[] bytes = (byte[]) value;
        output.writeInt(bytes.length);
        output.write(bytes);
      }
      case TAG_INT_ARRAY -> {
        int[] values = (int[]) value;
        output.writeInt(values.length);
        for (int current : values) {
          output.writeInt(current);
        }
      }
      case TAG_LONG_ARRAY -> {
        long[] values = (long[]) value;
        output.writeInt(values.length);
        for (long current : values) {
          output.writeLong(current);
        }
      }
      default -> throw new IOException("Unsupported writable NBT tag id " + tagType);
    }
  }

  private static int tagTypeOf(Object value) {
    if (value instanceof Boolean || value instanceof Byte) {
      return TAG_BYTE;
    }
    if (value instanceof Short) {
      return TAG_SHORT;
    }
    if (value instanceof Integer) {
      return TAG_INT;
    }
    if (value instanceof Long) {
      return TAG_LONG;
    }
    if (value instanceof Float) {
      return TAG_FLOAT;
    }
    if (value instanceof Double) {
      return TAG_DOUBLE;
    }
    if (value instanceof String) {
      return TAG_STRING;
    }
    if (value instanceof NbtCompound) {
      return TAG_COMPOUND;
    }
    if (value instanceof byte[]) {
      return TAG_BYTE_ARRAY;
    }
    if (value instanceof int[]) {
      return TAG_INT_ARRAY;
    }
    if (value instanceof long[]) {
      return TAG_LONG_ARRAY;
    }

    throw new IllegalArgumentException("Unsupported NBT value type: " + value.getClass().getName());
  }

  private static int readVarInt(DataInputStream input) throws IOException {
    int value = 0;
    int position = 0;

    while (true) {
      if (position >= 35) {
        throw new IOException("VarInt is too big");
      }

      int currentByte = input.readUnsignedByte();
      value |= (currentByte & 0x7F) << position;

      if ((currentByte & 0x80) == 0) {
        return value;
      }

      position += 7;
    }
  }

  private static void writeVarInt(DataOutputStream output, int value) throws IOException {
    while ((value & ~0x7F) != 0) {
      output.writeByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }

    output.writeByte(value);
  }

  private static String readString(DataInputStream input) throws IOException {
    int length = input.readUnsignedShort();
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 65535) {
      throw new IOException("NBT string is too long: " + bytes.length + " bytes");
    }

    output.writeShort(bytes.length);
    output.write(bytes);
  }

  private static boolean readsNbt(ServuxHudPacketType type) {
    return switch (type) {
      case C2S_METADATA_REQUEST,
          S2C_METADATA,
          C2S_SPAWN_DATA_REQUEST,
          S2C_SPAWN_DATA,
          S2C_WEATHER_TICK,
          C2S_RECIPE_MANAGER_REQUEST,
          S2C_DATA_LOGGER_TICK,
          C2S_DATA_LOGGER_REQUEST -> true;
      default -> false;
    };
  }

  private static final class NbtCompound {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private NbtCompound putString(String key, String value) {
      this.values.put(key, value);
      return this;
    }

    private NbtCompound putInt(String key, int value) {
      this.values.put(key, value);
      return this;
    }

    private NbtCompound putLong(String key, long value) {
      this.values.put(key, value);
      return this;
    }

    private NbtCompound putDouble(String key, double value) {
      this.values.put(key, value);
      return this;
    }

    private NbtCompound putBoolean(String key, boolean value) {
      this.values.put(key, value);
      return this;
    }

    private NbtCompound putCompound(String key, NbtCompound value) {
      this.values.put(key, value);
      return this;
    }

    private void putRaw(String key, Object value) {
      this.values.put(key, value);
    }

    private boolean getBooleanIgnoreCaseOrDefault(String key, boolean defaultValue) {
      Object directValue = this.values.get(key);
      if (directValue != null) {
        return asBoolean(directValue, defaultValue);
      }

      for (Map.Entry<String, Object> entry : this.values.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(key)) {
          return asBoolean(entry.getValue(), defaultValue);
        }
      }

      return defaultValue;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
      if (value instanceof Boolean bool) {
        return bool;
      }
      if (value instanceof Number number) {
        return number.intValue() != 0;
      }

      return defaultValue;
    }

    private Iterable<Map.Entry<String, Object>> entries() {
      return this.values.entrySet();
    }
  }

  private enum SkippedTag {
    INSTANCE
  }
}
