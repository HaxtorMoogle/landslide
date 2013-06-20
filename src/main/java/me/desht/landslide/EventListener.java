package me.desht.landslide;

import java.util.ArrayList;
import java.util.List;

import me.desht.dhutils.LogUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.Vector;

public class EventListener implements Listener {

	private LandslidePlugin plugin;

	public EventListener(LandslidePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockLanded(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof FallingBlock) {
			Block block = event.getBlock();
			if (plugin.getSlideManager().isWorldAffected(block.getWorld().getName())) {
				FallingBlock fb = (FallingBlock) event.getEntity();
				LogUtils.fine("falling block landed! " + fb.getMaterial() + " -> " + block);
				if (checkForSlide(block, event.getTo(), event.getData(), true)) {
					// the block continues to slide
					event.setCancelled(true);
				} else {
					if (plugin.getRandom().nextInt(100) < plugin.getConfig().getInt("explode_effect_chance")) {
						fb.getWorld().createExplosion(fb.getLocation(), 0.0f);
					}
				}
				checkForSlide(block.getRelative(BlockFace.DOWN));
				int dmg = plugin.getConfig().getInt("falling_block_damage");
				if (dmg > 0) {
					Location loc = block.getLocation();
					for (Entity e : loc.getChunk().getEntities()) {
						if (e instanceof LivingEntity) {
							e.getLocation(loc);
							if (loc.getBlockX() == block.getX() && loc.getBlockY() == block.getY() && loc.getBlockZ() == block.getZ()) {
								((LivingEntity) e).damage(dmg);
							}
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			Block above = event.getBlock().getRelative(BlockFace.UP);
			List<Block> l = new ArrayList<Block>();
			List<BlockFace> f = new ArrayList<BlockFace>();
			for (BlockFace face : LandslidePlugin.horizontalFaces) {
				Block b1 = above.getRelative(face);
				if (plugin.getSlideManager().isSlidy(b1.getType())) {
					l.add(b1);
					f.add(face.getOppositeFace());
				}
			}
			switch (l.size()) {
			case 0:
				break;
			case 1:
				plugin.getSlideManager().scheduleBlockSlide(l.get(0), f.get(0));
				break;
			default:
				int idx = plugin.getRandom().nextInt(l.size());
				plugin.getSlideManager().scheduleBlockSlide(l.get(idx), f.get(idx));
				break;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (!explosionEventApplies(event.getEntity())) {
			return;
		}

		// we don't drop items; instead any affected blocks have a chance to become a (high-speed) falling block
		event.setYield(0f);

		final Location centre = event.getLocation();

		double distMax0 = 0.0;
		for (Block b : event.blockList()) {
			distMax0 = Math.max(distMax0, b.getLocation().distanceSquared(centre));
		}
		if (distMax0 == 0.0) {
			return;
		}

		// work out a good direction to bias the block flinging - try to send them towards open air
		Block centreBlock = centre.getBlock();
		Vector dirModifier = new Vector(0.0, 0.0, 0.0);
		for (BlockFace face : LandslidePlugin.allFaces) {
			Block b1 = centreBlock.getRelative(face);
			if (!b1.getType().isSolid()) {
				dirModifier.add(new Vector(face.getModX() * plugin.getRandom().nextFloat() * 2.0,
				                           face.getModY() * plugin.getRandom().nextFloat() * 2.0,
				                           face.getModZ() * plugin.getRandom().nextFloat() * 2.0));
			}
		}

		final double distMax = Math.sqrt(distMax0);
		final double forceMult = plugin.getConfig().getDouble("explosions.force_mult", 1.0);
		final int yieldChance = plugin.getConfig().getInt("explosions.yield_chance", 50);

		LogUtils.fine("explosion: cause = " + event.getEntity() + ", " + event.blockList().size() + " blocks affected, radius = " + distMax);
		for (Block b : event.blockList()) {
			if (plugin.getRandom().nextInt(100) > yieldChance) {
				continue;
			}
			double xOff = b.getX() - centre.getBlockX();
			double yOff = b.getY() - centre.getBlockY();
			double zOff = b.getZ() - centre.getBlockZ();
			double dist = Math.sqrt(xOff * xOff + yOff * yOff + zOff * zOff);
			double power = Math.abs((double)distMax - (double)dist) / 3.0;
			Vector vec = new Vector(xOff, yOff, zOff).normalize().multiply(forceMult * power);
			plugin.getSlideManager().scheduleBlockFling(b, vec, dirModifier);
		}
	}

	private boolean explosionEventApplies(Entity e) {
		if (e instanceof Creeper && plugin.getConfig().getBoolean("fancy_explosions.creeper")) {
			return true;
		} else if (e instanceof TNTPrimed && plugin.getConfig().getBoolean("fancy_explosions.tnt")) {
			return true;
		} else if (e instanceof EnderDragon && plugin.getConfig().getBoolean("fancy_explosions.enderdragon")) {
			return true;
		} else if (plugin.getConfig().getBoolean("fancy_explosions.other")) {
			return true;
		}
		return false;
	}

	private boolean checkForSlide(Block block, Material mat, byte data, boolean immediate) {
		if (plugin.getSlideManager().isSlidy(mat)) {
			BlockFace face = plugin.getSlideManager().wouldSlide(block);
			if (face != null) {
				return plugin.getSlideManager().scheduleBlockSlide(block, face, mat, data, immediate);
			}
		}
		return false;
	}

	private boolean checkForSlide(Block block) {
		return checkForSlide(block, block.getType(), block.getData(), false);
	}

}
