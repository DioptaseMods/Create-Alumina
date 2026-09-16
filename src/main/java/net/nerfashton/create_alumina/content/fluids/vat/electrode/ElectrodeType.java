package net.nerfashton.create_alumina.content.fluids.vat.electrode;

import net.minecraft.util.StringRepresentable;

public enum ElectrodeType implements StringRepresentable {
    ANODE("anode", 0),
    CATHODE("cathode", 1);

    private final String name;
    private final int terminalIndex;

    ElectrodeType(String name, int terminalIndex) {
        this.name = name;
        this.terminalIndex = terminalIndex;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int getTerminalIndex() {
        return terminalIndex;
    }

    public ElectrodeType opposite() {
        return this == ANODE ? CATHODE : ANODE;
    }
}
