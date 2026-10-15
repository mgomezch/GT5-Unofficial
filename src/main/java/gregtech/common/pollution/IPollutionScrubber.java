package gregtech.common.pollution;

/**
 * Interface implemented by machines and devices that can accept abstract pollution
 * from an Air Duct network to filter or process it.
 */
public interface IPollutionScrubber {

    /**
     * @return remaining capacity of abstract pollution that this scrubber can receive.
     */
    int getScrubbingCapacity();

    /**
     * Receive up to {@code amount} of abstract pollution into this scrubber's internal buffer.
     *
     * @param amount the requested amount of pollution to accept
     * @return actual amount of pollution accepted
     */
    int receivePollution(int amount);

    /**
     * @return true if this scrubber is valid, active/operational, and able to receive pollution.
     */
    boolean isValidScrubber();

    /**
     * @return the base meta tile entity hosting this scrubber.
     */
    gregtech.api.interfaces.tileentity.IGregTechTileEntity getBaseMetaTileEntity();

    /**
     * @return current amount of buffered/stored pollution in this scrubber.
     */
    default int getStoredPollution() {
        return 0;
    }

    /**
     * Drain and clear all buffered pollution from this scrubber.
     *
     * @return the amount of pollution drained
     */
    default int drainStoredPollution() {
        return 0;
    }
}
