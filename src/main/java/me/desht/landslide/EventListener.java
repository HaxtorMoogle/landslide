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
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

/**
 * Note that all listener methods run with EventPriority.HIGH, giving other plugins a chance
 * to cancel the various events first.
 */
public class EventListener implements Listener {

	private LandslidePlugin plugin;

	public EventListener(LandslidePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			return;
		}
		checkForSlide(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockLanded(EntityChangeBlockEvent event) {
		if (!(event.getEntity() instanceof FallingBlock)) {
			return;
		}
		if (!plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			return;
		}

		Block block = event.getBlock();
		FallingBlock fb = (FallingBlock) event.getEntity();
		LogUtils.fine("falling block landed! " + fb.getMaterial() + " -> " + block);
		if (checkForSlide(block, event.getTo(), event.getData(), true)) {
			// the block continues to slide - don't waste time forming a true block
			// (checkForSlide() has created a new FallingBlock entity)
			event.setCancelled(true);
		} else {
			// the block has landed
			if (plugin.getRandom().nextInt(100) < plugin.getConfig().getInt("explode_effect_chance")) {
				fb.getWorld().createExplosion(fb.getLocation(), 0.0f);
			}
		}

		// see if the block we landed on can be dislodged
		checkForSlide(block.getRelative(BlockFace.DOWN));

		// anything standing in the way?
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

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (!plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			return;
		}
		checkForSlide(event.getBlock());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			return;
		}
		Block above = event.getBlock().getRelative(BlockFace.UP);
		List<Block> l = new ArrayList<Block>();
		List<BlockFace> f = new ArrayList<BlockFace>();
		for (BlockFace face : LandslidePlugin.horizontalFaces) {
			Block b1 = above.getRelative(face);
			if (plugin.getSlideManager().getSlideChance(b1.getType()) > plugin.getRandom().nextInt(100)) {
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

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		if (!explosionEventApplies(event.getEntity())) {
			return;
		}

		// we don't drop items; instead any affected blocks have a chance to become a (high-speed) falling block
		event.setYield(0f);

		Location centre = event.getLocation();

		double distMax0 = 0.0;
		for (Block b : event.blockList()) {
			distMax0 = Math.max(distMax0, b.getLocation().distanceSquared(centre));
		}
		if (distMax0 == 0.0) {
			return;
		}

		// work out a good direction to bias the block flinging - try to send them towards open air
		Block centreBlock = centre.getBlock();
		if (!centreBlock.getType().isSolid()) {
			centreBlock = findNearestSolid(centreBlock);
			centre = centreBlock.getLocation();
		}
		Vector dirModifier = new Vector(0.0, 0.0, 0.0);
		int n = 0;
		for (BlockFace face : LandslidePlugin.allFaces) {
			Block b1 = centreBlock.getRelative(face);
			if (!b1.getType().isSolid()) {
				dirModifier.add(new Vector(face.getModX() * (plugin.getRandom().nextFloat() * 1.0 + 1.0),
				                           face.getModY() * (plugin.getRandom().nextFloat() * 1.0 + 1.0),
				                           face.getModZ() * (plugin.getRandom().nextFloat() * 1.0 + 1.0)));
				n++;
			}
		}
		if (n > 1) {
			dirModifier = dirModifier.multiply(1.0 / n);
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
			plugin.getSlideManager().scheduleBlockFling(b, vec.clone(), dirModifier.clone());
		}
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		SlideOTron wand = SlideOTron.getWand(player);
		if (wand == null) {
			return;
		}
		event.setCancelled(true);
		int quantity = player.getItemInHand().getAmount();
		switch (event.getAction()) {
		case LEFT_CLICK_AIR: case LEFT_CLICK_BLOCK:
			if (player.isSneaking()) {
				wand.setMode(wand.getMode().nextMode());
				player.setItemInHand(wand.toItemStack(quantity));
			} else {
				wand.activate(plugin, player);
			}
			break;
		case RIGHT_CLICK_AIR: case RIGHT_CLICK_BLOCK:
			if (player.isSneaking()) {
				wand.setPower(wand.getPower() - 1);
			} else {
				wand.setPower(wand.getPower() + 1);
			}
			player.setItemInHand(wand.toItemStack(quantity));
			break;
		default:
			break;
		}
	}

	private Block findNearestSolid(Block b) {
		for (BlockFace face : LandslidePlugin.allFaces) {
			Block b1 = b.getRelative(face);
			if (b1.getType().isSolid()) {
				return b1;
			}
		}
		return b;
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
		int chance = plugin.getSlideManager().getSlideChance(mat);
		boolean orphan = plugin.isOrphan(block);
		if (chance > 0 && orphan && plugin.getConfig().getBoolean("drop_slidy_orphans")) {
			return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
		} else if (orphan && plugin.getConfig().getBoolean("drop_nonslidy_orphans")) {
			return plugin.getSlideManager().scheduleBlockSlide(block, BlockFace.DOWN, mat, data, true);
		} else if (chance > plugin.getRandom().nextInt(100)) {
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
