package io.github.haykam821.downpour.game.map;

import java.util.Iterator;

import io.github.haykam821.downpour.game.DownpourConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.util.BlockBounds;

public class DownpourMapBuilder {
	private static final BlockState FLOOR = Blocks.COARSE_DIRT.getDefaultState();
	private static final BlockState FLOOR_OUTLINE = Blocks.DARK_OAK_PLANKS.getDefaultState();
	private static final BlockState WALL = Blocks.ANDESITE_WALL.getDefaultState();
	private static final BlockState WALL_TOP = Blocks.SPRUCE_SLAB.getDefaultState();
	private static final BlockState BARRIER = Blocks.BARRIER.getDefaultState();

	private final DownpourConfig config;

	public DownpourMapBuilder(DownpourConfig config) {
		this.config = config;
	}

	public DownpourMap create() {
		MapTemplate template = MapTemplate.createEmpty();
		DownpourMapConfig mapConfig = this.config.getMapConfig();

		BlockBounds bounds = new BlockBounds(BlockPos.ORIGIN, new BlockPos(mapConfig.getX() + 1, 4, mapConfig.getZ() + 1));
		this.build(bounds, template, mapConfig);

		BlockBounds shelterBounds = new BlockBounds(new BlockPos(5, 1, 5), new BlockPos(mapConfig.getX() - 4, 1, mapConfig.getZ() - 4));
		return new DownpourMap(template, bounds, shelterBounds);
	}

	private BlockState getBlockState(BlockPos pos, BlockBounds bounds, DownpourMapConfig mapConfig) {
		int layer = pos.getY() - bounds.getMin().getY();
		boolean outline = pos.getX() == bounds.getMin().getX() || pos.getX() == bounds.getMax().getX() || pos.getZ() == bounds.getMin().getZ() || pos.getZ() == bounds.getMax().getZ();

		if (outline) {
			if (layer == 0) {
				return FLOOR_OUTLINE;
			} else if (layer == 1) {
				return WALL;
			} else if (layer == 2) {
				return WALL_TOP;
			} else if (layer >= 3) {
				return BARRIER;
			}
		} else if (layer == 0) {
			return FLOOR;
		}

		return null;
	}

	public void build(BlockBounds bounds, MapTemplate template, DownpourMapConfig mapConfig) {
		Iterator<BlockPos> iterator = bounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();

			BlockState state = this.getBlockState(pos, bounds, mapConfig);
			if (state != null) {
				template.setBlockState(pos, state);
			}
		}
	}
}