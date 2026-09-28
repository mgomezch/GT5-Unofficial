package gregtech.common.tileentities.machines.multi.nuclear;

public enum NeutronType {

    FAST,
    THERMAL,
    BOTH;

    public boolean matches(NeutronType other) {
        return this == BOTH || other == BOTH || this == other;
    }
}
