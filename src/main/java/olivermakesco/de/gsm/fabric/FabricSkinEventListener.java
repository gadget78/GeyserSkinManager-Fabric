package com.github.camotoy.geyserskinmanager.common.platform;

import com.github.camotoy.geyserskinmanager.common.*;
import com.github.camotoy.geyserskinmanager.common.skinretriever.BedrockSkinRetriever;
import com.github.camotoy.geyserskinmanager.common.skinretriever.GeyserSkinRetriever;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @param <P> Player class
 * @param <S> Secondary class that is usually used for applying the profile
 */
public abstract class SkinEventListener<P, S> implements PlatformPlayerUuidSupport<P> {
    protected final BedrockSkinRetriever skinRetriever;
    protected final SkinDatabase database;
    protected final SkinUploader skinUploader = new SkinUploader();
    protected final Consumer<String> warningLoggingFunction;

    public SkinEventListener(Path skinDatabaseLocation, Consumer<String> warningLoggingFunction) {
        this.database = new SkinDatabase(skinDatabaseLocation);
        this.skinRetriever = new GeyserSkinRetriever();
        this.warningLoggingFunction = warningLoggingFunction;
    }

    protected void uploadOrRetrieveSkin(P player, RawSkin skin) {
        PlayerEntry playerEntry = database.getPlayerEntry(getUUID(player));

        if (playerEntry == null) {
            // Fresh join
            uploadSkin(skin, player, null,null);
        } else {
            // This player has joined before
            SkinEntry setSkin = null;
            for (SkinEntry skinEntry : playerEntry.getSkinEntries()) {
                if (skinEntry.getBedrockSkin().equals(skin.rawData)) {
                    setSkin = skinEntry;
                    break;
                }
            }
            if (setSkin == null) {
                uploadSkin(skin, player, null, playerEntry);
            } else {
                // We have the skin, we can go straight to applying it to the player
                onSuccess(player, null, setSkin);
            }
        }
    }

    protected void uploadSkin(RawSkin skin, P player, S other, PlayerEntry playerEntry) {
        skinUploader.uploadSkin(skin).whenComplete((uploadResult, throwable) -> {
            UUID uuid = getUUID(player);
            if (!skinUploader.checkResult(warningLoggingFunction, uuid.toString(), uploadResult, throwable)) {
                return;
            }

            PlayerEntry playerEntryToSave;
            playerEntryToSave = Objects.requireNonNullElseGet(playerEntry, () -> new PlayerEntry(uuid));
            SkinEntry skinEntry = new SkinEntry(skin.rawData, uploadResult.getResponse().get("value").getAsString(),
                    uploadResult.getResponse().get("signature").getAsString(), false);
            playerEntryToSave.getSkinEntries().add(skinEntry);

            onSuccess(player, other, skinEntry);

            // Save the information so we don't have to upload skins to Mineskin again
            database.savePlayerInformation(playerEntryToSave);
        });
    }

    public abstract void onSuccess(P player, S other, SkinEntry skinEntry);
}
