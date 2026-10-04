package com.architecturoverse.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How content the village is (0-100), when wages were last paid (game time, -1 = not yet
 * counting) and whether the village is in open rebellion.
 */
public record VillageMood(int mood, long lastPayday, boolean rebellion) {
	public static final int START = 60;
	public static final VillageMood NEW = new VillageMood(START, -1L, false);

	public static final Codec<VillageMood> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("mood").forGetter(VillageMood::mood),
		Codec.LONG.fieldOf("last_payday").forGetter(VillageMood::lastPayday),
		Codec.BOOL.fieldOf("rebellion").forGetter(VillageMood::rebellion)
	).apply(i, VillageMood::new));

	public VillageMood withMood(int newMood) {
		return new VillageMood(Math.max(0, Math.min(100, newMood)), lastPayday, rebellion);
	}

	public VillageMood changeMood(int delta) {
		return withMood(mood + delta);
	}

	public VillageMood withLastPayday(long time) {
		return new VillageMood(mood, time, rebellion);
	}

	public VillageMood withRebellion(boolean newRebellion) {
		return new VillageMood(mood, lastPayday, newRebellion);
	}
}
