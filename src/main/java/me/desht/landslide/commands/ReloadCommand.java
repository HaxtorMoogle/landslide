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

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;

public class ReloadCommand extends AbstractCommand {

    public ReloadCommand() {
        super("landslide reload");
        setUsage("/<command> reload");
        setPermissionNode("landslide.commands.reload");
    }

    @Override
    public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
        LandslidePlugin lPlugin = (LandslidePlugin) plugin;
        lPlugin.reloadConfig();
        lPlugin.processConfig();
        lPlugin.getPerWorldConfig().processConfig();
        MiscUtil.statusMessage(sender, "The plugin configuration has been reloaded.");
        return true;
    }

}
