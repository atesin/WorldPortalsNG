package cl.netgamer.worldportalsng;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PortalCommand implements CommandExecutor
{
	
	private Main plugin;
	private String helpPage;
	
	
	public PortalCommand(Main plugin)
	{
		this.plugin = plugin;
		helpPage = "\u00A7E"+plugin.getConfig().getString("helpPage").replaceAll("\r?\n", "\n\u00A7B");
	}
	
	
	public boolean onCommand(CommandSender sender, Command cmd, String alias, String[] args)
	{
		/*
		/portal = displays portals list, all portals, page 1
		/portal [pattern?] [num] = displays portals list, which name contains "pattern", page "num"
		/portal [from] <to> = print the path to reach [from portal "from"] to portal "to"
		/portal ? = help page
		
		2 modes
		- list
		- pathfinding
		*/
		
		
		
		if (args.length == 1 && args[0].equals("?"))
		{
			sender.sendMessage(helpPage);
			return true;
		}
		
		
		// check valid parameter count
		if (args.length > 2)
			return plugin.sendMessage(sender, "tooManyParameters");
		
		
		// defaults
		char action = 'U'; // U: undefined, L: list, P: pathfinding
		int page = -1, num;
		String arg, origin = "", destiny = "";
		
		
		// instead of shift array, walk ahead
		for (int index = 0 ; index < args.length ; ++index)
		{
			arg = args[index];
			try
			{
				num = Integer.parseInt(arg);
				if (action == 'P')
					return plugin.sendMessage(sender, "incompatibleParameters");
				if (page > 0)
					return plugin.sendMessage(sender, "ambiguousPage");
				page = num;
				action = 'L';
			}
			catch (NumberFormatException e)
			{
				if (arg.endsWith("?"))
				{
					if (action == 'P')
						return plugin.sendMessage(sender, "incompatibleParameters");
					if (!destiny.isEmpty())
						return plugin.sendMessage(sender, "ambiguousPattern");
					destiny = arg.substring(0, arg.length()-1);
					action = 'L';
				}
				else
				{
					if (action == 'L')
						return plugin.sendMessage(sender, "incompatibleParameters");
					origin = destiny;
					destiny = arg;
					
					action = 'P';
				}
			}
		}
		
		
		// pathfinding
		if (action == 'P')
		{
			plugin.pathFind(sender, origin, destiny);
			return true;
		}
		
		
		// list portals, sanitize input data before
		Location location = null;
		if (sender instanceof Player)
		{
			location = ((Player)sender).getLocation();
			page = Math.max(1, page);
		}
		plugin.listPortals(sender, location, page, destiny);
		return true;
		
	}
	
}
