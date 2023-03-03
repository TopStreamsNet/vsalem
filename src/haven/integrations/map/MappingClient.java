package haven.integrations.map;

import haven.*;
import haven.MCache.LoadingMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @author Vendan
 */
public class MappingClient {
    private ExecutorService gridsUploader = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    public String mapString;
    public final String accName;

    private static volatile Map<String, MappingClient> clients = new HashMap<>();

    public static MappingClient getInstance(String username) {
        MappingClient client = clients.get(username);
        if (client == null) {
            client = new MappingClient(username);
            clients.put(username, client);
        }
        return (client);
    }

    public static void removeInstance(String username) {
        MappingClient client = clients.get(username);
        if (client != null) {
            client.scheduler.shutdownNow();
            client.gridsUploader.shutdownNow();
            clients.remove(username);
        }
    }

    private boolean trackingEnabled;

    /***
     * Enable tracking for this execution.  Must be called each time the client is started.
     * @param enabled
     */
    public void EnableTracking(boolean enabled) {
        trackingEnabled = enabled;
    }

    private boolean gridEnabled;

    /***
     * Enable grid data/image upload for this execution.  Must be called each time the client is started.
     * @param enabled
     */
    public void EnableGridUploads(boolean enabled) {
        gridEnabled = enabled;
    }

    private PositionUpdates pu = new PositionUpdates();

    private MappingClient(String accName) {
        this.accName = accName;
        scheduler.scheduleAtFixedRate(pu, 5L, 5L, TimeUnit.SECONDS);
    }

    public String endpoint;

    /***
     * Set mapping server endpoint.  Must be called each time the client is started.  Takes effect immediately.
     * @param endpoint
     */
    public void SetEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    private String playerName;

    /***
     * Set the player name.  Typically called from Charlist.wdgmsg
     * @param name
     */
    public void SetPlayerName(String name) {
        playerName = name;
    }

    /***
     * Checks that the endpoint is functional and matches the version of this mapping client.
     * @return
     */
    public boolean CheckEndpoint() {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(endpoint + "/checkVersion?version=4").openConnection();
            connection.setRequestMethod("GET");
            return connection.getResponseCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    public volatile MapRef lastMapRef;

    private Coord lastGC = null;

    /***
     * Called when entering a new grid
     * @param gc Grid coordinates
     */
    public void EnterGrid(Coord gc) {
        lastGC = gc;
        GetMapRef(true);
        scheduler.execute(new GenerateGridUpdateTask(gc));
    }

    /***
     * Called as you move around, automatically calculates if you have entered a new grid and calls EnterGrid accordingly.
     * @param c Normal coordinates
     */
    public void CheckGridCoord(Coord c) {
        Coord gc = toGC(c);
        if (lastGC == null || !gc.equals(lastGC)) {
            EnterGrid(gc);
        }
    }

    private Map<Long, MapRef> cache = new HashMap<>();

    /***
     * Gets a MapRef (mapid, coordinate pair) for the players current location
     * @return Current grid MapRef
     */
    public MapRef GetMapRef(boolean remote) {
        try {
            Gob player = UI.instance.gui.map.player();
            Coord gc = toGC(player.rc);
            synchronized (cache) {
                long id = UI.instance.sess.glob.map.getgrid(gc).id;
                MapRef mapRef = cache.get(id);
                if (mapRef == null && remote) {
                    scheduler.execute(new Locate(id));
                }
                lastMapRef = mapRef;
                return mapRef;
            }
        } catch (Exception e) {
        }
        return null;
    }

    /***
     * Given a mapref, opens the map to the corresponding location
     * @param mapRef
     */
    public void OpenMap(MapRef mapRef) {
        try {
            mapString = String.format(endpoint + "/#/grid/%d/%d/%d/6", mapRef.mapID, mapRef.gc.x, mapRef.gc.y);
            WebBrowser.self.show(new URL(
                    mapString));
        } catch (Exception ex) {
        }
    }

    private class Locate implements Runnable {
        long gridID;

        Locate(long gridID) {
            this.gridID = gridID;
        }

        @Override
        public void run() {
            try {
                final HttpURLConnection connection =
                        (HttpURLConnection) new URL(endpoint + "/locate?gridID=" + gridID).openConnection();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String resp = reader.lines().collect(Collectors.joining());
                    String[] parts = resp.split(";");
                    if (parts.length == 3) {
                        MapRef mr = new MapRef(Integer.valueOf(parts[0]), new Coord(Integer.valueOf(parts[1]), Integer.valueOf(parts[2])));
                        synchronized (cache) {
                            cache.put(gridID, mr);
                        }
                    }

                } finally {
                    connection.disconnect();
                }

            } catch (final Exception ex) {
            }
        }
    }

