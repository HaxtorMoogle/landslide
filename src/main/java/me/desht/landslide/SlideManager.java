package me.desht.landslide;

/*
This file is part of Landslide

Landslide is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Landslide is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Landslide.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import me.desht.dhutils.Debugger;
import me.desht.dhutils.LogUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.material.PistonBaseMaterial;
import org.bukkit.material.PistonExtensionMaterial;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SlideManager {
	private static final int RING_BUFFER_SIZE = 30;
	private static final int MAX_SLIDE_DELAY = 20;

	private static final Block[] neighbours = new Block[BlockFace.values().length];

	private final LandslidePlugin plugin;
	private final Set<Location> slideTo = new HashSet<Location>();
	private final List<ScheduledBlockMove>[] slides;
	private final List<Drop>[] drops;
	private final Set<Material> bracingMaterials = new HashSet<Material>();

	private int pointer;
	private int totalSlidesScheduled;
	private int maxSlidesPerTick;
	private int maxSlidesTotal;
	private Boolean worldGuardEnabled;
	private StateFlag wgFlag;
	private boolean stickyPistonsRetracted;
	private boolean stickyPistonsExtended;
	private int bracingDistance;

	public interface ScheduledBlockMove {
		public FallingBlock initiateMove();
	}

	@SuppressWarnings("unchecked")
	public SlideManager(LandslidePlugin plugin) {
		this.plugin = plugin;
		slides = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			slides[i] = new ArrayList<ScheduledBlockMove>();
		}
		drops  = new ArrayList[RING_BUFFER_SIZE];
		for (int i = 0; i < RING_BUFFER_SIZE; i++) {
			drops[i] = new ArrayList<SlideManager.Drop>();
		}
		pointer = 0;
		totalSlidesScheduled = 0;
		worldGuardEnabled = false;
		wgFlag = null;
	}

	public void tick() {
		slideTo.clear();

		if (slides[pointer].size() > 0) {
			Debugger.getInstance().debug(2, "pointer = " + pointer + " - " + slides[pointer].size() + " blocks to slide");
		}
		for (ScheduledBlockMove slide : new ArrayList<ScheduledBlockMove>(slides[pointer])) {
			slide.initiateMove();
		}
		for (Drop drop : new ArrayList<Drop>(drops[pointer])) {
			alignForDrop(drop);
		}
		totalSlidesScheduled -= slides[pointer].size();
		slides[pointer].clear();
		drops[pointer].clear();
		pointer = (pointer + 1) % RING_BUFFER_SIZE;
	}

	public boolean scheduleBlockSlide(Block block, BlockFace direction, Material mat, byte data, boolean immediate) {
		Debugger.getInstance().debug(2, "Schedule slide: " + block + " dir=" + direction + " immediate=" + immediate);
		if (totalSlidesScheduled >= getMaxSlidesTotal() || getMaxSlidesPerTick() <= 0) {
			return false;
		}
		if (isProtectedByWG(block)) {
			return false;
		}

		Slide slide = new Slide(block.getLocation(), direction, mat, data, immediate);
		int delay = immediate ? 1 : plugin.getRandom().nextInt(MAX_SLIDE_DELAY);

		if (scheduleOperation(slide, delay)) {
			slideTo.add(block.getRelative(direction).getLocation());
			return true;
		} else {
			return false;
		}
	}

	public boolean scheduleBlockSlide(Block block, BlockFace direction) {
		return scheduleBlockSlide(block, direction, block.getType(), block.getData(), false);
	}

	public boolean scheduleBlockFling(Block block, Vector vec, Vector offset) {
		int delay =  plugin.getRandom().nextInt(MAX_SLIDE_DELAY);
		Vector offset2 = offset.multiply(0.5);
		Fling fling = new Fling(block.getLocation().add(offset), vec.add(offset2), block.getType(), block.getData());
		if (scheduleOperation(fling, delay)) {
			Debugger.getInstance().debug("scheduled fling: " + block.getLocation() + " -> " + vec);
			return true;
		} else {
			return false;
		}
	}

	private boolean scheduleOperation(ScheduledBlockMove operation, int delay) {
		int idx = (pointer + delay) % RING_BUFFER_SIZE;
		int n = 0;
		while (slides[idx].size() >= getMaxSlidesPerTick()) {
			idx = (idx + 1) % RING_BUFFER_SIZE;
			if (n++ >= RING_BUFFER_SIZE) {
				return false;
			}
		}
		slides[idx].add(operation);
		totalSlidesScheduled++;
		return true;
	}

	public void setMaxSlidesPerTick(int max) {
		maxSlidesPerTick = max;
	}

	public int getMaxSlidesPerTick() {
		return maxSlidesPerTick;
	}

	public void setMaxSlidesTotal(int max) {
		maxSlidesTotal = max;
	}

	public int getMaxSlidesTotal() {
		return maxSlidesTotal;
	}

	private boolean isBraced(Block block, BlockFace face) {
		if (bracingDistance <= 0) {
			return false;
		}
		if (bracingMaterials.contains(getNeighbour(face).getType())) {
			return true;
		}
		if (bracingDistance > 1) {
			for (int i = 2; i <= bracingDistance; i++) {
				if (bracingMaterials.contains(block.getRelative(face, i).getType())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Check if a block would slide, given its position and neighbours.  Note that the
	 * slidiness of the block's material has already been checked at this point.  If the
	 * block can drop down, then BlockFace.DOWN will be returned in preference to any
	 * horizontal direction.
	 *
	 * @param block the block to check
	 * @return the direction in which the block should slide, or null if it should not
	 */
	public BlockFace wouldSlide(Block block) {
		for (BlockFace face : LandslidePlugin.allFaces) {
			neighbours[face.ordinal()] = block.getRelative(face);
			if (isBraced(block, face)) {
				return null;
			} else if (getNeighbour(face).getType() == Material.PISTON_STICKY_BASE && stickyPistonsRetracted) {
				PistonBaseMaterial pbm = (PistonBaseMaterial) getNeighbour(face).getState().getData();
				if (pbm.getFacing() == face.getOppositeFace()) {
					return null;
				}
			} else if (getNeighbour(face).getType() == Material.PISTON_EXTENSION && stickyPistonsExtended) {
				PistonExtensionMaterial pem = (PistonExtensionMaterial) getNeighbour(face).getState().getData();
				if (pem.isSticky() && pem.getFacing() == face.getOppositeFace()) {
					return null;
				}
			} else if (getNeighbour(face).getType() == Material.PISTON_MOVING_PIECE) {
				return null;
			}
		}

		Block below = getNeighbour(BlockFace.DOWN);
		if (!below.getType().isSolid()) {
			return BlockFace.DOWN;
		}
		if (!plugin.getPerWorldConfig().getHorizontalSlides(block.getWorld())) {
			return null;
		}
		Block above = getNeighbour(BlockFace.UP);
		List<BlockFace>	possibles = new ArrayList<BlockFace>();
		for (BlockFace face : LandslidePlugin.horizontalFaces) {
			Block sideBlock = getNeighbour(face);
			if (!below.getRelative(face).getType().isSolid() &&
					!isThickSnowLayer(below.getRelative(face)) &&
					!sideBlock.getType().isSolid() &&
					!above.getRelative(face).getType().isSolid() &&
					!slideTo.contains(sideBlock.getLocation())) {
				possibles.add(face);
			}
		}
		switch (possibles.size()) {
		case 0: return null;
		case 1: return possibles.get(0);
		default: return possibles.get(plugin.getRandom().nextInt(possibles.size()));
		}
	}

	private Block getNeighbour(BlockFace direction) {
		return neighbours[direction.ordinal()];
	}

	private boolean isThickSnowLayer(Block b) {
		return b.getType() == Material.SNOW && b.getData() > 4;
	}

	public void setWorldGuardEnabled(Boolean enabled) {
		this.worldGuardEnabled = enabled;
	}

	public boolean isWorldGuardEnabled() {
		return worldGuardEnabled;
	}

	public void setWorldGuardFlag(String flagName) {
		if (flagName == null || flagName.isEmpty()) {
			return;
		}
		flagName = flagName.replace("-", "");
		for (Flag<?> flag : DefaultFlag.getFlags()) {
			if (flag.getName().replace("-", "").equalsIgnoreCase(flagName)) {
				if (flag instanceof StateFlag) {
					this.wgFlag = (StateFlag) flag;
					return;
				}
            }
        }
		LogUtils.warning("bad value for worldguard.use_flag: " + flagName);
	}

	private void alignForDrop(Drop drop) {
		Location loc = drop.fb.getLocation();
		// align the block neatly on a 0.5 boundary so it will drop cleanly onto the block below,
		// minimising the chance of it breaking and dropping an item
		loc.setX(Math.floor(loc.getX()) + 0.5);
		loc.setZ(Math.floor(loc.getZ()) + 0.5);
		drop.fb.teleport(loc);
		// halt the block's lateral velocity, making it continue straight down
		Vector vec = drop.fb.getVelocity();
		vec.setX(0.0);
		vec.setZ(0.0);
		drop.fb.setVelocity(vec);
	}

	private void scheduleDrop(FallingBlock fb, int delay) {
		if (delay != 0) {
			int idx = (pointer + delay) % RING_BUFFER_SIZE;
			drops[idx].add(new Drop(fb));
		}
	}

	private boolean isProtectedByWG(Block b) {
		if (!plugin.isWorldGuardAvailable() || !worldGuardEnabled) {
			return false;
		}
		return !WGBukkit.getRegionManager(b.getWorld()).getApplicableRegions(b.getLocation()).allows(wgFlag);
	}

	public void setBracingMaterials(List<String> bracingMaterialNames) {
		bracingMaterials.clear();
		for (String s : bracingMaterialNames) {
			try {
				bracingMaterials.add(Material.matchMaterial(s));
			} catch (IllegalArgumentException e) {
				LogUtils.warning("invalid material " + s + " in bracing_materials");
			}
		}
	}

	public void setStickyPistonsRetracted(boolean sticky) {
		stickyPistonsRetracted = sticky;
	}

	public void setStickyPistonsExtended(boolean sticky) {
		stickyPistonsExtended = sticky;
	}

	public void setBracingDistance(Integer bracingDistance) {
		this.bracingDistance = bracingDistance;
	}

	private class Slide implements ScheduledBlockMove {
		private final Location loc;
		private final Material blockMaterial;
		private final byte data;
		private final BlockFace direction;
		private final boolean immediate;

		private Slide(Location loc, BlockFace direction, Material blockType, byte data, boolean immediate) {
			this.direction = direction;
			this.loc = loc;
			this.blockMaterial = blockType;
			this.data = data;
			this.immediate = immediate;
		}

		@Override
		public FallingBlock initiateMove() {
			Block b = loc.getBlock();
			if (wouldSlide(b) == null || (b.getType() != blockMaterial && !immediate)) {
				// sanity check; ensure the block can still slide now
				return null;
			}

			Block above = b.getRelative(BlockFace.UP);
			FallingBlock fb;
			int blockType = 0;
			byte blockData = 0;
			byte fbData = data;

			if (b.getType() == Material.SNOW && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
				// special case; snow can slide off in layers
				fbData = 0; // single layer of snow slides
				if (b.getData() > 0) {
					// leave behind a slightly smaller layer of snow
					blockData = (byte)(b.getData() - 1);
					blockType = Material.SNOW.getId();
				}
			}

			Material fbMaterial = plugin.getPerWorldConfig().getTransform(b.getWorld(), blockMaterial);
			if (fbMaterial == Material.AIR) {
				// spawning falling air blocks makes the client very sad
				return null;
			}

			if (above.getType().isSolid() && direction != BlockFace.DOWN) {
				if (plugin.getRandom().nextInt(100) < plugin.getPerWorldConfig().getCliffStability(b.getWorld())) {
					return null;
				}
				b.setTypeIdAndData(blockType, blockData, true);
				// start with the block out of its hole - can't slide it sideways with a block above
				Block toSide = loc.getBlock().getRelative(direction);
				fb = loc.getWorld().spawnFallingBlock(toSide.getLocation(), fbMaterial, fbData);
				float force = plugin.getRandom().nextFloat() / 2.0f;
				fb.setVelocity(new Vector(direction.getModX() * force, 0.15, direction.getModZ() * force));
			} else {
				b.setTypeIdAndData(blockType, blockData, true);
				fb = loc.getWorld().spawnFallingBlock(loc.add(0.0, direction == BlockFace.DOWN ? 0.0 : 0.15, 0.0), fbMaterial, fbData);
				double x = direction.getModX() / 4.7;
				double z = direction.getModZ() / 4.7;
				fb.setVelocity(new Vector(x, direction == BlockFace.DOWN ? 0.0 : 0.15, z));
			}
			scheduleDrop(fb, (int) (Math.abs((fb.getVelocity().getX() + fb.getVelocity().getZ()) / 0.0354)));
			fb.setDropItem(plugin.getPerWorldConfig().getDropItems(b.getWorld()));
			return fb;
		}
	}

	private class Fling implements ScheduledBlockMove {
		private final Location loc;
		private final Vector vec;
		private final Material blockType;
		private final byte data;

		private Fling(Location location, Vector vec, Material blockType, byte data) {
			this.loc = location;
			this.vec = vec;
			this.blockType = blockType;
			this.data = data;
		}

		@Override
		public FallingBlock initiateMove() {
			loc.getBlock().setType(Material.AIR);
			FallingBlock fb = loc.getWorld().spawnFallingBlock(loc, plugin.getPerWorldConfig().getTransform(loc.getWorld(), blockType), data);
			fb.setVelocity(vec);
			fb.setDropItem(plugin.getPerWorldConfig().getDropItems(loc.getWorld()));
			return fb;
		}
	}

	private class Drop {
		private final FallingBlock fb;
		private Drop (FallingBlock fb) {
			this.fb = fb;
		}
	}
}
