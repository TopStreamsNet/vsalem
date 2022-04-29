package haven.integrations.map;

import haven.Coord;
import haven.Gob;
import haven.UI;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author APXEOLOG (Artyom Melnikov), at 27.01.2019
 */
public class Navigation {
    public static final List<String> UNDERGROUND_TILES = Arrays.asList("gfx/tiles/mine");
    public static final List<String> WATER_TILES = Arrays.asList("gfx/tiles/water", "gfx/tiles/deep");
    public static final List<String> SHALLOW_WATER_TILES = Arrays.asList("gfx/tiles/water");

    public enum GridType { UNKNOWN, CAVE, HOUSE, SURFACE, UNKNOWN_WATER, UNKNOWN_PAVING,
        CHARACTER_GENERATION, CHARACTER_SWITCH, DREAM }

    private static class PlayerPartyCoordinates {
        Coord realGridUnitCoordinates;    // Player's real grid TL coordinates in units
        Coord virtualCoordinates = null;  // Player's virtual coordinates

        PlayerPartyCoordinates(Coord realGridUnitCoordinates) {
            this.realGridUnitCoordinates = realGridUnitCoordinates;
        }

        boolean ready() {
            return realGridUnitCoordinates != null && virtualCoordinates != null;
        }
    }

    public static boolean isValidGridType(GridType gridType) {
        return gridType == GridType.SURFACE || gridType == GridType.UNKNOWN_WATER || gridType == GridType.DREAM;
    }

    /**
     * Tracks current session type
     */
    private static boolean isSessionSetup = false;
    private static GridType sessionType = null;

    private static void setSessionType(GridType type) {
        sessionType = type;
        isSessionSetup = true;
        logMessage("Navigation: Set session type to "  + type);
    }

    /*
     * Methods call order:
     * 0. setCharacterName from CharacterList when we select character to login
     * 1. addPartyCoordinates from Party, fill all party coordinates
     * 2. setCharacterId from MapView
     * 3. setPlayerCoordinates (from (2), position also comes from MapView)
     * 4. receiveGridData from MCache (grids are requested by MapView)
     */

    /**
     * Current character name
     */
    private static String characterName = "";

    public static synchronized void setCharacterName(String name) {
        if (lastPlayerCoordinates != null) {
            setSessionType(GridType.CHARACTER_SWITCH);
        }
        characterName = name;
    }

    public static String getCharacterName() {
        return characterName;
    }

    /**
     * Coordinates of the party players on initial login. Contains both real and virtual coordinates
     */
    private static HashMap<Long, PlayerPartyCoordinates> sessionPartyCoordinates = new HashMap<>();

    public static void addPartyCoordinates(long gobId, Coord coordinates) {
        if (!isSessionSetup) {
            synchronized (Navigation.class) {
                PlayerPartyCoordinates partyCoordinates = sessionPartyCoordinates.get(gobId);
                if (partyCoordinates == null) {
                    sessionPartyCoordinates.put(gobId, new PlayerPartyCoordinates(coordinates));
                } else {
                    if (partyCoordinates.virtualCoordinates == null) {
                        partyCoordinates.virtualCoordinates = coordinates;
                    }
                }
            }
        }
    }

    /**
     * Current character id
     * Process sessionPartyCoordinates when we receive this:
     *  - Check for the character selection instance by coordinates
     *  - Calculate absolute coordinates for the player
     */
    private static long characterId = -1;

    public static synchronized void setCharacterId(long id, Coord coordinates) {
        characterId = id;
        setPlayerCoordinates(coordinates);
    }

    public static long getCharacterId() {
        return characterId;
    }

    /**
     * Player's character virtual coordinates
     */
    private static Coord lastPlayerCoordinates = null;


    private static ConcurrentHashMap<Coord, Long> virtualGridsCache = new ConcurrentHashMap<>();

    public static synchronized void setPlayerCoordinates(Coord coordinates) {
        lastPlayerCoordinates = coordinates;
        recalculateAbsoluteCoordinates();
    }

    static synchronized void recalculateAbsoluteCoordinates() {
        Long gridId = virtualGridsCache.get(lastPlayerCoordinates.toGridCoordinate());
        if (gridId != null) {
            Coord gridCoord = RemoteNavigation.getInstance().locateGridCoordinates(gridId);
            if (gridCoord != null) {
                absoluteCoordinates = lastPlayerCoordinates.gridOffset().add(gridCoord.mul(1100));
            } else {
                absoluteCoordinates = null;
            }
        } else absoluteCoordinates = null;
    }

    public static synchronized void receiveGridData(Coord gridCoordinate, long gridId, GridType gridType) {
        if (lastPlayerCoordinates == null)
            return;

        virtualGridsCache.put(gridCoordinate, gridId);

        if (gridCoordinate.equals(lastPlayerCoordinates.toGridCoordinate())) {
            if (detectedAbsoluteCoordinates != null) {
                detectedAbsoluteCoordinates = null;
            }
            if (sessionType == null) {
                setSessionType(gridType);
            }
            if (isValidGridType(gridType)) {
                // Check if this is first login
                if (!sessionPartyCoordinates.isEmpty()) {
                    PlayerPartyCoordinates partyCoordinates = sessionPartyCoordinates.get(characterId);
                    if (partyCoordinates != null) {
                        if (partyCoordinates.ready()) {
                            detectedAbsoluteCoordinates = partyCoordinates.realGridUnitCoordinates.inv();
                            System.out.println("Detected AC: " + detectedAbsoluteCoordinates);
                        }
                    }
                    sessionPartyCoordinates.clear();
                }
                if (isValidGridType(sessionType)) {
                    RemoteNavigation.getInstance().setCharacterGrid(gridId, gridCoordinate, null);
                }
            }
        }
        recalculateAbsoluteCoordinates();
    }

    /**
     * Generate absolute coordinates
     */
    private static volatile Coord absoluteCoordinates = null;

    private static volatile Coord detectedAbsoluteCoordinates = null;

    /**
     * Get absolute grid coordinates
     */

    public static synchronized void mapdataReset() {
        logMessage("Navigation: Big jump detected, session type cleanup");
        sessionType = null;
        absoluteCoordinates = null;
        virtualGridsCache.clear();
        RemoteNavigation.getInstance().removeAllGrids();
    }

    private static void logMessage(String msg) {
        System.out.println(msg);
    }

    /**
     * Call from Session() to reset before login
     */
    public static synchronized void reset() {
        isSessionSetup = false;
        lastPlayerCoordinates = null;
        sessionType = null;
        characterName = null;
        characterId = -1;
        sessionPartyCoordinates.clear();
        virtualGridsCache.clear();
        RemoteNavigation.getInstance().removeAllGrids();
    }

    public static Coord getAbsoluteCoordinates() {
        return absoluteCoordinates;
    }

    public static Coord getDetectedAbsoluteCoordinates() {
        return detectedAbsoluteCoordinates;
    }

    public static Coord getAbsoluteCoordinates(Gob gob){
        Coord offset = UI.instance.gui.map.player().rc.sub(gob.rc);
        return getAbsoluteCoordinates().add(offset);
    }
}