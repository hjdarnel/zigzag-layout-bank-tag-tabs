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

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Varbits;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;

@Slf4j
@PluginDescriptor(name = "Zigzag Layout For Bank Tag Tabs", description = "Right click a bank tag tab, and click \"Enable layout\", then right click again and chose Auto layout: Zigzag", tags = {"bank", "tag", "layout"})
@PluginDependency(BankTagsPlugin.class)
public class ZigzagBankTagTabLayoutPlugin extends Plugin
{
	@Inject
	public Client client;
	@Inject
	public ItemManager itemManager;
	@Inject
	public ConfigManager configManager;
	@Inject
	public ZigzagBankTagTabLayoutConfig config;
	@Inject
	public LayoutManager layoutManager;

	private final LayoutGenerator layoutGenerator = new LayoutGenerator(this);

	@Provides
	ZigzagBankTagTabLayoutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZigzagBankTagTabLayoutConfig.class);
	}

	@Override
	protected void startUp()
	{
		layoutManager.unregisterAutoLayout("Zigzag");
		layoutManager.registerAutoLayout(this, "Zigzag", currentLayout -> {
			List<Integer> equippedGear = getEquippedGear();
			List<Integer> inventory = getInventory();
			if (equippedGear.stream().noneMatch(id -> id > 0) && inventory.stream().noneMatch(id -> id > 0))
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "bla", "This feature uses your equipped items and inventory to automatically create a bank tag layout, but you don't have any items equipped or in your inventory.", "bla");
				return null;
			}

			Layout l = new Layout();
			for (int i = 0; i < currentLayout.getLayout().length; i++)
			{
				int itemId = currentLayout.getLayout()[i];
				if (itemId == -1)
				{
					continue;
				}
				l.putItem(itemId, i);
			}

			Layout previewLayout = layoutGenerator.basicBankTagLayout(equippedGear, inventory, config.autoLayoutIncludeRunePouchRunes() ? getRunePouchRunes() : Collections.emptyList(), Collections.emptyList(), l, getAutoLayoutDuplicateLimit());
			net.runelite.client.plugins.banktags.tabs.Layout l2 = new net.runelite.client.plugins.banktags.tabs.Layout(currentLayout.getTag());
			for (Map.Entry<Integer, Integer> pair : previewLayout.allPairs())
			{
				l2.setItemAtPos(pair.getValue(), pair.getKey());
			}
			return l2;
		});
	}

	private List<Integer> getEquippedGear()
	{
		ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
		if (container == null)
		{
			return Collections.emptyList();
		}
		return Arrays.stream(container.getItems()).map(Item::getId).collect(Collectors.toList());
	}

	/**
	 * empty spaces before an item are always -1, empty spaces after an item may be -1 or may not be included in the
	 * list at all.
	 */
	private List<Integer> getInventory()
	{
		ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
		if (container == null)
		{
			return Collections.emptyList();
		}
		return Arrays.stream(container.getItems()).map(w -> w.getId()).collect(Collectors.toList());
	}

	private static final int[] AMOUNT_VARBITS = {Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4};
	private static final int[] RUNE_VARBITS = {Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4};

	private List<Integer> getRunePouchRunes()
	{
		List<Integer> runes = new ArrayList<>(AMOUNT_VARBITS.length);
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(AMOUNT_VARBITS[i]);
			if (amount <= 0)
			{
				continue;
			}
			int runeId = client.getVarbitValue(RUNE_VARBITS[i]);
			int runeItemId = runepouchEnum.getIntValue(runeId);
			runes.add(runeItemId);
		}
		return runes;
	}

	private int getAutoLayoutDuplicateLimit()
	{
		return !config.autoLayoutDuplicatesEnabled() ? 0 : config.autoLayoutDuplicateLimit();
	}

	int getNonPlaceholderId(int id)
	{
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return (itemComposition.getPlaceholderTemplateId() == 14401) ? itemComposition.getPlaceholderId() : id;
	}

}
