package com.hjdarnel.ZigzagBankTagTabLayoutPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ZigzagBankTagTabLayoutPlugin.class);
		RuneLite.main(args);
	}
}