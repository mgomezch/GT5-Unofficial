package gregtech.common.pollution;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import gregtech.api.metatileentity.implementations.MTEAirDuct;

/**
 * Represents an interconnected network of Air Ducts connecting pollution emitters (muffler hatches)
 * to pollution scrubbers (singleblock scrubbers and electric air filter multiblocks).
 */
public class AirDuctNetwork {

    private final Set<MTEAirDuct> ducts = new HashSet<>();
    private final Set<IPollutionScrubber> scrubbers = new HashSet<>();
    private boolean invalid = false;

    public void addDuct(MTEAirDuct duct) {
        if (duct != null) {
            ducts.add(duct);
        }
    }

    public void addScrubber(IPollutionScrubber scrubber) {
        if (scrubber != null) {
            scrubbers.add(scrubber);
        }
    }

    public Set<MTEAirDuct> getDucts() {
        return Collections.unmodifiableSet(ducts);
    }

    public Set<IPollutionScrubber> getScrubbers() {
        return Collections.unmodifiableSet(scrubbers);
    }

    public MTEAirDuct getAnchorDuct() {
        if (ducts.isEmpty()) return null;
        return ducts.iterator()
            .next();
    }

    public boolean isInvalid() {
        if (invalid) {
            return true;
        }
        for (MTEAirDuct duct : ducts) {
            if (!duct.isValidDuct()) {
                invalid = true;
                return true;
            }
        }
        return false;
    }

    public void invalidate() {
        this.invalid = true;
    }

    /**
     * @return the total remaining scrubbing capacity across all connected and valid scrubbers.
     */
    public int getTotalScrubbingCapacity() {
        if (isInvalid()) return 0;
        int total = 0;
        for (IPollutionScrubber scrubber : scrubbers) {
            if (scrubber.isValidScrubber()) {
                total += Math.max(0, scrubber.getScrubbingCapacity());
            }
        }
        return total;
    }

    /**
     * Routes pollution into the connected scrubbers up to their reported capacity.
     *
     * @param pollutionAmount requested pollution to absorb
     * @return amount of pollution actually absorbed by the network
     */
    public int routePollution(int pollutionAmount) {
        if (pollutionAmount <= 0 || isInvalid()) return 0;
        int remaining = pollutionAmount;
        for (IPollutionScrubber scrubber : scrubbers) {
            if (!scrubber.isValidScrubber()) continue;
            int cap = scrubber.getScrubbingCapacity();
            if (cap <= 0) continue;
            int toTake = Math.min(remaining, cap);
            int accepted = scrubber.receivePollution(toTake);
            remaining -= accepted;
            if (remaining <= 0) break;
        }
        return pollutionAmount - remaining;
    }
}
