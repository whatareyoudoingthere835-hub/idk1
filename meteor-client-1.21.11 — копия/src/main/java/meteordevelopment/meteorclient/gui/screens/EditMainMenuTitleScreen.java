/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

/**
 * Окно смены названия главного меню (логотипа).
 * Открывается по клику на название в {@link MainMenuScreen}.
 */
public class EditMainMenuTitleScreen extends WindowScreen {
    public EditMainMenuTitleScreen(GuiTheme theme) {
        super(theme, "Edit main menu title");

        WTable table = add(theme.table()).widget();

        table.add(theme.label("Title: "));
        WTextBox title = table.add(theme.textBox(MainMenuScreen.title)).minWidth(300).expandX().widget();
        title.setFocused(true);
        table.row();

        WButton save = table.add(theme.button("Save")).expandX().widget();
        save.action = () -> {
            MainMenuScreen.setTitle(title.get());
            close();
        };

        enterAction = save.action;
    }
}
