package io.github.haykam821.downpour.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.TemplateChunkGenerator;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class DownpourMap {
	private final MapTemplate template;
	private final BlockBounds bounds;
	private final BlockBounds shelterBounds;

	public DownpourMap(MapTemplate template, BlockBounds bounds, BlockBounds shelterBounds) {
		this.template = template;
		this.bounds = bounds;
		this.shelterBounds = shelterBounds;
	}

	public BlockBounds getBounds() {
		return this.bounds;
	}

	public BlockBounds getShelterBounds() {
		return this.shelterBounds;
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}