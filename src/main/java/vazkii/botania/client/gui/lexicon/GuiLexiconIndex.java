/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 * 
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 * 
 * File Created @ [Jan 14, 2014, 6:46:59 PM (GMT)]
 */
package vazkii.botania.client.gui.lexicon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.lexicon.ILexicon;
import vazkii.botania.api.lexicon.LexiconCategory;
import vazkii.botania.api.lexicon.LexiconEntry;
import vazkii.botania.client.core.handler.ClientTickHandler;
import vazkii.botania.client.gui.lexicon.button.GuiButtonBack;
import vazkii.botania.client.gui.lexicon.button.GuiButtonInvisible;
import vazkii.botania.client.gui.lexicon.button.GuiButtonPage;

public class GuiLexiconIndex extends GuiLexicon implements IParented {

	LexiconCategory category;
	String title;
	int page = 0;

	int tutPage = -1;

	GuiButton leftButton, rightButton, backButton;
	GuiLexicon parent;
	GuiTextField searchField;

	List<LexiconEntry> entriesToDisplay = new ArrayList();

	public GuiLexiconIndex(LexiconCategory category) {
		this.category = category;
		title = StatCollector.translateToLocal(category == null ? "botaniamisc.lexiconIndex" : category.getUnlocalizedName());
		parent = new GuiLexicon();
	}

	@Override
	void drawHeader() {
		// NO-OP
	}

	@Override
	String getTitle() {
		return title;
	}

	@Override
	boolean isIndex() {
		return true;
	}

	@Override
	boolean isCategoryIndex() {
		return false;
	}

	@Override
	public void onInitGui() {
		super.onInitGui();

		if(!GuiLexicon.isValidLexiconGui(this))	{
			currentOpenLexicon = new GuiLexicon();
			mc.displayGuiScreen(currentOpenLexicon);
			ClientTickHandler.notifyPageChange();
			return;
		}

		buttonList.add(backButton = new GuiButtonBack(12, left + guiWidth / 2 - 8, top + guiHeight + 2));
		buttonList.add(leftButton = new GuiButtonPage(13, left, top + guiHeight - 10, false));
		buttonList.add(rightButton = new GuiButtonPage(14, left + guiWidth - 18, top + guiHeight - 10, true));

		searchField = new GuiTextField(fontRendererObj, left + guiWidth / 2 + 28, top + guiHeight + 6, 200, 10);
		searchField.setCanLoseFocus(false);
		searchField.setFocused(true);
		searchField.setEnableBackgroundDrawing(false);

		updateAll();
	}

	void updateAll() {
		buildEntries();
		updatePageButtons();
		populateIndex();
	}

	void buildEntries() {
		entriesToDisplay.clear();
		ILexicon lex = (ILexicon) stackUsed.getItem();
		for(LexiconEntry entry : category == null ? BotaniaAPI.getAllEntries() : category.entries) {
			if(lex.isKnowledgeUnlocked(stackUsed, entry.getKnowledgeType()) && StatCollector.translateToLocal(entry.getUnlocalizedName()).toLowerCase().contains(searchField.getText().toLowerCase().trim()))
				entriesToDisplay.add(entry);
		}
		Collections.sort(entriesToDisplay);
	}

	@Override
	void populateIndex() {
		LexiconEntry tutEntry = tutorial != null && !tutorial.isEmpty() ? tutorial.peek() : null;

		for(int i = page * 12; i < (page + 1) * 12; i++) {
			GuiButtonInvisible button = (GuiButtonInvisible) buttonList.get(i - page * 12);
			LexiconEntry entry = i >= entriesToDisplay.size() ? null : entriesToDisplay.get(i);
			if(entry != null) {
				button.displayString = entry.getKnowledgeType().color + "" + (entry.isPriority() ? EnumChatFormatting.ITALIC : "") + StatCollector.translateToLocal(entry.getUnlocalizedName());
				button.displayStack = entry.getIcon();
				if(entry == tutEntry)
					tutPage = page;
			} else button.displayString = "";
		}
	}

	@Override
	public void drawScreen(int par1, int par2, float par3) {
		super.drawScreen(par1, par2, par3);

		if(!searchField.getText().isEmpty()) {
			drawBookmark(left + 138, top + guiHeight - 24, "  " + searchField.getText(), false);
			mc.renderEngine.bindTexture(texture);
			GL11.glColor4f(1F, 1F, 1F, 1F);
			drawTexturedModalRect(left + 134, top + guiHeight - 26, 86, 180, 12, 12);
		} else {
			boolean unicode = mc.fontRenderer.getUnicodeFlag();
			mc.fontRenderer.setUnicodeFlag(true);
			String s = StatCollector.translateToLocal("botaniamisc.typeToSearch");
			mc.fontRenderer.drawString(s, left + 120 - mc.fontRenderer.getStringWidth(s), top + guiHeight - 18, 0x666666);
			mc.fontRenderer.setUnicodeFlag(unicode);
		}
	}

