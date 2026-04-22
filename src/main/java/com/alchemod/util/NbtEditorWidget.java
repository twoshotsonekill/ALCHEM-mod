package com.alchemod.util;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class NbtEditorWidget {

    private final TextFieldWidget nameField;
    private final TextFieldWidget loreField;
    private final TextFieldWidget enchantmentField;
    private final TextFieldWidget enchantmentLevelField;

    public NbtEditorWidget(TextFieldWidget nameField, TextFieldWidget loreField,
                           TextFieldWidget enchantmentField, TextFieldWidget enchantmentLevelField) {
        this.nameField = nameField;
        this.loreField = loreField;
        this.enchantmentField = enchantmentField;
        this.enchantmentLevelField = enchantmentLevelField;
    }

    public String getCustomName() {
        return nameField != null ? nameField.getText() : "";
    }

    public String getCustomLore() {
        return loreField != null ? loreField.getText() : "";
    }

    public String getEnchantmentId() {
        return enchantmentField != null ? enchantmentField.getText() : "";
    }

    public int getEnchantmentLevel() {
        try {
            return enchantmentLevelField != null ? Integer.parseInt(enchantmentLevelField.getText()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setValues(String name, String lore, String enchantId, int enchantLevel) {
        if (nameField != null) nameField.setText(name);
        if (loreField != null) loreField.setText(lore);
        if (enchantmentField != null) enchantmentField.setText(enchantId);
        if (enchantmentLevelField != null) enchantmentLevelField.setText(String.valueOf(enchantLevel));
    }

    public void clear() {
        if (nameField != null) nameField.setText("");
        if (loreField != null) loreField.setText("");
        if (enchantmentField != null) enchantmentField.setText("");
        if (enchantmentLevelField != null) enchantmentLevelField.setText("");
    }

    public boolean isEmpty() {
        return (nameField == null || nameField.getText().isBlank()) &&
               (loreField == null || loreField.getText().isBlank()) &&
               (enchantmentField == null || enchantmentField.getText().isBlank());
    }
}