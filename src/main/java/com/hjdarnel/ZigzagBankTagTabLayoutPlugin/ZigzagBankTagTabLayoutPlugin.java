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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Data;
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
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
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

	@Data
	public static class FakeItem {
		public final int index;
		public final int itemId;
		public final boolean layoutPlaceholder;
		public final int quantity;
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

	private int getPlaceholderId(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return (itemComposition.getPlaceholderTemplateId() == 14401) ? id : itemComposition.getPlaceholderId();
	}

	int switchPlaceholderId(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return itemComposition.getPlaceholderId();
	}

	public boolean isPlaceholder(int id) {
		ItemComposition itemComposition = itemManager.getItemComposition(id);
		return itemComposition.getPlaceholderTemplateId() == 14401;
	}

	public String itemNameWithId(Integer itemId) {
		return ((itemId == null) ? "null" : itemManager.getItemComposition(itemId).getName()) + " (" + itemId + (isPlaceholder(itemId) ? ",ph" : "") + ")";
	}

	/**
	 * Whether this item should be treated as having variants for the purpose of custom bank layouts.
	 * If true, this means that the item should occupy the next available position in the custom layout which matches either its own id or any of its variants.
	 * This includes placeholders for the item.
	 * This does mean that the order that items appear in the normal bank has an impact on the custom layout. Not something you'd expect from this feature, lol.
	 */
	boolean itemShouldBeTreatedAsHavingVariants(int nonPlaceholderItemId) {
		return itemHasVariants(nonPlaceholderItemId);
	}

	private boolean itemHasVariants(int nonPlaceholderItemId) {
		return ItemVariationMapping.getVariations(ItemVariationMapping.map(nonPlaceholderItemId)).size() > 1;
	}

	private int getVariationBaseId(int nonPlaceholderId)
	{
		int runeliteBaseId = ItemVariationMapping.map(nonPlaceholderId);
		if (runeliteBaseId == 713) {
			ItemComposition itemComposition = itemManager.getItemComposition(nonPlaceholderId);
			int iconId = itemComposition.getInventoryModel();
			if (iconId == 37162) { // beginner
				return nonPlaceholderId; // All share the same id.
			}
			else if (iconId == 37202) { // easy
				return 2677; // Lowest id of this clue type.
			}
			else if (iconId == 37152) { // medium
				return 2801; // Lowest id of this clue type.
			}
			else if (iconId == 37181) { // hard
				return 2722; // Lowest id of this clue type.
			}
			else if (iconId == 37167) { // elite
				return 12073; // Lowest id of this clue type.
			}
			else if (iconId == 37183) { // master
				return nonPlaceholderId; // All share the same id.
			}
			// this is either a (likely unobtainable) pink skirt or a sote quest item. I don't care how either of these items are handled.
		}
		return runeliteBaseId;
	}

	@FunctionalInterface
	private interface functionalinterfacetrashname {
		int getIndex(Collection<Integer> itemIds, int itemId);
	}

	private void assignitemstrashname(Map<Integer, Widget> indexToWidget, Multimap<Integer, Integer> variantItemsInLayout, Integer variationBaseId, List<Widget> notYetPositionedWidgets, functionalinterfacetrashname getIndex, String debugDescription)
	{
		Iterator<Widget> iter = notYetPositionedWidgets.iterator();
		while (iter.hasNext()) {
			Widget widget = iter.next();
			int itemId = widget.getItemId();

			Collection<Integer> itemIds = variantItemsInLayout.get(variationBaseId);
			if (itemIds == null) continue; // this could happen because I removed all the widgets at this key.

			int index = getIndex.getIndex(itemIds, itemId);

			if (index != -1 && !indexToWidget.containsKey(index)) {
				log.debug("item " + itemNameWithId(itemId) + " assigned on " + debugDescription + " to index " + index);
				indexToWidget.put(index, widget);
				iter.remove();
			}
		}
	}

	// TODO this logic needs looking at re: barrows items.
	private void assignVariantItemPositions(Layout layout, List<Widget> bankItems, Map<Integer, Widget> indexToWidget) {
		// Remove duplicate item id widgets.
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		bankItems = new ArrayList<>(bankItems.stream().filter(widget -> seen.add(widget.getItemId())).collect(Collectors.toList()));

		Multimap<Integer, Widget> variantItemsInBank = LinkedListMultimap.create(); // key is the variant base id; the list contains the item widgets that go in this variant base id;
		for (Widget bankItem : bankItems) {
			int nonPlaceholderId = getNonPlaceholderId(bankItem.getItemId());
			if (itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
				int variationBaseId = getVariationBaseId(nonPlaceholderId);
				variantItemsInBank.put(variationBaseId, bankItem);
			}
		}

		Multimap<Integer, Integer> variantItemsInLayout = LinkedListMultimap.create(); // key is the variant base id; the list contains the item ids;
		for (Map.Entry<Integer, Integer> pair : layout.allPairs()) {
			int nonPlaceholderId = getNonPlaceholderId(pair.getValue());
			if (itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
				int variationBaseId = getVariationBaseId(nonPlaceholderId);
				variantItemsInLayout.put(variationBaseId, pair.getValue());
			}
		}

		for (Integer variationBaseId : variantItemsInBank.keySet()) {
			List<Widget> notYetPositionedWidgets = new ArrayList<>(variantItemsInBank.get(variationBaseId));

			// first, figure out if there is a perfect match.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> itemIdsInLayoutForVariant.contains(itemId) ? layout.getIndexForItem(itemId) : -1, "pass 1 (exact itemid match)");

			// check matches of placeholders or placeholders matching items.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> {
				itemId = switchPlaceholderId(itemId);
				return itemIdsInLayoutForVariant.contains(itemId) ? layout.getIndexForItem(itemId) : -1;
			}, "pass 2 (placeholder match)");

			// match any variant item.
			assignitemstrashname(indexToWidget, variantItemsInLayout, variationBaseId, notYetPositionedWidgets, (itemIdsInLayoutForVariant, itemId) -> {
				for (Integer id : itemIdsInLayoutForVariant) {
					int index = layout.getIndexForItem(id);
					if (!indexToWidget.containsKey(index)) {
						return index;
					}
				}
				return -1;
			}, "pass 3 (variant item match)");

			if (!notYetPositionedWidgets.isEmpty()) {
				for (Widget notYetPositionedWidget : notYetPositionedWidgets) {
					int itemId = notYetPositionedWidget.getItemId();
					int layoutIndex = layout.getIndexForItem(itemId);
					if (layoutIndex != -1) continue; // Prevents an issue where items with the same id that take up multiple bank slots, e.g. items that have their charges stored on the item, can be added into two slots during this stage.
					int index = layout.getFirstEmptyIndex();
					layout.putItem(itemId, index);
					log.debug("item " + itemNameWithId(itemId) + " assigned on pass 4 (assign to empty spot) to index " + index);
					indexToWidget.put(index, notYetPositionedWidget);
				}
			}
		}
	}

	private void assignNonVariantItemPositions(Layout layout, List<Widget> bankItems, Map<Integer, Widget> indexToWidget) {
		for (Widget bankItem : bankItems) {
			int itemId = bankItem.getItemId();

			int nonPlaceholderId = getNonPlaceholderId(itemId);

			if (!itemShouldBeTreatedAsHavingVariants(nonPlaceholderId)) {
//				log.debug("\tassigning position for " + itemName(itemId) + itemId + ": ");

				Integer indexForItem = layout.getIndexForItem(itemId);
				if (indexForItem == -1) {
					// swap the item with its placeholder (or vice versa) and try again.
					int otherItemId = switchPlaceholderId(itemId);
					indexForItem = layout.getIndexForItem(otherItemId);
				}

				if (indexForItem == -1) {
					// The item is not in the layout.
					indexForItem = layout.getFirstEmptyIndex();
					layout.putItem(itemId, indexForItem);
				}
				indexToWidget.put(indexForItem, bankItem);
			}
		}
	}

	/**
	 * Generates a map of widgets to the bank indexes where they should show up in the laid-out tag. Does not update fake items.
	 */
	Map<Integer, Widget> assignItemPositions(Layout layout, List<Widget> bankItems)
	{
		Map<Integer, Widget> indexToWidget = new HashMap<>();
		assignVariantItemPositions(layout, bankItems, indexToWidget);
		// TODO check if the existance of this method is just a performance boost.
		assignNonVariantItemPositions(layout, bankItems, indexToWidget);
		return indexToWidget;
	}

	// TODO this is n^2. There are multiple places I think where I do such an operation, so doing something about this would be nice.
	Set<FakeItem> calculateFakeItems(Layout layout, Map<Integer, Widget> indexToWidget)
	{
		Set<FakeItem> fakeItems = new HashSet<>();
		for (Map.Entry<Integer, Integer> entry : layout.allPairs()) {
			Integer index = entry.getKey();
			if (indexToWidget.containsKey(index)) continue;

			int itemId = entry.getValue();
			Optional<Widget> any = layout.allPairs().stream()
				.filter(e -> e.getValue() == itemId)
				.map(e -> indexToWidget.get(e.getKey()))
				.filter(widget -> widget != null)
				.findAny();

			boolean isLayoutPlaceholder = !any.isPresent();
			int quantity = any.isPresent() ? any.get().getItemQuantity() : -1;
			int fakeItemItemId = any.isPresent() ? any.get().getItemId() : itemId;
//			fakeItems.add(new FakeItem(index, getNonPlaceholderId(fakeItemItemId), isLayoutPlaceholder, quantity));
			fakeItems.add(new FakeItem(index, fakeItemItemId, isLayoutPlaceholder, quantity));
		}
		return fakeItems;
	}

}
