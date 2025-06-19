/*
 * Copyright (c) 2021, geheur
 * Copyright (c) 2025, hjdarnel
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.hjdarnel.ZigzagBankTagTabLayoutPlugin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ZigzagBankTagTabLayoutPlugin")
public interface ZigzagBankTagTabLayoutConfig extends Config
{
	@ConfigItem(
		keyName = "autoLayoutDuplicatesEnabled",
		name = "Create duplicates",
		description = "Whether or not to create duplicates when there are multiple of the same item when using auto-layout.",
		position = 0
	)
	default boolean autoLayoutDuplicatesEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoLayoutDuplicateLimit",
		name = "Duplicate limit",
		description = "The maximum number of items in a row to create duplicates for with auto-layout. Set to 28 to create duplicates for every item. To disable duplicate creation, toggle the \"Create duplicates\" option off.",
		position = 1
	)
	default int autoLayoutDuplicateLimit()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "autoLayoutIncludeRunePouchRunes",
		name = "Include Rune Pouch Runes",
		description = "Include the runes in the rune pouch when making the layout. The runes will also not be added to the tag.",
		position = 2
	)
	default boolean autoLayoutIncludeRunePouchRunes()
	{
		return true;
	}
}
