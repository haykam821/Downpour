package io.github.haykam821.downpour.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class DownpourMapConfig {
	public static final Codec<DownpourMapConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Codec.INT.fieldOf("x").forGetter(DownpourMapConfig::getX),
			Codec.INT.fieldOf("z").forGetter(DownpourMapConfig::getZ),
			Codec.BOOL.optionalFieldOf("walls", true).forGetter(DownpourMapConfig::hasWalls)
		).apply(instance, DownpourMapConfig::new);
	});

	private final int x;
	private final int z;
	private final boolean walls;

	public DownpourMapConfig(int x, int z, boolean walls) {
		this.x = x;
		this.z = z;
		this.walls = walls;
	}

	public int getX() {
		return this.x;
	}

	public int getZ() {
		return this.z;
	}

	public boolean hasWalls() {
		return this.walls;
	}
}