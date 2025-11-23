package org.musheck;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

/**
 * Highway Builder Plugin
 *
 * @author musheck
 */
public class MusheckPlugin extends Plugin {
	
	@Override
	public void onLoad() {
		
		//logger
		this.getLogger().info("Musheck poked around here!");
		
		//creating and registering a new module
		final HighwayManager highwayManager = new HighwayManager();
		RusherHackAPI.getModuleManager().registerFeature(highwayManager);
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("Shutting down GPU miner, transferring BTC!");
	}
	
}