import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankMode;
import org.dreambot.api.methods.container.impl.bank.BankQuantitySelection;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.grandexchange.LivePrices;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.login.LoginUtility;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.world.World;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.utilities.Timer;
import org.dreambot.api.utilities.impl.Condition;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

import static org.dreambot.api.methods.Calculations.random;

@ScriptManifest(name = "Mind Bomber", description = "Buys wizard mind bombs in Falador", author = "Developer Name",
        version = 1.0, category = Category.MONEYMAKING, image = "")
public class MindBomber extends AbstractScript {

    Area bank = new Area(2943, 3373, 2949, 3368, 0);
    Area bar = new Area(2952, 3378, 2961, 3366, 0);
    Area barInner = new Area(2956,3374, 2960,3368);
    int bankBoothMinX = 2946;
    int bankBoothMaxX = 2950;
    int purchased = 0;
    int failureCount = 0;
    Walker walker = new Walker();
    State state;
    String status = "None";

    @Override
    public void onPaint(Graphics g) {
        super.onPaint(g);
        g.setColor(Color.BLACK);
        g.fillRect(0, 280, 250, 50);
        g.setColor(Color.WHITE);
        g.drawString("Bombs purchased: " + purchased, 5, 295);
        var price = LivePrices.get("Wizard's mind bomb");
        g.drawString("Estimated profit: " + purchased*(price-3), 5, 310);
        g.drawString(status, 5, 325);
    }

    @Override
    public void onStart() {
        super.onStart();
        if(bar.contains(Players.getLocal())) {
            if (Inventory.count("Coins") >= 3) {
                state = State.BUY_DRINK;
            } else {
                state = State.BANK;
            }
        } else if (bank.contains(Players.getLocal())) {
            if (Inventory.count("Coins") >= 3) {
                state = State.RETURN_TO_BAR;
            } else {
                state = State.BANK;
            }
        } else {
            state = State.BANK;
        }
    }

    @Override
    public int onLoop() {
        if(!Client.isLoggedIn()) {
            log("Not logged in. Will retry in 10s.");
            return 10000;
        }
        purchased = Inventory.count("Wizard's mind bomb") + Bank.count("Wizard's mind bomb");
        if(Inventory.count("Coins") + Bank.count("Coins") < 3) {
            log("you've got no coins. can't buy anything");
            Shutdown();
        }
        if(failureCount > 10) {
            log("Failed to perform the needed action more than 10 times. Logging out for safety");
            Shutdown();
        }
        switch (state) {
            case BUY_DRINK -> {
                BuyDrink();
            }
            case GO_TO_BANK -> {
                UpdateStatus("going to the bank");
                GoToBank();
            }
            case WAITING_TO_BANK -> {
                UpdateStatus("waiting for bank");
                WaitToBank();
            }
            case BANK -> {
                UpdateStatus("banking");
                Bank();
            }
            case RETURN_TO_BAR -> {
                UpdateStatus("heading to the bar");
                ReturnToBar();
            }
        }
        return random(500,2000);
    }

    private void BuyDrink() {
        //If inventory is full or no coins left, go to the bank
        if(Inventory.isFull() || Inventory.count("Coins") < 3) {
            state = State.GO_TO_BANK;
            return;
        }

        //If we aren't in the broader bar area, walk there
        if(!bar.contains(Players.getLocal()) && !bank.contains(Players.getLocal())) {
            UpdateStatus("heading to the bar");
            state = State.RETURN_TO_BAR;
            return;
        }

        UpdateStatus("looking for the bartender");
        var bartender = NPCs.closest(x -> x != null && Objects.equals(x.getName(), "Kaylee"));
        if(bartender == null) {
            failureCount++;
            return;
        }
        //If the bartender is under that dwarf, wait for her to come out since we are forcing a left click. If she doesn't come out, hop worlds.
        //Right clicking the bartender every time we buy a beer would be sus.
        var tries = 0;
        while(bartender.getX() == 2956 && bartender.getY() == 3367) {
            UpdateStatus("waiting for bartender to move off the dwarf");
            if(tries >= 10) {
                HopWorlds();
            }
            sleep(500,1000);
            tries++;
        }

        var bombs = Inventory.count("Wizard's mind bomb");
        UpdateStatus("talking to the bartender");
        if(bartender.interactForceLeft("Talk-to")) {
            UpdateStatus("waiting to buy a mind bomb");
            //Wait until we have the 'ill try the mind bomb option'
            if(!(ContinueDialogueUntil(() -> Dialogues.getOptionIndexContaining("try the") != -1, 5000))) {
                failureCount++;
                log("failed to find the buy option");
                return;
            }
            UpdateStatus("choosing mind bomb");
            Dialogues.chooseFirstOptionContaining("try the");
            //wait until we get the mind bomb
            if(!(ContinueDialogueUntil(() -> Inventory.count("Wizard's mind bomb") > bombs, 5000))) {
                log("failed to buy the drink");
                failureCount++;
                return;
            }
            failureCount = 0;
            purchased++;
        }
    }

