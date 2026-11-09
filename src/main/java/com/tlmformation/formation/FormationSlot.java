package com.tlmformation.formation;

public record FormationSlot(int index, int count) {
    public FormationSlot {
        if (index < 0 || count < 1 || index >= count) {
            throw new IllegalArgumentException("Invalid formation slot " + index + "/" + count);
        }
    }
}
