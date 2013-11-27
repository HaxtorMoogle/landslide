package me.desht.landslide;

import java.util.HashMap;
import java.util.Map;

import me.desht.dhutils.LogUtils;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;

/**
 * Cache the per-world parameters.  We cache the parameters here when the plugin starts or when
 * the configuration changes to avoid the lookup overhead of getting parameters from the plugin
 * configuration.
 */
public class PerWorldConfiguration {
	private static final String WORLD_DEFAULTS = "*DEFAULT";

	private final Map<String, WorldParams> worlds = new HashMap<String, PerWorldConfiguration.WorldParams>();

	private final LandslidePlugin plugin;
	private WorldParams defaultWorld;

	public PerWorldConfiguration(LandslidePlugin plugin) {
		this.plugin = plugin;

		processConfig();
	}

	public void processConfig() {
		Configuration pConf = plugin.getConfig();

		worlds.clear();

		for (String key : pConf.getKeys(true)) {
			if (!pConf.isConfigurationSection(key)) {
				processKey(pConf, key);
			}
		}
		defaultWorld = worlds.get(WORLD_DEFAULTS);
		defaultWorld.sanityCheck();
	}

	public void processKey(Configuration conf, String fullKey) {
		String[] parts = fullKey.split("\\.");
		String worldName = WORLD_DEFAULTS;
		String key, subKey;
		if (parts[0].equals("worlds")) {
			worldName = parts[1];
			key = parts[2];
			subKey = parts.length >= 4 ? parts[3] : null;
		} else {
			key = parts[0];
			subKey = parts.length >= 2 ? parts[1] : null;
		}
		LogUtils.fine("process key [" + fullKey + "]: world=" + worldName + ", key=" + key + ", val=" + conf.get(fullKey));
		if (!conf.contains(fullKey)) {
			// config key has been deleted; re-read everything and reconstruct the cached config
			// this is not the most efficient way, but should be pretty safe
			processConfig();
		} else if (key.equals("enabled")) {
			getWorldParams(worldName).setEnabled(conf.getBoolean(fullKey));
		} else if (key.equals("cliff_stability")) {
			getWorldParams(worldName).setCliffStability(conf.getInt(fullKey));
		} else if (key.equals("slide_chance")) {
			getWorldParams(worldName).setSlideChance(subKey, conf.getInt(fullKey));
		} else if (key.equals("drop_chance")) {
			getWorldParams(worldName).setDropChance(subKey, conf.getInt(fullKey));
		} else if (key.equals("drop_items")) {
			getWorldParams(worldName).setDropItems(conf.getBoolean(fullKey));
		} else if (key.equals("transform")) {
			getWorldParams(worldName).setTransform(subKey, conf.getString(fullKey));
		} else if (key.equals("falling_block_damage")) {
			getWorldParams(worldName).setFallingBlockDamage(conf.getInt(fullKey));
		} else if (key.equals("explode_effect_chance")) {
			getWorldParams(worldName).setExplodeEffectChance(conf.getInt(fullKey));
		} else if (key.equals("falling_blocks_bounce")) {
			getWorldParams(worldName).setFallingBlocksBounce(conf.getBoolean(fullKey));
		} else if (key.equals("horizontal_slides")) {
			getWorldParams(worldName).setHorizontalSlides(conf.getBoolean(fullKey));
		} else if (key.equals("snow")) {
			if (subKey.equals("form_chance")) {
				getWorldParams(worldName).setSnowFormChance(conf.getInt(fullKey));
			} else if (subKey.equals("melt_chance")) {
				getWorldParams(worldName).setSnowMeltChance(conf.getInt(fullKey));
			} else if (subKey.equals("slide_thickness")) {
				getWorldParams(worldName).setSnowSlideThickness(conf.getInt(fullKey));
			}
		}
	}

	private WorldParams getWorldParams(String worldName) {
		if (!worlds.containsKey(worldName)) {
			worlds.put(worldName, new WorldParams());
		}
		return worlds.get(worldName);
	}

	public boolean isEnabled(World world) {
		return getWorldParams(world.getName()).isEnabled();
	}

	public int getCliffStability(World world) {
		return getWorldParams(world.getName()).getCliffStability();
	}

	public int getSlideChance(World world, Material mat) {
		return getWorldParams(world.getName()).getSlideChance(mat);
	}

	public int getDropChance(World world, Material mat) {
		return getWorldParams(world.getName()).getDropChance(mat);
	}

	public boolean getDropItems(World world) {
		return getWorldParams(world.getName()).getDropItems();
	}

	public Material getTransform(World world, Material mat) {
		return getWorldParams(world.getName()).getTransform(mat);
	}

	public int getExplodeEffectChance(World world) {
		return getWorldParams(world.getName()).getExplodeEffectChance();
	}

	public boolean getFallingBlocksBounce(World world) {
		return getWorldParams(world.getName()).getFallingBlocksBounce();
	}

	public int getFallingBlockDamage(World world) {
		return getWorldParams(world.getName()).getFallingBlockDamage();
	}

	public boolean getHorizontalSlides(World world) {
		return getWorldParams(world.getName()).getHorizontalSlides();
	}

	public int getSnowFormChance(World world) {
		return getWorldParams(world.getName()).getSnowFormChance();
	}

	public int getSnowMeltChance(World world) {
		return getWorldParams(world.getName()).getSnowMeltChance();
	}

