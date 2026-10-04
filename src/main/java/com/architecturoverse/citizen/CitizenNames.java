package com.architecturoverse.citizen;

import net.minecraft.util.RandomSource;

public final class CitizenNames {
	private static final String[] NAMES = {
		"Ivan", "Boris", "Oleg", "Igor", "Pavel", "Egor", "Gleb", "Artem", "Denis", "Kirill",
		"Maxim", "Roman", "Timur", "Fedor", "Yakov", "Lev", "Mark", "Ilya", "Stepan", "Savva",
		"Anna", "Olga", "Vera", "Nina", "Elena", "Maria", "Sofia", "Daria", "Yana", "Alisa",
		"Polina", "Irina", "Zoya", "Kira", "Lada", "Mila", "Eva", "Taisia", "Varvara", "Ulyana"
	};

	private CitizenNames() {
	}

	public static String random(RandomSource random) {
		return NAMES[random.nextInt(NAMES.length)];
	}
}
