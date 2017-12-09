package cl.netgamer.worldportalsng;

import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import cl.netgamer.worldportalsng.ConfigAccessor;
import cl.netgamer.worldportalsng.Main;
import cl.netgamer.worldportalsng.Portal;

class Data
{
	/** this class interacts with data file */
	
	private ConfigAccessor file;
	private FileConfiguration conf;
	// portal location > portal name > actual portal
	
		
	Data(Main plugin)
	{
		file = new ConfigAccessor(plugin, "data.yml");
		file.saveDefaultConfig();
		conf = file.getConfig();
		
		// warning, portal location and facing are now splitted
		// every portal location are now block locations, facing are now just used to build portals and find blocks
		// load portals
		String name;
		String[] fields;
		Location location;
		float facing;
		for (Entry<String, Object> entry : conf.getValues(false).entrySet())
		{
			name = entry.getKey();
			if (name.contains("+"))
			{
				System.out.println("- CRITICAL: Old WorldPortals plugin configuration detected,");
				System.out.println("- CRITICAL: MyPortalsNG v1 is the only version that supports migration.");
				System.out.println("- CRITICAL: To migrate first install and run it, then you can back.");
				System.out.println("- CRITICAL: https://dev.bukkit.org/projects/world-portals-ng/files");
				plugin.stopPlugin = true;
				return;
			}
			
			// W+XXXX+YY+ZZZZ+YYY
			fields = ((String) entry.getValue()).split("(?=[+-])");
			location = new Location(		
				Bukkit.getWorlds().get(Integer.parseInt(fields[0])), 
				Double.parseDouble(fields[1]), 
				Double.parseDouble(fields[2]), 
				Double.parseDouble(fields[3]),
				0,
				0
			);
			facing = Float.parseFloat(fields[4]);
			
			plugin.loadPortal(name, location, facing);
		}
		
	}
	
	
	void savePortal(String name, Location location, float facing)
	{
		String encoded = ""+
			Bukkit.getWorlds().indexOf(location.getWorld())+
			(location.getBlockX() < 0?"":"+")+location.getBlockX()+
			(location.getBlockY() < 0?"":"+")+location.getBlockY()+
			(location.getBlockZ() < 0?"":"+")+location.getBlockZ()+
			"+"+(int)facing;
		conf.set(name, encoded);
		file.saveConfig();
	}
	
	
	void deletePortal(Portal portal)
	{
		conf.set(portal.getName(), null);
		file.saveConfig();
	}
	
}