    /***
     * Update Marker
     */
    public void setMarker(ArrayList<JSONObject> loadedMarkers){
        try {
            scheduler.execute(new MarkerUpdate(new JSONArray(loadedMarkers.toArray())));
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private class MarkerUpdate implements Runnable {
        JSONArray data;

        MarkerUpdate(JSONArray data) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                System.out.println("Uploading markers");
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(endpoint + "/markerUpdate").openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                connection.setDoOutput(true);
                try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                    final String json = data.toString();
                    System.out.println("Marker json: "+json);
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                System.out.println("Marker upload " + connection.getResponseCode());
            } catch (Exception ex) {
                //System.out.println("MarkerUpdate " + ex);
            }
        }
    }

    private class PositionUpdates implements Runnable {
        private PositionUpdates() {
        }

        @Override
        public void run() {
            if (trackingEnabled && CheckEndpoint()) {
                JSONObject upload = new JSONObject();
                try {
                    Glob g = UI.instance.sess.glob;
                    if (g == null) {
                        return;
                    }
                    for (Gob gob : g.oc) {
                        try {
                            if (gob.name().startsWith("gfx/borka/body") && gob.getattr(GobHealth.class) == null) {
                                JSONObject j = new JSONObject();
                                if (gob.isplayer()) {
                                    j.put("name", playerName);
                                    j.put("type", "player");
                                } else {
                                    KinInfo ki = gob.getattr(KinInfo.class);
                                    if (ki == null) {
                                        j.put("name", "???");
                                        j.put("type", "unknown");
                                    } else {
                                        j.put("name", ki.name);
                                        j.put("type", Integer.toHexString(BuddyWnd.gc[ki.group].getRGB()));
                                    }
                                }
                                MCache.Grid grid = g.map.getgrid(toGC(gob.rc));
                                j.put("gridID", String.valueOf(grid.id));
                                JSONObject c = new JSONObject();
                                Coord goc = gridOffset(gob.rc);
                                c.put("x", (int) (goc.x / 11));
                                c.put("y", (int) (goc.y / 11));
                                j.put("coords", c);
                                upload.put(String.valueOf(gob.id), j);
                            }
                        } catch (Exception ex) {
                            System.out.println("MappintClient run : " + ex);
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("PositionUpdates " + ex);
                    return;
                }

                try {
                    final HttpURLConnection connection =
                            (HttpURLConnection) new URL(endpoint + "/positionUpdate").openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    connection.setDoOutput(true);
                    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                        final String json = upload.toString();
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    connection.getResponseCode();
                } catch (final Exception ex) {
                    System.out.println("PositionUpdates endpoint " + ex);
                }
            }
        }
    }

    private static class GridUpdate {
        String[][] grids;
        Map<String, WeakReference<MCache.Grid>> gridRefs;

        GridUpdate(final String[][] grids, Map<String, WeakReference<MCache.Grid>> gridRefs) {
            this.grids = grids;
            this.gridRefs = gridRefs;
        }

        @Override
        public String toString() {
            return String.format("GridUpdate (%s)", grids[1][1]);
        }
    }

    private class GenerateGridUpdateTask implements Runnable {
        Coord coord;
        int retries = 3;

        GenerateGridUpdateTask(Coord c) {
            this.coord = c;
        }

        @Override
        public void run() {
            if (gridEnabled) {
                final String[][] gridMap = new String[3][3];
                Map<String, WeakReference<MCache.Grid>> gridRefs = new HashMap<>();
                Glob glob = UI.instance.sess.glob;
                LoadingMap error = null;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        try {
                            final MCache.Grid subg = glob.map.getgrid(coord.add(x, y));
                            gridMap[x + 1][y + 1] = String.valueOf(subg.id);
                            gridRefs.put(String.valueOf(subg.id), new WeakReference<>(subg));
                        } catch (LoadingMap l) {
                            error = l;
                        }
                    }
                }
                if (error != null) {
                    glob.map.sendreqs();
                    error.waitfor(() -> {
                        retries--;
                        if (retries >= 0) {
                            scheduler.schedule(this, 250L, TimeUnit.MILLISECONDS);
                        }
                    }, w -> {});
                } else scheduler.execute(new UploadGridUpdateTask(new GridUpdate(gridMap, gridRefs)));
            }
        }
    }

