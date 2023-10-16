import org.dreambot.api.input.Mouse;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Sleep;

import static org.dreambot.api.methods.Calculations.random;
import static org.dreambot.api.utilities.Logger.log;
import static org.dreambot.api.utilities.Sleep.sleep;

public class Walker {

    public void WalkTo(Area destination) {
        //While the player is not at the destination:
        while(!destination.contains(Players.getLocal())) {
            //If the destination tile is in the area, wait until we get there.
            if(destination.contains(Walking.getDestination())) {
                Sleep.sleepUntil(() -> destination.contains(Players.getLocal()), 5000);
                //If we made it there, we can break out of the while loop
                if(destination.contains(Players.getLocal())) break;
            }
            //If our current destination (not necessarily the final destination) is within some tiles then make the next click towards the final destination.
            else {
                if(Walking.shouldWalk(3)) {
                    Walking.walk(destination);
                }
            }
            sleep(100,200);
        }
    }

    public void WalkToReturnWhenDestinationIsTargeted(Area destination) {
        //While the player is not at the destination:
        while(!destination.contains(Players.getLocal()) && !destination.contains(Walking.getDestination())) {
            if(Walking.shouldWalk(random(0,5))) {
                Walking.walk(destination);
            }
            sleep(100,200);
        }
    }
}
