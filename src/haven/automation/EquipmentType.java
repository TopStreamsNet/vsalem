package haven.automation;

import haven.Coord;
import haven.Equipory;

import java.util.HashMap;
import java.util.Map;


public enum EquipmentType {
    Head(Equipory.boxen[0].c),
    Face(Equipory.boxen[1].c),
    Shirt(Equipory.boxen[2].c),
    Torso(Equipory.boxen[3].c),
    Keys(Equipory.boxen[4].c),
    Belt(Equipory.boxen[5].c),
    Lhande(Equipory.boxen[6].c),
    Rhande(Equipory.boxen[7].c),
    Wallet(Equipory.boxen[9].c),
    Coat(Equipory.boxen[10].c),
    Cape(Equipory.boxen[11].c),
    Pants(Equipory.boxen[12].c),
    Neck(Equipory.boxen[13].c),
    Back(Equipory.boxen[14].c),
    Feet(Equipory.boxen[15].c),

    Unknown(new Coord(-5, -5));

    public static final Map<Coord, EquipmentType> eqmap = new HashMap<>();
    public final Coord position;

    EquipmentType(final Coord pos) {
        this.position = pos.add(4, 4);
    }

    static {
        for (final EquipmentType type : values()) {
            eqmap.put(type.position, type);
        }
    }
}