package com.layla.ui;

import com.layla.AppContext;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ProfileSelectController implements ViewLifecycle {

    @FXML private Label lblStats1;
    @FXML private Label lblStats2;
    @FXML private Label lblStats3;

    @Override
    public void onEnter() {
        refreshSlots();
    }

    private void refreshSlots() {
        updateSlot(1, lblStats1);
        updateSlot(2, lblStats2);
        updateSlot(3, lblStats3);
    }

    private void updateSlot(int id, Label label) {
        var summary = AppContext.db().getProfileSummary(id);
        if (summary.runs() == 0) {
            label.setText("New Run\n\nNo Data");
        } else {
            label.setText(String.format(
                "Runs: %d\nWins: %d\nStreak: %d\nBest Streak: %d\nDeaths: %d",
                summary.runs(), summary.wins(), summary.streak(), summary.bestStreak(), summary.deaths()
            ));
        }
    }

    @FXML
    private void onSlot1() { selectProfile(1); }
    @FXML
    private void onSlot2() { selectProfile(2); }
    @FXML
    private void onSlot3() { selectProfile(3); }

    private void selectProfile(int id) {
        AppContext.setProfileId(id);
        System.out.println("[Profile] Selected profile " + id);
        SceneRouter.goWithFadeKeepSize("ui/main_menu.fxml");
    }
}
