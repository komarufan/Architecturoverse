package com.architecturoverse.client.screen;

import com.architecturoverse.citizen.CitizenJob;
import com.architecturoverse.citizen.CitizenMode;
import com.architecturoverse.network.CitizenCommandPayload;
import com.architecturoverse.network.KingdomSnapshotPayload;
import com.architecturoverse.network.KingdomSnapshotPayload.CitizenInfo;
import com.architecturoverse.network.KingdomSnapshotPayload.VillageInfo;
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

/** The ruler's book: villages on the left, citizens of the selected village on the right. */
public class KingdomScreen extends Screen {
	private static final int PANEL_WIDTH = 380;
	private static final int PANEL_HEIGHT = 256;
	private static final int VILLAGE_COLUMN = 110;
	private static final int ROW_HEIGHT = 22;
	private static final int ROWS_PER_PAGE = 6;
	private static final int ALL_VILLAGES = -1;

	private KingdomSnapshotPayload data;
	private int selectedVillage = ALL_VILLAGES;
	private int page;
	private @Nullable UUID highlighted;

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
			int index = visibleCitizens().indexOf(c);
			page = Math.max(0, index / ROWS_PER_PAGE);
		});
	}

	private List<CitizenInfo> visibleCitizens() {
		return data.citizens().stream()
			.filter(c -> selectedVillage == ALL_VILLAGES || c.villageId() == selectedVillage)
			.toList();
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

	@Override
	protected void init() {
		page = Math.min(page, pageCount() - 1);
		int x = left() + 8;
		int y = top() + 40;

		addVillageButton(x, y, ALL_VILLAGES, Component.translatable("screen.architecturoverse.all_villages", data.citizens().size()));
		y += ROW_HEIGHT;
		for (VillageInfo village : data.villages()) {
			if (y > top() + PANEL_HEIGHT - 50) {
				break;
			}
			addVillageButton(x, y, village.id(), Component.translatable("screen.architecturoverse.village", village.id(), village.population()));
			y += ROW_HEIGHT;
		}

		int listX = left() + VILLAGE_COLUMN + 16;
		int rowY = top() + 40;
		List<CitizenInfo> citizens = visibleCitizens();
		for (int i = page * ROWS_PER_PAGE; i < Math.min(citizens.size(), (page + 1) * ROWS_PER_PAGE); i++) {
			CitizenInfo citizen = citizens.get(i);
			CitizenJob job = CitizenJob.byId(citizen.job());
			CitizenMode mode = CitizenMode.byId(citizen.mode());
			addRenderableWidget(Button.builder(job.displayName(), b -> sendOrder(citizen, job.next(), null))
				.bounds(listX + 90, rowY, 80, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.job_hint")))
				.build());
			addRenderableWidget(Button.builder(mode.displayName(), b -> sendOrder(citizen, null, mode.next()))
				.bounds(listX + 174, rowY, 72, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.architecturoverse.mode_hint")))
				.build());
			rowY += ROW_HEIGHT;
		}

		int bottom = top() + PANEL_HEIGHT - 28;
		Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1)).bounds(listX, bottom, 20, 20).build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1)).bounds(listX + 70, bottom, 20, 20).build());
		prev.active = page > 0;
		next.active = page < pageCount() - 1;
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(left() + PANEL_WIDTH - 88, bottom, 80, 20).build());
	}

	private void addVillageButton(int x, int y, int villageId, Component label) {
		Button button = addRenderableWidget(Button.builder(label, b -> {
			selectedVillage = villageId;
			page = 0;
			rebuildWidgets();
		}).bounds(x, y, VILLAGE_COLUMN, 20).build());
		button.active = selectedVillage != villageId;
	}

	private void changePage(int delta) {
		page = Math.max(0, Math.min(pageCount() - 1, page + delta));
		rebuildWidgets();
	}

	private void sendOrder(CitizenInfo citizen, @Nullable CitizenJob job, @Nullable CitizenMode mode) {
		highlighted = citizen.uuid();
		ClientPlayNetworking.send(new CitizenCommandPayload(citizen.uuid(), job == null ? -1 : job.ordinal(), mode == null ? -1 : mode.ordinal()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int left = left();
		int top = top();
		graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101018);
		graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC9A227);
		graphics.fill(left + VILLAGE_COLUMN + 11, top + 36, left + VILLAGE_COLUMN + 12, top + PANEL_HEIGHT - 8, 0x60FFFFFF);

		graphics.centeredText(font, Component.translatable("screen.architecturoverse.kingdom_of", data.ownerName())
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), left + PANEL_WIDTH / 2, top + 8, 0xFFFFFFFF);
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.summary", data.villages().size(), data.citizens().size()),
			left + PANEL_WIDTH / 2, top + 21, 0xFFAAAAAA);

		int listX = left + VILLAGE_COLUMN + 16;
		int rowY = top + 40;
		List<CitizenInfo> citizens = visibleCitizens();
		if (citizens.isEmpty()) {
			graphics.text(font, Component.translatable("screen.architecturoverse.no_citizens"), listX, rowY + 6, 0xFF888888);
		}
		for (int i = page * ROWS_PER_PAGE; i < Math.min(citizens.size(), (page + 1) * ROWS_PER_PAGE); i++) {
			CitizenInfo citizen = citizens.get(i);
			if (citizen.uuid().equals(highlighted)) {
				graphics.fill(listX - 3, rowY - 1, listX + 250, rowY + 21, 0x40C9A227);
			}
			graphics.text(font, citizen.name(), listX, rowY + 6, 0xFFFFFFFF);
			rowY += ROW_HEIGHT;
		}

		int bottom = top + PANEL_HEIGHT - 28;
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.page", page + 1, pageCount()), listX + 45, bottom + 6, 0xFFFFFFFF);
		selectedVillageInfo().ifPresent(village -> extractVillageStatus(graphics, village, listX, bottom - 30));
		graphics.centeredText(font, Component.translatable("screen.architecturoverse.hint").withStyle(ChatFormatting.ITALIC),
			left + PANEL_WIDTH / 2, top + PANEL_HEIGHT + 6, 0xFFAAAAAA);

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private Optional<VillageInfo> selectedVillageInfo() {
		return data.villages().stream().filter(v -> v.id() == selectedVillage).findFirst();
	}

	/** Warehouse and mine status plus the warehouse's biggest stocks, shown under the citizen list. */
	private void extractVillageStatus(GuiGraphicsExtractor graphics, VillageInfo village, int x, int y) {
		Component warehouse = Component.translatable(village.warehouse()
			? "screen.architecturoverse.warehouse_yes" : "screen.architecturoverse.warehouse_no")
			.withStyle(village.warehouse() ? ChatFormatting.GREEN : ChatFormatting.RED);
		Component mine = village.mineProgress() >= 0
			? Component.translatable("screen.architecturoverse.mine_yes", village.mineProgress()).withStyle(ChatFormatting.GREEN)
			: Component.translatable("screen.architecturoverse.mine_no").withStyle(ChatFormatting.GRAY);
		graphics.text(font, Component.empty().append(warehouse).append("  ").append(mine), x, y - 10, 0xFFFFFFFF);
		int itemX = x;
		for (KingdomSnapshotPayload.StockEntry entry : village.stock()) {
			graphics.item(entry.item(), itemX, y);
			graphics.text(font, compactCount(entry.count()), itemX + 17, y + 9, 0xFFFFFFFF);
			itemX += 40;
		}
	}

	private static String compactCount(int count) {
		return count >= 10000 ? count / 1000 + "k" : String.valueOf(count);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
