package com.splitter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface SplitterConfig extends Config
{
	@ConfigItem(
		keyName = "defaultSplitSize",
		name = "Default split size",
		description = "The default number of people to split among"
	)
	default int splitSize()
	{
		return 2;
	}
}
