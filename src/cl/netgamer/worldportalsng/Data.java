package cl.netgamer.worldportalsng;

import java.util.List;
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
	
	private Main plugin;
	
	Data(Main plugin)
	{
		this.plugin = plugin;
	}
	
	
	void loadPortals()
	{
		file = new ConfigAccessor(plugin, "data.yml");
		file.saveDefaultConfig();
		conf = file.getConfig();
		
		// BEGIN: DATA CONVERSION FROM MYPORTALS, REMOVE IN LATER VERSIONS
		Location portalLocation;
		float facing;
		for(String name : conf.getKeys(false))
		{
			if (name.contains("+"))
			{
				// old style data format, name means encoded location
				List<String> fields = conf.getStringList(name);
				String[] coords = name.split("(?=[+-])", -1);
				facing = Float.parseFloat(fields.get(0))*90F;
				portalLocation = new Location(		
					Bukkit.getWorlds().get(Integer.parseInt(coords[0])), 
					Double.parseDouble(coords[1]), 
					Double.parseDouble(coords[2])+1, 
					Double.parseDouble(coords[3]),
					0,
					0
				);
				
				// delete old style portal data
				plugin.structureDisable(portalLocation, facing);
				conf.set(name, null);
				file.saveConfig();
				
				// skip unnamed portals, it wasn't used anyway
				name = fields.get(1).replaceAll("[^a-zA-Z0-9-]", "-");
				if (name.isEmpty())
					continue;
				
				// if not overlapping try to build a new style portal, anyway 2 portals together are now pointless
				//if (!plugin.structureBuild(portalLocation, facing, name))
				//	continue;
				
				// save new style portal data... no, in interactive mode portal is saved later anyway
								
				// rebuild portals (emulate interactive)
				
				plugin.testPortalCreation(Bukkit.getConsoleSender(), portalLocation, facing, name, true);
			}
			else
			{
				String encoded = conf.getString(name);
				portalLocation = decodeLocation(encoded);
				facing = Float.parseFloat(encoded.substring(encoded.lastIndexOf('+'), encoded.length()));
				
				// dont rebuild, register blocks instead (non interactive)
				plugin.testPortalCreation(Bukkit.getConsoleSender(), portalLocation, facing, name, false);
			}
		}
		// END: DATA CONVERSION FROM MYPORTALS
		
		
		/* TO DISABLE DATA CONVERSION FROM MYPORTALS IN LATER VERSIONS ALSO UNCOMMENT THIS BLOCK
		// warning, portal location and facing are now splitted
		// every portal location are now block locations, facing are now just used to build portals and find blocks
		// load portals
		String name;
		Location location, key;
		Portal portal;
		for (Entry<String, Object> entry : conf.getValues(false).entrySet())
		{
			name = entry.getKey();
			// if name contains "+" = old format, exit
			location = decodeLocation((String)entry.getValue());
			portal = new Portal(name, location);
			portalsByName.put(name, portal);
			
			key = location.clone();
			key.setYaw(0);
			portalsByLocation.put(key, name);
		}
		*/
	}
	
	
	private Location decodeLocation(String encoded)
	{
		// W+XXXX+YY+ZZZZ+YY.Y
		String[] fields = encoded.split("(?=[+-])");
		return new Location(		
			Bukkit.getWorlds().get(Integer.parseInt(fields[0])), 
			Double.parseDouble(fields[1]), 
			Double.parseDouble(fields[2]), 
			Double.parseDouble(fields[3]),
			0,
			0
		);
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
