package com.architecturoverse.client.screen;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.citizen.CitizenStatus;
import com.architecturoverse.kingdom.ArmyOrder;
import com.architecturoverse.kingdom.BuildOrder;
import com.architecturoverse.network.CitizenCommandPayload;
import com.architecturoverse.network.KingdomActionPayload;
import com.architecturoverse.network.KingdomSnapshotPayload;
import com.architecturoverse.network.KingdomSnapshotPayload.CitizenInfo;
import com.architecturoverse.network.KingdomSnapshotPayload.ConstructionInfo;
import com.architecturoverse.network.KingdomSnapshotPayload.VillageInfo;
import com.architecturoverse.network.VillageOrderPayload;
import com.architecturoverse.structure.StructureType;
import com.architecturoverse.village.Executions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The ruler's book: villages on the left, and tabs for citizens, structures and the army. */
public class KingdomScreen extends Screen {
	private static final int PANEL_WIDTH = 420;
	private static final int PANEL_HEIGHT = 256;
	private static final int VILLAGE_COLUMN = 110;
	private static final int ROW_HEIGHT = 22;
	private static final int ROWS_PER_PAGE = 5;
	private static final int CONTENT_TOP = 58;
	private static final int ALL_VILLAGES = -1;
	/** Height of one row on the structures tab. */
	private static final int STRUCTURE_ROW = 24;

	private enum Tab { CITIZENS, STRUCTURES, ARMY }

	private KingdomSnapshotPayload data;
	private Tab tab = Tab.CITIZENS;
	private int selectedVillage = ALL_VILLAGES;
	private int page;
	private @Nullable UUID highlighted;
	/** Citizen whose job list is open. */
	private @Nullable UUID jobPickerFor;
	/** Citizen whose execution waits for a second click. */
	private @Nullable UUID confirmSentence;
	/** Citizen whose justice menu is open. */
	private @Nullable UUID lawPickerFor;

	public KingdomScreen(KingdomSnapshotPayload data) {
		super(Component.translatable("screen.architecturoverse.kingdom"));
		this.data = data;
		data.focus().ifPresent(this::focusOn);
	}

	/** Called when a new snapshot arrives while the screen is open, e.g. after an order. */
	public void update(KingdomSnapshotPayload newData) {
		this.data = newData;
		newData.focus().ifPresent(this::focusOn);
		rebuildWidgets();
	}

	private void focusOn(UUID citizen) {
		highlighted = citizen;
		data.citizens().stream().filter(c -> c.uuid().equals(citizen)).findFirst().ifPresent(c -> {
			selectedVillage = c.villageId();
			page = Math.max(0, visibleCitizens().indexOf(c) / ROWS_PER_PAGE);
		});
	}

	private List<CitizenInfo> visibleCitizens() {
		return data.citizens().stream()
			.filter(c -> selectedVillage == ALL_VILLAGES || c.villageId() == selectedVillage)
			.toList();
	}

	private Optional<VillageInfo> selectedVillageInfo() {
		return data.villages().stream().filter(v -> v.id() == selectedVillage).findFirst();
	}

