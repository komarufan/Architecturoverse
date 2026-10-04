package com.architecturoverse.kingdom;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Sends the ruler problem reports from their villages without spamming the same report every tick. */
public final class RulerNotifier {
	private static final long COOLDOWN_TICKS = 20 * 120;
	private static final Map<String, Long> LAST_SENT = new HashMap<>();

	private RulerNotifier() {
	}

	/** Shows {@code key} (with the village id as argument) at most once every two minutes per village. */
	public static void notify(ServerPlayer ruler, int villageId, String key) {
		String id = ruler.getUUID() + "|" + villageId + "|" + key;
		long now = ruler.level().getGameTime();
		Long last = LAST_SENT.get(id);
		if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
			return;
		}
		LAST_SENT.put(id, now);
		ruler.sendSystemMessage(Component.translatable(key, villageId).withStyle(ChatFormatting.YELLOW));
	}
}
