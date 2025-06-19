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

import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

import net.runelite.api.ItemID;
import net.runelite.client.game.ItemVariationMapping;

@Slf4j
@RequiredArgsConstructor
public class LayoutGenerator {
	private final ZigzagBankTagTabLayoutPlugin plugin;

	public Layout basicBankTagLayout(List<Integer> equippedItems, List<Integer> inventory, List<Integer> runePouch, List<Integer> additionalItems, Layout currentLayout, int duplicateLimit) {
		return generateLayout(equippedItems, inventory, runePouch, additionalItems, currentLayout, duplicateLimit);
	}

	public Layout generateLayout(List<Integer> equippedItems, List<Integer> inventory, List<Integer> runePouch, List<Integer> additionalItems, Layout currentLayout, int duplicateLimit) {
		if (!hasRunePouch(inventory)) {
			runePouch = null;
		}
		equippedItems = equippedItems.stream()
			.map(itemId -> plugin.itemManager.canonicalize(itemId)) // Weight reducing items have different ids when equipped; this fixes that.
			.collect(Collectors.toList());

				return zigzagLayout(equippedItems, inventory, runePouch, additionalItems, currentLayout, duplicateLimit);


	}

	private boolean hasRunePouch(List<Integer> inventory)
	{
		Collection<Integer> runePouchVariations = ItemVariationMapping.getVariations(ItemID.RUNE_POUCH);
		Collection<Integer> divineRunePouchVariations = ItemVariationMapping.getVariations(ItemID.DIVINE_RUNE_POUCH);
		return inventory.stream().anyMatch(itemId -> runePouchVariations.contains(itemId) || divineRunePouchVariations.contains(itemId));
	}

	public Layout zigzagLayout(List<Integer> equippedItems, List<Integer> inventory, List<Integer> runePouch, List<Integer> additionalItems, Layout currentLayout, int duplicateLimit) {
		Layout previewLayout = Layout.emptyLayout();
		List<Integer> displacedItems = new ArrayList<>();

		log.debug("generate layout");
		log.debug("equipped gear is " + equippedItems);
		log.debug("inventory is " + inventory);

		int i = 0;

		// lay out equipped items.
		i = layoutItems(equippedItems, currentLayout, previewLayout, displacedItems, i, true);

		inventory = inventory.stream().filter(integer -> integer != -1).collect(Collectors.toList());

		// lay out the inventory items.
		if (duplicateLimit <= 0)
		{
			// distinct leaves the first duplicate it encounters and removes only duplicates coming after the first.
			inventory = inventory.stream().distinct().collect(Collectors.toList());
		}
		else
		{
			inventory = limitDuplicates(inventory, duplicateLimit);
		}

		i = layoutItems(inventory, currentLayout, previewLayout, displacedItems, i, true);

		if (runePouch != null)
		{
			i = layoutItems(runePouch, currentLayout, previewLayout, displacedItems, i, false);
		}

		i = layoutItems(additionalItems, currentLayout, previewLayout, displacedItems, i, false);

		int displacedItemsStart = i;

		// copy items from current layout into the empty spots.
		for (Map.Entry<Integer, Integer> itemPosition : currentLayout.allPairs()) {
			int index = itemPosition.getKey();
			int currentItemAtIndex = itemPosition.getValue();
			int previewItemAtIndex = previewLayout.getItemAtIndex(index);

			if (currentItemAtIndex != -1 && previewItemAtIndex == -1) {
				previewLayout.putItem(currentItemAtIndex, index);
			}
		}

		// Remove items that were placed as part of the gear or inventory.
		displacedItems = displacedItems.stream().filter(id -> !layoutContainsItem(id, previewLayout)).collect(Collectors.toList());

		int j = displacedItemsStart;
		while (displacedItems.size() > 0 && j < 2000 / 38 * 8) {
			int currentItemAtIndex = currentLayout.getItemAtIndex(j);
			if (currentItemAtIndex == -1) {
				Integer itemId = displacedItems.remove(0);
				previewLayout.putItem(itemId, j);
			}

			j++;
		}

		return previewLayout;
	}

	private List<Integer> limitDuplicates(List<Integer> inventory, int duplicateLimit)
	{
		List<Map.Entry<Integer, Integer>> groupedInventory = new ArrayList<>();

		int inARow = 0;
		int lastItemId = -1;
		for (Integer itemId : inventory)
		{
			if (lastItemId != itemId)
			{
				int quantity = inARow > duplicateLimit ? 1 : inARow;
				groupedInventory.add(new AbstractMap.SimpleEntry<>(lastItemId, quantity));
				inARow = 0;
			}
			inARow++;

			lastItemId = itemId;
		}
		int quantity = inARow > duplicateLimit ? 1 : inARow;
		if (quantity > 0) groupedInventory.add(new AbstractMap.SimpleEntry<>(lastItemId, quantity));

		inventory = groupedInventory.stream().flatMap(entry -> Collections.nCopies(entry.getValue(), entry.getKey()).stream()).collect(Collectors.toList());
		return inventory;
	}

	private int layoutItems(List<Integer> inventory, Layout currentLayout, Layout previewLayout, List<Integer> displacedItems, int i, boolean useZigZag) {
		for (Integer itemId : inventory) {
			if (itemId == -1) continue;
			int index = useZigZag ? toZigZagIndex(i, 0, 0) : i;
			previewLayout.putItem(itemId, index);
			int currentLayoutItem = currentLayout.getItemAtIndex(index);
			if (currentLayoutItem != -1) displacedItems.add(currentLayoutItem);
			i++;
		}
		if (!inventory.isEmpty()) {
			Optional<Integer> highestUsedIndex = previewLayout.getAllUsedIndexes().stream().max(Integer::compare);
			if (highestUsedIndex.isPresent()) {
				if (useZigZag) {
					i = (highestUsedIndex.get() / 16 * 2 + 2) * 8;
				} else {
					i = (highestUsedIndex.get() / 8 + 1) * 8;
				}
			}
		}
		return i;
	}

	private boolean layoutContainsItem(int id, Layout previewLayout) {
		int baseId = ItemVariationMapping.map(plugin.getNonPlaceholderId(id));
		for (Integer item : previewLayout.getAllUsedItemIds()) {
			if (baseId == ItemVariationMapping.map(plugin.getNonPlaceholderId(item))) {
				return true;
			}
		}
		return false;
	}

	private static int toZigZagIndex(int inventoryIndex, int row, int col) {
		if (inventoryIndex < 0 || row < 0 || col < 0) throw new IllegalArgumentException();

		row += (inventoryIndex / 16) * 2; // Does this cover multiple pairs of rows?
		inventoryIndex -= (inventoryIndex / 16) * 16;
		int index = 0;
		index += inventoryIndex % 2 == 0 ? 0 : 8; // top or bottom row?
		index += inventoryIndex / 2; // column.
		index += row * 8 + col; // offset.
		return index;
	}
}