	private int pageCount() {
		return Math.max(1, (visibleCitizens().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
	}

	private int left() {
		return (width - PANEL_WIDTH) / 2;
	}

	private int top() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private int contentX() {
		return left() + VILLAGE_COLUMN + 16;
	}

	private int bottom() {
		return top() + PANEL_HEIGHT - 28;
	}

	// ---- widgets -----------------------------------------------------------------------------

	@Override
	protected void init() {
		page = Math.min(page, pageCount() - 1);
		addTabs();
		addVillageList();
		switch (tab) {
			case CITIZENS -> addCitizenTab();
			case STRUCTURES -> addStructureTab();
			case ARMY -> addArmyTab();
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(left() + PANEL_WIDTH - 88, bottom(), 80, 20).build());
	}

	private void addTabs() {
		int x = contentX();
		for (Tab t : Tab.values()) {
			Button button = addRenderableWidget(Button.builder(Component.translatable("screen.architecturoverse.tab." + t.name().toLowerCase()), b -> {
				tab = t;
				jobPickerFor = null;
				lawPickerFor = null;
				confirmSentence = null;
				rebuildWidgets();
			}).bounds(x, top() + 32, 92, 20).build());
			button.active = tab != t;
			x += 96;
		}
	}

	private void addVillageList() {
		int x = left() + 8;
		int y = top() + CONTENT_TOP;
		addVillageButton(x, y, ALL_VILLAGES, Component.translatable("screen.architecturoverse.all_villages", data.citizens().size()));
		y += ROW_HEIGHT;
		for (VillageInfo village : data.villages()) {
			if (y > bottom() - ROW_HEIGHT) {
				break;
			}
			addVillageButton(x, y, village.id(), Component.translatable("screen.architecturoverse.village", village.id(), village.population()));
			y += ROW_HEIGHT;
		}
	}

	private void addVillageButton(int x, int y, int villageId, Component label) {
		Button button = addRenderableWidget(Button.builder(label, b -> {
			selectedVillage = villageId;
			page = 0;
			jobPickerFor = null;
			lawPickerFor = null;
			rebuildWidgets();
		}).bounds(x, y, VILLAGE_COLUMN, 20).build());
		button.active = selectedVillage != villageId;
	}

	private void addCitizenTab() {
		int x = contentX();
		if (jobPickerFor != null) {
			addJobPicker(x, top() + CONTENT_TOP);
			return;
		}
		if (lawPickerFor != null) {
			addLawPicker(x, top() + CONTENT_TOP);
			return;
		}
		int rowY = top() + CONTENT_TOP;
		List<CitizenInfo> citizens = visibleCitizens();
		for (int i = page * ROWS_PER_PAGE; i < Math.min(citizens.size(), (page + 1) * ROWS_PER_PAGE); i++) {
			CitizenInfo citizen = citizens.get(i);
			CitizenJob job = CitizenJob.byId(citizen.job());
			CitizenMode mode = CitizenMode.byId(citizen.mode());
			addRenderableWidget(Button.builder(job.displayName().copy().append(" ▼"), b -> {
					jobPickerFor = citizen.uuid();
					rebuildWidgets();
				})
				.bounds(x + 84, rowY, 86, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.job_hint")))
				.build()).active = CitizenStatus.byId(citizen.status()) == CitizenStatus.FREE;
			addRenderableWidget(Button.builder(mode.displayName(), b -> sendCitizenOrder(citizen, null, mode.next()))
				.bounds(x + 174, rowY, 66, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.mode_hint")))
				.build()).active = CitizenStatus.byId(citizen.status()) == CitizenStatus.FREE;
			addRenderableWidget(Button.builder(Component.literal("⚖"), b -> {
					lawPickerFor = citizen.uuid();
					confirmSentence = null;
					rebuildWidgets();
				})
				.bounds(x + 244, rowY, 42, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.law_hint")))
				.build());
			rowY += ROW_HEIGHT;
		}
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1)).bounds(x, bottom(), 20, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1)).bounds(x + 70, bottom(), 20, 20).build());
		prev.active = page > 0;
		next.active = page < pageCount() - 1;
	}

	/** The justice menu for one citizen: execute (asks twice), prison, or pardon / release. */
	private void addLawPicker(int x, int y) {
		CitizenInfo citizen = data.citizens().stream().filter(c -> c.uuid().equals(lawPickerFor)).findFirst().orElse(null);
		if (citizen == null) {
			lawPickerFor = null;
			return;
		}
		CitizenStatus status = CitizenStatus.byId(citizen.status());
		int rowY = y + 14;
		boolean confirming = citizen.uuid().equals(confirmSentence);
		Button execute = addRenderableWidget(Button.builder(Component.translatable(confirming
					? "screen.architecturoverse.execute_confirm" : "screen.architecturoverse.execute")
					.withStyle(confirming ? ChatFormatting.RED : ChatFormatting.DARK_RED),
				b -> {
					if (citizen.uuid().equals(confirmSentence)) {
						sendSentence(citizen, Executions.Sentence.DEATH);
					} else {
						confirmSentence = citizen.uuid();
						rebuildWidgets();
					}
				})
			.bounds(x, rowY, 200, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.execute_hint")))
			.build());
		execute.active = status != CitizenStatus.CONDEMNED;
		Button prison = addRenderableWidget(Button.builder(Component.translatable("screen.architecturoverse.imprison"),
				b -> sendSentence(citizen, Executions.Sentence.PRISON))
			.bounds(x, rowY + ROW_HEIGHT, 200, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.imprison_hint")))
			.build());
		prison.active = status == CitizenStatus.FREE || status == CitizenStatus.REBEL;
		Button pardon = addRenderableWidget(Button.builder(Component.translatable(status == CitizenStatus.CONDEMNED
					? "screen.architecturoverse.pardon" : "screen.architecturoverse.release").withStyle(ChatFormatting.GREEN),
				b -> sendSentence(citizen, Executions.Sentence.PARDON))
			.bounds(x, rowY + ROW_HEIGHT * 2, 200, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.pardon_hint")))
			.build());
		pardon.active = status != CitizenStatus.FREE;
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
			lawPickerFor = null;
			confirmSentence = null;
			rebuildWidgets();
		}).bounds(x, rowY + ROW_HEIGHT * 3 + 6, 200, 20).build());
	}

	/** The drop-down list of jobs for one citizen. */
	private void addJobPicker(int x, int y) {
		CitizenInfo citizen = data.citizens().stream().filter(c -> c.uuid().equals(jobPickerFor)).findFirst().orElse(null);
		if (citizen == null) {
			jobPickerFor = null;
			return;
		}
		CitizenJob[] jobs = CitizenJob.values();
		for (int i = 0; i < jobs.length; i++) {
			CitizenJob job = jobs[i];
			Button button = addRenderableWidget(Button.builder(job.displayName(), b -> {
				jobPickerFor = null;
				sendCitizenOrder(citizen, job, null);
				rebuildWidgets();
			}).bounds(x + (i % 2) * 140, y + 14 + (i / 2) * ROW_HEIGHT, 136, 20).build());
			button.active = job.ordinal() != citizen.job();
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
			jobPickerFor = null;
			rebuildWidgets();
		}).bounds(x, y + 14 + ((jobs.length + 1) / 2) * ROW_HEIGHT + 6, 136, 20).build());
	}

	private void addStructureTab() {
		Optional<VillageInfo> selected = selectedVillageInfo();
		if (selected.isEmpty()) {
			return;
		}
		VillageInfo village = selected.get();
		int x = contentX() + 160;
		int y = top() + CONTENT_TOP;
		addOrderButton(x, y, village, BuildOrder.WAREHOUSE, village.warehouse());
		addOrderButton(x, y + STRUCTURE_ROW, village, BuildOrder.MINE, false);
		addStructureButton(x, y + STRUCTURE_ROW * 2, village, StructureType.MILITARY_BASE, village.militaryBase());
		addStructureButton(x, y + STRUCTURE_ROW * 3, village, StructureType.PRISON, village.prison());
		addStructureButton(x, y + STRUCTURE_ROW * 4, village, StructureType.POST_OFFICE, village.postOffice());
		Button trader = addRenderableWidget(Button.builder(Component.translatable("screen.architecturoverse.call_trader"),
				b -> ClientPlayNetworking.send(new KingdomActionPayload(KingdomActionPayload.Action.CALL_TRADER, village.id(), 0, Optional.empty())))
			.bounds(x, y + STRUCTURE_ROW * 5, 120, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.call_trader_hint")))
			.build());
		trader.active = village.warehouse() && !village.traderPresent();
	}

	/** "Build" for a building that the villagers put up block by block. */
	private void addStructureButton(int x, int y, VillageInfo village, StructureType type, boolean built) {
		Button button = addRenderableWidget(Button.builder(Component.translatable("screen.architecturoverse.build"),
				b -> ClientPlayNetworking.send(new KingdomActionPayload(KingdomActionPayload.Action.BUILD_STRUCTURE, village.id(),
					type.ordinal(), Optional.empty())))
			.bounds(x, y, 120, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.structure_hint." + type.name().toLowerCase())))
			.build());
		button.active = !built && village.construction().isEmpty();
	}

	/** "Build a warehouse" / "Found a mine": the order goes to the village builders. */
	private void addOrderButton(int x, int y, VillageInfo village, BuildOrder order, boolean alreadyBuilt) {
		boolean queued = village.orders().contains(order.ordinal());
		String key = queued ? "screen.architecturoverse.order_in_progress." : "screen.architecturoverse.order.";
		Button button = addRenderableWidget(Button.builder(Component.translatable(key + order.name().toLowerCase()),
				b -> ClientPlayNetworking.send(new VillageOrderPayload(village.id(), order.ordinal())))
			.bounds(x, y, 120, 20)
			.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.order_hint." + order.name().toLowerCase())))
			.build());
		button.active = !queued && !alreadyBuilt;
	}

	private void addArmyTab() {
		int x = contentX();
		int y = top() + CONTENT_TOP + 40;
		addArmyButton(x, y, ArmyOrder.RALLY, "screen.architecturoverse.army.rally");
		addArmyButton(x, y + 24, ArmyOrder.BASE, "screen.architecturoverse.army.base");
		addArmyButton(x, y + 48, ArmyOrder.PATROL, "screen.architecturoverse.army.patrol");
		selectedVillageInfo().ifPresent(village -> {
			Button rob = addRenderableWidget(Button.builder(Component.translatable("screen.architecturoverse.rob_trader").withStyle(ChatFormatting.GOLD),
					b -> ClientPlayNetworking.send(new KingdomActionPayload(KingdomActionPayload.Action.ROB_TRADER, village.id(), 0, Optional.empty())))
				.bounds(x, y + 76, 180, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.rob_trader_hint")))
				.build());
			rob.active = village.traderPresent();
		});
	}

	private void addArmyButton(int x, int y, ArmyOrder order, String key) {
		addRenderableWidget(Button.builder(Component.translatable(key),
				b -> ClientPlayNetworking.send(new KingdomActionPayload(KingdomActionPayload.Action.ARMY_ORDER, selectedVillage,
					order.ordinal(), Optional.empty())))
			.bounds(x, y, 180, 20)
			.tooltip(Tooltip.create(Component.translatable(key + ".hint")))
			.build());
	}

	private void changePage(int delta) {
		page = Math.max(0, Math.min(pageCount() - 1, page + delta));
		rebuildWidgets();
	}

	private void sendCitizenOrder(CitizenInfo citizen, @Nullable CitizenJob job, @Nullable CitizenMode mode) {
		highlighted = citizen.uuid();
		ClientPlayNetworking.send(new CitizenCommandPayload(citizen.uuid(), job == null ? -1 : job.ordinal(), mode == null ? -1 : mode.ordinal()));
	}

	private void sendSentence(CitizenInfo citizen, Executions.Sentence sentence) {
		confirmSentence = null;
		lawPickerFor = null;
		highlighted = citizen.uuid();
		ClientPlayNetworking.send(new KingdomActionPayload(KingdomActionPayload.Action.SENTENCE, citizen.villageId(), sentence.ordinal(),
			Optional.of(citizen.uuid())));
		rebuildWidgets();
	}

	// ---- drawing -----------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = left();
		int top = top();
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101018);
		graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC9A227);
		graphics.fill(left + VILLAGE_COLUMN + 11, top + 30, left + VILLAGE_COLUMN + 12, top + PANEL_HEIGHT - 8, 0x60FFFFFF);

		graphics.centeredText(font, Component.translatable("screen.architecturoverse.kingdom_of", data.ownerName())
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + PANEL_WIDTH / 2, top + 8, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.summary", data.villages().size(), data.citizens().size()),
			left + PANEL_WIDTH / 2, top + 19, 0xFFAAAAAA);

		switch (tab) {
			case CITIZENS -> extractCitizens(graphics);
			case STRUCTURES -> extractStructures(graphics);
			case ARMY -> extractArmy(graphics);
		}
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.hint." + tab.name().toLowerCase())
			.withStyle(ChatFormatting.ITALIC), left + PANEL_WIDTH / 2, top + PANEL_HEIGHT + 6, 0xFFAAAAAA);

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void extractCitizens(GuiGraphicsExtractor graphics) {
		int x = contentX();
		if (jobPickerFor != null || lawPickerFor != null) {
			int titleY = top() + CONTENT_TOP;
			UUID picked = jobPickerFor != null ? jobPickerFor : lawPickerFor;
			String key = jobPickerFor != null ? "screen.architecturoverse.choose_job" : "screen.architecturoverse.choose_sentence";
			data.citizens().stream().filter(c -> c.uuid().equals(picked)).findFirst().ifPresent(c ->
				graphics.text(font, Component.translatable(key, c.name()).withStyle(ChatFormatting.GOLD), x, titleY, 0xFFFFFFFF));
			return;
		}
		int rowY = top() + CONTENT_TOP;
		List<CitizenInfo> citizens = visibleCitizens();
		if (citizens.isEmpty()) {
			graphics.text(font, Component.translatable("screen.architecturoverse.no_citizens"), x, rowY + 6, 0xFF888888);
		}
		for (int i = page * ROWS_PER_PAGE; i < Math.min(citizens.size(), (page + 1) * ROWS_PER_PAGE); i++) {
			CitizenInfo citizen = citizens.get(i);
			if (citizen.uuid().equals(highlighted)) {
				graphics.fill(x - 3, rowY - 1, x + 288, rowY + 21, 0x40C9A227);
			}
			graphics.text(font, citizenName(citizen), x, rowY + 6, 0xFFFFFFFF);
			rowY += ROW_HEIGHT;
		}
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.page", page + 1, pageCount()), x + 45, bottom() + 6, 0xFFFFFFFF);
		selectedVillageInfo().ifPresent(village -> {
			extractMood(graphics, village, x, bottom() - 52);
			extractStock(graphics, village, x, bottom() - 30);
		});
	}

	/** The name, marked with the citizen's status: † condemned, [prison] prisoners, ⚑ rebels. */
	private static Component citizenName(CitizenInfo citizen) {
		return switch (CitizenStatus.byId(citizen.status())) {
			case FREE -> Component.literal(citizen.name());
			case CONDEMNED -> Component.literal("† " + citizen.name()).withStyle(ChatFormatting.RED);
			case ARRESTED, IMPRISONED -> Component.literal("▦ " + citizen.name()).withStyle(ChatFormatting.GRAY);
			case REBEL -> Component.literal("⚑ " + citizen.name()).withStyle(ChatFormatting.DARK_RED);
		};
	}

	/** Mood, wages and the next payday; a warning while the village is in rebellion. */
	private void extractMood(GuiGraphicsExtractor graphics, VillageInfo village, int x, int y) {
		if (village.rebellion()) {
			graphics.text(font, Component.translatable("screen.architecturoverse.rebellion").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), x, y, 0xFFFFFFFF);
			return;
		}
		ChatFormatting color = village.mood() >= 60 ? ChatFormatting.GREEN : village.mood() >= 35 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		graphics.text(font, Component.translatable("screen.architecturoverse.mood", village.mood()).withStyle(color)
			.append(Component.translatable("screen.architecturoverse.wages", village.dailyWages(), village.ticksToPayday() / 1200)
				.withStyle(ChatFormatting.GRAY)), x, y, 0xFFFFFFFF);
	}

	/** Warehouse and mine status plus the warehouse's biggest stocks. */
	private void extractStock(GuiGraphicsExtractor graphics, VillageInfo village, int x, int y) {
		graphics.text(font, Component.empty().append(warehouseStatus(village)).append("  ").append(mineStatus(village)), x, y - 10, 0xFFFFFFFF);
		int itemX = x;
		for (KingdomSnapshotPayload.StockEntry entry : village.stock()) {
			graphics.item(entry.item(), itemX, y);
			graphics.text(font, compactCount(entry.count()), itemX + 17, y + 9, 0xFFFFFFFF);
			itemX += 40;
		}
	}

	private void extractStructures(GuiGraphicsExtractor graphics) {
		int x = contentX();
		int y = top() + CONTENT_TOP;
		Optional<VillageInfo> selected = selectedVillageInfo();
		if (selected.isEmpty()) {
			graphics.text(font, Component.translatable("screen.architecturoverse.pick_village"), x, y + 6, 0xFF888888);
			return;
		}
		VillageInfo village = selected.get();
		graphics.text(font, warehouseStatus(village), x, y + 6, 0xFFFFFFFF);
		graphics.text(font, mineStatus(village), x, y + STRUCTURE_ROW + 6, 0xFFFFFFFF);
		graphics.text(font, structureStatus(village, StructureType.MILITARY_BASE, village.militaryBase()), x, y + STRUCTURE_ROW * 2 + 6, 0xFFFFFFFF);
		graphics.text(font, structureStatus(village, StructureType.PRISON, village.prison()), x, y + STRUCTURE_ROW * 3 + 6, 0xFFFFFFFF);
		graphics.text(font, structureStatus(village, StructureType.POST_OFFICE, village.postOffice()), x, y + STRUCTURE_ROW * 4 + 6, 0xFFFFFFFF);
		graphics.text(font, Component.translatable(village.traderPresent() ? "screen.architecturoverse.trader_here" : "screen.architecturoverse.trader_none")
			.withStyle(village.traderPresent() ? ChatFormatting.GREEN : ChatFormatting.GRAY), x, y + STRUCTURE_ROW * 5 + 6, 0xFFFFFFFF);
		village.construction().ifPresent(site -> extractConstruction(graphics, site, x, y + STRUCTURE_ROW * 6 + 4));
	}

	/** "Prison: built", "Prison: 45%" or "Prison: none". */
	private static Component structureStatus(VillageInfo village, StructureType type, boolean built) {
		if (built) {
			return Component.translatable("screen.architecturoverse.structure_built", type.displayName()).withStyle(ChatFormatting.GREEN);
		}
		if (village.construction().isPresent() && village.construction().get().type() == type.ordinal()) {
			return Component.translatable("screen.architecturoverse.under_construction", type.displayName(), village.construction().get().percent())
				.withStyle(ChatFormatting.YELLOW);
		}
		return Component.translatable("screen.architecturoverse.structure_none", type.displayName()).withStyle(ChatFormatting.GRAY);
	}

	private void extractConstruction(GuiGraphicsExtractor graphics, ConstructionInfo site, int x, int y) {
		graphics.fill(x, y, x + 280, y + 4, 0xFF333333);
		graphics.fill(x, y, x + 280 * site.percent() / 100, y + 4, 0xFF55AA55);
		graphics.text(font, Component.translatable("screen.architecturoverse.blocks_left", site.woodLeft(), site.stoneLeft()), x, y + 8, 0xFFFFFFFF);
	}

	private void extractArmy(GuiGraphicsExtractor graphics) {
		int x = contentX();
		int y = top() + CONTENT_TOP;
		long soldiers = visibleCitizens().stream().filter(c -> c.job() == CitizenJob.SOLDIER.ordinal()).count();
		graphics.text(font, Component.translatable("screen.architecturoverse.soldiers", soldiers).withStyle(ChatFormatting.GOLD), x, y + 4, 0xFFFFFFFF);
		selectedVillageInfo().ifPresent(village -> graphics.text(font, Component.translatable("screen.architecturoverse.current_order",
			ArmyOrder.byId(village.armyOrder()).displayName()), x, y + 18, 0xFFFFFFFF));
		if (soldiers == 0) {
			graphics.text(font, Component.translatable("screen.architecturoverse.no_soldiers").withStyle(ChatFormatting.GRAY), x, y + 28, 0xFFFFFFFF);
		}
		if (data.villages().stream().anyMatch(v -> v.raidIncoming() && (selectedVillage == ALL_VILLAGES || v.id() == selectedVillage))) {
			graphics.text(font, Component.translatable("screen.architecturoverse.raid_incoming").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				x, y + 144, 0xFFFFFFFF);
		}
	}

	private static Component warehouseStatus(VillageInfo village) {
		return Component.translatable(village.warehouse() ? "screen.architecturoverse.warehouse_yes" : "screen.architecturoverse.warehouse_no")
			.withStyle(village.warehouse() ? ChatFormatting.GREEN : ChatFormatting.RED);
	}

	private static Component mineStatus(VillageInfo village) {
		return village.mineProgress() >= 0
			? Component.translatable("screen.architecturoverse.mine_yes", village.mineProgress()).withStyle(ChatFormatting.GREEN)
			: Component.translatable("screen.architecturoverse.mine_no").withStyle(ChatFormatting.GRAY);
	}

	private static String compactCount(int count) {
		return count >= 10000 ? count / 1000 + "k" : String.valueOf(count);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