    private class UploadGridUpdateTask implements Runnable {
        private final GridUpdate gridUpdate;

        UploadGridUpdateTask(final GridUpdate gridUpdate) {
            this.gridUpdate = gridUpdate;
        }

        @Override
        public void run() {
            if (gridEnabled) {
                HashMap<String, Object> dataToSend = new HashMap<>();

                dataToSend.put("grids", this.gridUpdate.grids);
                try {
                    HttpURLConnection connection =
                            (HttpURLConnection) new URL(endpoint + "/gridUpdate").openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    connection.setDoOutput(true);
                    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                        String json = new JSONObject(dataToSend).toString();
                        //System.out.println("Sending grid update " + json);
                        out.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    if (connection.getResponseCode() == 200) {
                        DataInputStream dio = new DataInputStream(connection.getInputStream());
                        int nRead;
                        byte[] data = new byte[1024];
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        while ((nRead = dio.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        buffer.flush();
                        JSONObject jo = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
                        synchronized (cache) {
                            try {
                                MapRef mr = new MapRef(jo.getLong("map"), new Coord(jo.getJSONObject("coords").getInt("x"), jo.getJSONObject("coords").getInt("y")));
                                lastMapRef = mr;
                                cache.put(Long.valueOf(gridUpdate.grids[1][1]), mr);
                            } catch (Exception ex) {
                            }
                        }
                        JSONArray reqs = jo.optJSONArray("gridRequests");
                        if (reqs != null) {
                            for (int i = 0; i < reqs.length(); i++) {
                                gridsUploader.execute(new GridUploadTask(reqs.getString(i), gridUpdate.gridRefs.get(reqs.getString(i))));
                            }
                        }
                    }
                } catch (Exception ex) {
                }
            }
        }
    }

    private class GridUploadTask implements Runnable {
        private final String gridID;
        private final WeakReference<MCache.Grid> grid;
        private int retries = 5;

        GridUploadTask(String gridID, WeakReference<MCache.Grid> grid) {
            this.gridID = gridID;
            this.grid = grid;
        }

        @Override
        public void run() {
            try {
                Glob glob = UI.instance.sess.glob;
                MCache.Grid g = grid.get();
                if (g != null && glob != null && glob.map != null) {
                    Loading l = MinimapImageGenerator.checkForLoading(glob.map, g);
                    if (l != null) throw (l);
                    BufferedImage image = MinimapImageGenerator.drawmap(glob.map, g);
                    if (image != null) {
                        try {
                            JSONObject extraData = new JSONObject();
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            ImageIO.write(image, "png", outputStream);
                            ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                            MultipartUtility multipart = new MultipartUtility(endpoint + "/gridUpload", "utf-8");
                            multipart.addFormField("id", this.gridID);
                            multipart.addFilePart("file", inputStream, "minimap.png");

                            extraData.put("season", glob.season);

                            multipart.addFormField("extraData", extraData.toString());

                            MultipartUtility.Response response = multipart.finish();
                            if (response.statusCode != 200) {
                                System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
                            } else {
                                //System.out.println("Uploaded " + gridID);
                            }
                        } catch (IOException e) {
                            System.out.println("Cannot upload " + gridID + ": " + e.getMessage());
                        } catch (JSONException e) {
                            System.out.println("JSON Error!");
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Loading ex) {
                // Retry on Loading
                gridsUploader.submit(this);
            }

        }
    }

    private static Coord toGC(Coord c) {
        return new Coord(Math.floorDiv((int) c.x, 1100), Math.floorDiv((int) c.y, 1100));
    }

    private static Coord toGridUnit(Coord c) {
        return new Coord(Math.floorDiv((int) c.x, 1100) * 1100, Math.floorDiv((int) c.y, 1100) * 1100);
    }

    private static Coord gridOffset(Coord c) {
        Coord gridUnit = toGridUnit(c);
        return new Coord(c.x - gridUnit.x, c.y - gridUnit.y);
    }

    public class MapRef {
        public Coord gc;
        public long mapID;

        private MapRef(long mapID, Coord gc) {
            this.gc = gc;
            this.mapID = mapID;
        }

        public String toString() {
            return (gc.toString() + " in map space " + mapID);
        }
    }
}
