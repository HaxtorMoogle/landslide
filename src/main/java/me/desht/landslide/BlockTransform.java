package me.desht.landslide;

import java.util.HashMap;

import java.util.Map;

import me.desht.dhutils.LogUtils;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class BlockTransform {
	private final Map<Material, Material> map = new HashMap<Material, Material>();

	public BlockTransform() {
	}

	public void clear() {
		map.clear();
	}

	public void processConfig(ConfigurationSection cs) {
		clear();

		for (String m1 : cs.getKeys(false)) {
			String m2 = cs.getString(m1);
			Material mat1 = Material.matchMaterial(m1);
			Material mat2 = m2.isEmpty() ? mat1 : Material.matchMaterial(m2);
			if (mat1 == null || mat2 == null) {
				LogUtils.warning("invalid material transform: " + m1 + " -> " + m2);
			} else {
				add(mat1, mat2);
			}
		}
	}

	public void add(String s1, String s2) {
		Material mat1 = Material.matchMaterial(s1);
		Material mat2 = s2.isEmpty() ? mat1 : Material.matchMaterial(s2);
		if (mat1 == null || mat2 == null) {
			LogUtils.warning("invalid material transform: " + s1 + " -> " + s2);
		} else {
			add(mat1, mat2);
		}
	}

	public void add(Material m1, Material m2) {
		if (m1 == m2) {
			map.remove(m1);
		} else {
			map.put(m1, m2);
		}
	}

	public Material get(Material m) {
		return map.get(m);
	}
}