	@Override
	public void positionTutorialArrow() {
		LexiconEntry entry = tutorial.peek();
		LexiconCategory category = entry.category;
		if(category != this.category) {
			orientTutorialArrowWithButton(backButton);
			return;
		}

		if(tutPage != -1 && tutPage != page) {
			orientTutorialArrowWithButton(tutPage < page ? leftButton : rightButton);
			return;
		}

		List<GuiButton> buttons = buttonList;
		for(GuiButton button : buttons) {
			int id = button.id;
			int index = id + page * 12;
			if(index >= entriesToDisplay.size())
				continue;

			if(entry == entriesToDisplay.get(index)) {
				orientTutorialArrowWithButton(id >= 12 ? rightButton : button);
				break;
			}
		}
	}

	@Override
	protected void actionPerformed(GuiButton par1GuiButton) {
		if(par1GuiButton.id >= BOOKMARK_START)
			handleBookmark(par1GuiButton);
		else
			switch(par1GuiButton.id) {
			case 12 :
				mc.displayGuiScreen(parent);
				ClientTickHandler.notifyPageChange();
				break;
			case 13 :
				page--;
				updatePageButtons();
				populateIndex();
				ClientTickHandler.notifyPageChange();
				break;
			case 14 :
				page++;
				updatePageButtons();
				populateIndex();
				ClientTickHandler.notifyPageChange();
				break;
			default :
				int index = par1GuiButton.id + page * 12;
				openEntry(index);
			}
	}

	void openEntry(int index) {
		if(index >= entriesToDisplay.size())
			return;

		LexiconEntry entry = entriesToDisplay.get(index);
		mc.displayGuiScreen(new GuiLexiconEntry(entry, this));
		ClientTickHandler.notifyPageChange();
	}

	public void updatePageButtons() {
		leftButton.enabled = page != 0;
		rightButton.enabled = page < (entriesToDisplay.size() - 1) / 12;
		putTutorialArrow();
	}

	@Override
	public void setParent(GuiLexicon gui) {
		parent = gui;
	}

	int fx = 0;
	boolean swiped = false;

	@Override
	protected void mouseClickMove(int x, int y, int button, long time) {
		if(button == 0 && Math.abs(x - fx) > 100 && mc.gameSettings.touchscreen && !swiped) {
			double swipe = (x - fx) / Math.max(1, (double) time);
			if(swipe < 0.5) {
				nextPage();
				swiped = true;
			} else if(swipe > 0.5) {
				prevPage();
				swiped = true;
			}
		}
	}

	@Override
	protected void mouseClicked(int par1, int par2, int par3) {
		super.mouseClicked(par1, par2, par3);

		searchField.mouseClicked(par1, par2, par3);
		fx = par1;
		if(par3 == 1)
			back();
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();

		if(Mouse.getEventButton() == 0)
			swiped = false;

		int w = Mouse.getEventDWheel();
		if(w < 0)
			nextPage();
		else if(w > 0)
			prevPage();
	}

	@Override
	boolean closeScreenOnInvKey() {
		return false;
	}

	@Override
	protected void keyTyped(char par1, int par2) {
		if(par2 == 203 || par2 == 200 || par2 == 201) // Left, Up, Page Up
			prevPage();
		else if(par2 == 205 || par2 == 208 || par2 == 209) // Right, Down Page Down
			nextPage();
		else if(par2 == 14 && searchField.getText().isEmpty()) // Backspace
			back();
		else if(par2 == 199) { // Home
			mc.displayGuiScreen(new GuiLexicon());
			ClientTickHandler.notifyPageChange();
		} else if(par2 == 28 && entriesToDisplay.size() == 1) // Enter
			openEntry(0);

		String search = searchField.getText();
		searchField.textboxKeyTyped(par1, par2);
		if(!searchField.getText().equalsIgnoreCase(search))
			updateAll();

		super.keyTyped(par1, par2);
	}

	void back() {
		if(backButton.enabled) {
			actionPerformed(backButton);
			backButton.func_146113_a(mc.getSoundHandler());
		}
	}

	void nextPage() {
		if(rightButton.enabled) {
			actionPerformed(rightButton);
			rightButton.func_146113_a(mc.getSoundHandler());
		}
	}

	void prevPage() {
		if(leftButton.enabled) {
			actionPerformed(leftButton);
			leftButton.func_146113_a(mc.getSoundHandler());
		}
	}
}