    private boolean ContinueDialogueUntil(Condition predicate, long timeout) {
        //If we don't ever get into dialogue, just return
        if(!(Sleep.sleepUntil(() -> !Dialogues.getNPCDialogue().isEmpty(), 5000))) {
            log("did not get into dialogue");
            return false;
        }
        //Otherwise, continue dialogue until the condition is met
        var t = new Timer();
        t.start();
        while(!predicate.verify()) {
            //If the condition isn't met, return unsuccessful
            if(t.elapsed() > timeout) {
                return false;
            }
            Dialogues.continueDialogue();
            sleep(100,500);
        }
        return true;
    }

    private void GoToBank() {
        //If we are in the bar, open the door if needed
        if(bar.contains(Players.getLocal())) OpenBarDoor();

        //Click a random bank from current location
        var tileX = random(bankBoothMinX, bankBoothMaxX);
        var boothQuery = GameObjects.getObjectsOnTile(new Tile(tileX,3367, 0));
        var booth = Arrays.stream(boothQuery).filter(x -> Objects.equals(x.getName(), "Bank booth")).findFirst();
        if(booth.isPresent()) {
            if(booth.get().interact("Bank")) state = State.WAITING_TO_BANK;
        } else {
            //If for some reason we can't see the bank booths, walk to the bank instead
            walker.WalkTo(bank);
        }
    }

    private void WaitToBank() {
        if(!Sleep.sleepUntil(() -> Bank.isOpen(), () -> Players.getLocal().isMoving(), 1000, 100)) {
            state = State.GO_TO_BANK;
            return;
        };
        state = State.BANK;
    }

    private void Bank() {
        if(!Bank.isOpen()) {
            GoToBank();
            return;
        }
        //If we have no coins left, shut down
        if(Inventory.count("Coins") < 3) {
            if(Bank.count("Coins") < 3) {
                log("you've got no coins left. shutting down");
                Shutdown();
            } else {
                if(!Bank.withdrawAll("Coins")) return;
                sleep(100,300);
            }
        }
        if(Bank.depositAll("Wizard's mind bomb"))  {
            walker.WalkToReturnWhenDestinationIsTargeted(barInner);
            state = State.RETURN_TO_BAR;
        }
    }

    private void ReturnToBar() {
        //Open the door if necessary
        OpenBarDoor();
        if(!bar.contains(Players.getLocal()) && !bank.contains(Players.getLocal())) {
            walker.WalkTo(barInner);
        }
        state = State.BUY_DRINK;
    }

    private void OpenBarDoor() {
        UpdateStatus("trying to open the bar door");
        //Get the bar's north door
        var northDoorQuery = GameObjects.getObjectsOnTile(new Tile(2956,3379, 0));
        var doorN = Arrays.stream(northDoorQuery).filter(x -> Objects.equals(x.getName(), "Door")).findFirst();

        //Get the bar's south door
        var southDoorQuery = GameObjects.getObjectsOnTile(new Tile(2962,3372, 0));
        var doorS = Arrays.stream(southDoorQuery).filter(x -> Objects.equals(x.getName(), "Door")).findFirst();

        //If both doors are closed, open the north door
        if (doorN.isPresent() && doorS.isPresent()) {
            UpdateStatus("opening the bar door");
            //walker.WalkToReturnWhenDestinationIsTargeted(outsideBar);
            var door = doorN.get();
            var interact = door.interact("Open");
            while (!interact) interact = door.interact("Open");
        }
    }

    private void UpdateStatus(String newStatus) {
        status = newStatus;
    }

    private void Shutdown() {
        UpdateStatus("Shutting down");
        Tabs.logout();
        getScriptManager().stop();
    }

    private void HopWorlds() {
        UpdateStatus("hopping worlds");
        var current = Worlds.getCurrentWorld();
        WorldHopper.hopWorld(Worlds.getRandomWorld(x ->
                x.isF2P() &&
                x.isNormal() &&
                x.getMinimumLevel() == 0 &&
                x.getWorld() < current + 50 &&
                x.getWorld() > current - 50));
    }
}