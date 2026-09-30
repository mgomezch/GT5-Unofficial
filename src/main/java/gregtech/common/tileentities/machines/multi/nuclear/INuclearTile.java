package gregtech.common.tileentities.machines.multi.nuclear;

public interface INuclearTile {

    /**
     * Current temperature of this grid tile in degrees Celsius.
     */
    double getTemperature();

    /**
     * Sets the temperature of this tile.
     */
    void setTemperature(double temperature);

    /**
     * Deposits heat into this tile in EU equivalent (64 EU = 1 degree C).
     */
    void addHeat(double heatEU);

    /**
     * Heat transfer coefficient between 0.0 and 1.0.
     */
    double getHeatTransferCoeff();

    /**
     * Checks if this tile contains nuclear fuel.
     */
    boolean isFuel();

    /**
     * Generates fast neutrons for the current tick, scaled by reactivity efficiency.
     */
    int generateNeutrons(double efficiency);

    /**
     * Probability [0.0, 1.0] of absorbing a neutron of the given type.
     */
    double getAbsorptionProbability(NeutronType type);

    /**
     * Probability [0.0, 1.0] of scattering a neutron of the given type.
     */
    double getScatteringProbability(NeutronType type);

    /**
     * Probability [0.0, 1.0] that a scattered fast neutron slows down to a thermal neutron.
     */
    double getModerationProbability();

    /**
     * Callback when one or more neutrons are absorbed by this tile.
     */
    void onNeutronAbsorbed(NeutronType type, int count);

    /**
     * Callback when one or more neutrons are scattered by this tile.
     */
    void onNeutronScattered(NeutronType type, int count);

    /**
     * Registers neutron flux traversing this tile.
     */
    void addNeutronFlux(NeutronType type, int count);

    /**
     * Called at the end of the simulation cycle to update durability, cooling, and transmutation.
     */
    void nuclearTick(double efficiency);

    /**
     * Fraction of incoming heat transfer dampened/blocked by insulation [0.0, 1.0].
     * 0.0 means uninsulated (normal heat transfer).
     * 1.0 means complete insulation (no incoming heat transfer).
     */
    default double getInsulationDampening() {
        return 0.0;
    }
}
