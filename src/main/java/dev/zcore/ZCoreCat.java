package dev.zcore;

import meteordevelopment.meteorclient.systems.modules.Category;

/**
 * Centralized custom categories for Z-Core modules.
 * Registered in ZCore#onRegisterCategories() before modules are added.
 */
public final class ZCoreCat {
    public static final Category COMBAT = new Category("Z-Combat");
    public static final Category UTILITY = new Category("Z-Utility");

    private ZCoreCat() {}
}
