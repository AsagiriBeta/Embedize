package com.embedize.compat.papi;

import com.embedize.EmbedizePlugin;
import com.embedize.reset.ResourceWorldPlaceholderValues;
import com.embedize.reset.ResourceWorldResetService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Internal PlaceholderAPI expansion equivalent to
 * {@code resource_world_reset_placeholders.sk}.
 *
 * <p>Placeholders (identifier {@code resource}):
 * <ul>
 *   <li>{@code %resource_reset_next%} — e.g. {@code 2026-09-01 04:00}</li>
 *   <li>{@code %resource_reset_countdown%} — e.g. {@code 12天3小时4分钟}</li>
 *   <li>{@code %resource_reset_days%} / {@code %resource_reset_hours%} / {@code %resource_reset_mins%}</li>
 * </ul>
 */
public final class ResourceWorldExpansion extends PlaceholderExpansion {

    private final EmbedizePlugin plugin;

    public ResourceWorldExpansion(EmbedizePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "resource";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Required for internal expansions so /papi reload does not drop us.
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        ResourceWorldResetService service = plugin.getResourceWorldResetService();
        if (service == null) {
            return null;
        }
        ResourceWorldPlaceholderValues.Snapshot snap =
                ResourceWorldPlaceholderValues.compute(service.settings());
        return ResourceWorldPlaceholderValues.resolve(snap, params);
    }
}
