package me.desht.landslide.commands;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class DeleteCfgCommand extends AbstractCommand {

    public DeleteCfgCommand() {
        super("landslide delete", 1, 1);
        setPermissionNode("landslide.commands.delete");
        setUsage("/<command> delete <config-key>");
    }

    private static final Pattern worldPat = Pattern.compile("^worlds\\.(.+?)\\.(.+)");

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        String key = args[0];
        Matcher m = worldPat.matcher(key);
        if (!m.find() || m.groupCount() != 2) {
            throw new DHUtilsException("Only per-world (worlds.world.XXX) keys may be deleted");
        }
        ConfigurationManager configManager = ((LandslidePlugin) plugin).getConfigManager();
        configManager.set(key, (String) null);
        MiscUtil.statusMessage(sender, key + " has been deleted (default will be used)");
        return true;
    }
}