	public int getSnowSlideThickness(World world) {
		return getWorldParams(world.getName()).getSnowSlideThickness();
	}

	private class WorldParams {
		private Integer cliffStability = null;
		private Integer fallingBlockDamage = null;
		private Boolean fallingBlocksBounce = null;
		private Integer explodeEffectChance = null;
		private Boolean enabled = null;
		private Boolean dropItems = null;
		private Boolean horizontalSlides = null;
		private final Map<Material,Integer> slideChances = new HashMap<Material,Integer>();
		private final Map<Material,Integer> dropChances = new HashMap<Material,Integer>();
		private final BlockTransform transforms = new BlockTransform();
		private Integer snowMeltChance = null;
		private Integer snowFormChance = null;
		private Integer snowSlideThickness = null;

		private WorldParams() {
		}

		public void sanityCheck() {
			// called for the default settings; ensure nothing is null here
			// (shouldn't ever happen, unless the config gets corrupted)
			if (cliffStability == null) cliffStability = 50;
			if (fallingBlockDamage == null) fallingBlockDamage = 0;
			if (fallingBlocksBounce == null) fallingBlocksBounce = true;
			if (explodeEffectChance == null) explodeEffectChance = 5;
			if (horizontalSlides == null) horizontalSlides = true;
			if (enabled == null) enabled = false;
			if (dropItems == null) dropItems = true;
			if (snowFormChance == null) snowFormChance = 15;
			if (snowMeltChance == null) snowMeltChance = 15;
			if (snowSlideThickness == null) snowSlideThickness = 2;
		}

		public boolean getHorizontalSlides() {
			return horizontalSlides == null ? defaultWorld.getHorizontalSlides() : horizontalSlides;
		}

		public void setHorizontalSlides(boolean horizontalSlides) {
			this.horizontalSlides = horizontalSlides;
		}

		public void setTransform(String s1, String s2) {
			transforms.add(s1, s2);
		}

		public Material getTransform(Material mat) {
			Material res = transforms.get(mat);
			return res == null ? defaultWorld.getTransform(mat, mat) : res;
		}

		private Material getTransform(Material mat, Material def) {
			Material res = transforms.get(mat);
			return res == null ? def : res;
		}

		public void setDropItems(boolean dropItems) {
			this.dropItems = dropItems;
		}

		public boolean getDropItems() {
			return dropItems == null ? defaultWorld.getDropItems() : dropItems;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isEnabled() {
			return enabled == null ? defaultWorld.isEnabled() : enabled;
		}

		public void setCliffStability(int cliffStability) {
			this.cliffStability = cliffStability;
		}

		public int getCliffStability() {
			return cliffStability == null ? defaultWorld.getCliffStability() : cliffStability;
		}

		public int getSlideChance(Material mat) {
			return slideChances.containsKey(mat)? slideChances.get(mat) : defaultWorld.getSlideChance(mat, 0);
		}

		public void setSlideChance(String matName, int chance) {
			Material mat = Material.matchMaterial(matName);
			if (mat != null) {
				slideChances.put(mat, chance);
			}
		}

		private int getSlideChance(Material mat, int def) {
			return slideChances.containsKey(mat)? slideChances.get(mat) : def;
		}

		public void setDropChance(String matName, int chance) {
			Material mat = Material.matchMaterial(matName);
			if (mat != null) {
				dropChances.put(mat, chance);
			}
		}

		public int getDropChance(Material mat) {
			return dropChances.containsKey(mat)? dropChances.get(mat) : defaultWorld.getDropChance(mat, 0);
		}

		private int getDropChance(Material mat, int def) {
			return dropChances.containsKey(mat)? dropChances.get(mat) : def;
		}

		public int getExplodeEffectChance() {
			return explodeEffectChance == null ? defaultWorld.getExplodeEffectChance() : explodeEffectChance;
		}

		public void setExplodeEffectChance(int explodeEffectChance) {
			this.explodeEffectChance = explodeEffectChance;
		}

		public boolean getFallingBlocksBounce() {
			return fallingBlocksBounce == null ? defaultWorld.getFallingBlocksBounce() : fallingBlocksBounce;
		}

		public void setFallingBlocksBounce(boolean fallingBlocksBounce) {
			this.fallingBlocksBounce = fallingBlocksBounce;
		}

		public int getFallingBlockDamage() {
			return fallingBlockDamage == null ? defaultWorld.getFallingBlockDamage() : fallingBlockDamage;
		}

		public void setFallingBlockDamage(int fallingBlockDamage) {
			this.fallingBlockDamage = fallingBlockDamage;
		}

		public int getSnowFormChance() {
			return snowFormChance == null ? defaultWorld.getSnowFormChance() : snowFormChance;
		}

		public void setSnowFormChance(int snowFormChance) {
			this.snowFormChance = snowFormChance;
		}

		public int getSnowMeltChance() {
			return snowMeltChance == null ? defaultWorld.getSnowMeltChance() : snowMeltChance;
		}

		public void setSnowMeltChance(int snowMeltChance) {
			this.snowMeltChance = snowMeltChance;
		}

		public int getSnowSlideThickness() {
			return snowSlideThickness == null ? defaultWorld.getSnowSlideThickness() : snowSlideThickness;
		}

		public void setSnowSlideThickness(int snowSlideThickness) {
			this.snowSlideThickness = snowSlideThickness;
		}
	}
}
