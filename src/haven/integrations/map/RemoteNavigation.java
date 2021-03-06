package haven.integrations.map;

import haven.*;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author APXEOLOG (Artyom Melnikov), at 31.01.2019
 */
public class RemoteNavigation {
    private static final String MAP_ENDPOINT = "http://hnh.0xebfe.me";
    private static final String INDEX_FILE_URL = MAP_ENDPOINT + "/grids/mapdata_index";
    private static final String API_ENDPOINT = MAP_ENDPOINT + "/api";

    private final File localMapdataIndexFile = new File(System.getProperty("user.dir"), "mapdata_index_local");

    private final ConcurrentLinkedQueue<GridData> receivedGrids = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, Pair<Coord, Coord>> sessionAbsoluteRealPair = new ConcurrentHashMap<>();

    private final ExecutorService gridsUploader = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    private final ConcurrentHashMap<Long, Coord> globalMapdataIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Coord> localMapdataIndex = new ConcurrentHashMap<>();

    private static volatile RemoteNavigation INSTANCE = null;

    public static RemoteNavigation getInstance() {
        if (INSTANCE == null) {
            synchronized (RemoteNavigation.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RemoteNavigation();
                }
            }
        }
        return INSTANCE;
    }

    private RemoteNavigation() {
        // Load local caches
        File globalMapdataIndexFile = new File(System.getProperty("user.dir"), "mapdata_index_global");
        if (globalMapdataIndexFile.exists()) {
            scheduler.execute(new LoadIndexFileTask(globalMapdataIndexFile, globalMapdataIndex));
        }
        if (localMapdataIndexFile.exists()) {
            scheduler.execute(new LoadIndexFileTask(localMapdataIndexFile, localMapdataIndex));
        }
        // Request new mapdata cache and schedule update every 30 minutes
        scheduler.scheduleAtFixedRate(new LoadRemoteIndexTask(globalMapdataIndexFile, globalMapdataIndex),
                0L, 30, TimeUnit.MINUTES);
        // Update character's position on the map
        scheduler.scheduleAtFixedRate(new UpdateCharacterPosition(), 2L, 2L, TimeUnit.SECONDS);
    }

    public void uploadMarkerData(MarkerData markerData) {
            //scheduler.schedule(new UploadMarkerTask(markerData), 5, TimeUnit.SECONDS);
            scheduler.execute(new UploadMarkerTask(markerData));
    }

    /**
     * Find absolute coordinates for the specific gridId
     * @return null is there is no such data
     */
    public CompletableFuture<Coord> locateGridCoordinates(long gridId, boolean includeRemote) {
        Coord coordinates = globalMapdataIndex.get(gridId);
        if (coordinates != null) {
            //System.out.println(gridId + " found in global cache: " + coordinates);
            return CompletableFuture.completedFuture(coordinates);
        }
        coordinates = localMapdataIndex.get(gridId);
        if (coordinates != null) {
            //System.out.println(gridId + " found in local cache: " + coordinates);
            return CompletableFuture.completedFuture(coordinates);
        }
        if (includeRemote) {
            return CompletableFuture.supplyAsync(() -> {
                String response = getUrlResponse(API_ENDPOINT + "/v1/locate?gridId=" + gridId);
                if (response != null) {
                    String[] parts = response.split(";");
                    if (parts.length == 2) {
                        return new Coord(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]));
                    }
                }
                return null;
            });
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    Coord locateGridCoordinates(long gridId) {
        try {
            return locateGridCoordinates(gridId, false).get();
        } catch (Exception ex) {
            return null;
        }
    }

    void setCharacterGrid(long gridId, Coord virtualCoordinates, Coord detectedAbsoluteCoordinates) {
        final int sessionId = sessionCounter.get();
        System.out.println("Setting character grid " + gridId + " " + virtualCoordinates + " " + detectedAbsoluteCoordinates);
        if (sessionAbsoluteRealPair.containsKey(sessionId)) {
            // Do nothing, we already have this session processed
            System.out.println("This session is already present");
            return;
        } else {
            if (detectedAbsoluteCoordinates != null) {
                sessionAbsoluteRealPair.put(sessionId, new Pair<>(null, null));
            }
        }
        locateGridCoordinates(gridId, true).thenApply(cachedCoordinates -> {
            if (cachedCoordinates != null) return cachedCoordinates;
            else {
                if (detectedAbsoluteCoordinates != null) {
                    System.out.println("Using detected absolute coordinates for grid " + gridId + ": " + detectedAbsoluteCoordinates);
                }
                return detectedAbsoluteCoordinates;
            }
        }).thenAccept(absoluteCoordinates -> {
            if (absoluteCoordinates == null) {
                System.out.println("No absolute coordinates for grid " + gridId);
                return;
            }
            localMapdataIndex.put(gridId, absoluteCoordinates);
            sessionAbsoluteRealPair.put(sessionId, new Pair<>(absoluteCoordinates, virtualCoordinates));

            Iterator<GridData> iterator = receivedGrids.iterator();
            while (iterator.hasNext()) {
                GridData gridData = iterator.next();
                if (gridData.sessionId < sessionId) {
                    iterator.remove();
                    System.out.println("Removed obsolete grid for session" + gridData.sessionId + " - " + gridData);
                } else if (gridData.sessionId == sessionId) {
                    gridData.calculateAbsoluteCoordinates(absoluteCoordinates, virtualCoordinates);
                    localMapdataIndex.put(gridData.gridId, gridData.absoluteGridCoordinates);
                    System.out.println("Calculated grid: " + gridData.toString());
                    iterator.remove();
                    if (Config.enableNavigationTracking) {
                        gridsUploader.submit(new UploadGridDataTask(gridData));
                    }
                }
            }
            Navigation.recalculateAbsoluteCoordinates();
            scheduler.execute(new SaveIndexFileTask(localMapdataIndexFile, localMapdataIndex));
        });
    }

    public void openBrowserMap(Coord gridCoord) {
        try {
            WebBrowser.self.show(new URL(
                    String.format(MAP_ENDPOINT + "/#/grid/%d/%d/6", gridCoord.x, gridCoord.y)));
        } catch (Exception ex) {}
    }

    /**
     * Receive new grid
     */
    public void receiveGrid(MCache.Grid grid) {
        int currentSession = sessionCounter.get();
        GridData gridData = new GridData(grid, currentSession);
        Pair<Coord, Coord> sessionOffsets = sessionAbsoluteRealPair.get(currentSession);
        if (sessionOffsets != null && sessionOffsets.a != null && sessionOffsets.b != null) {
            gridData.calculateAbsoluteCoordinates(sessionOffsets.a, sessionOffsets.b);
            Coord indexAbsoluteCoord = globalMapdataIndex.get(gridData.gridId) != null ?
                    globalMapdataIndex.get(gridData.gridId) : localMapdataIndex.get(gridData.gridId);
            if (indexAbsoluteCoord != null && !gridData.absoluteGridCoordinates.equals(indexAbsoluteCoord)){
                System.out.println("Detected AC "+gridData.absoluteGridCoordinates+" index AC "+
                        indexAbsoluteCoord+" - overriding!");
                Navigation.mapdataReset();
                gridData.absoluteGridCoordinates = indexAbsoluteCoord;
                receivedGrids.add(gridData);
                return;
            }
            localMapdataIndex.put(gridData.gridId, gridData.absoluteGridCoordinates);
            if (Config.enableNavigationTracking) {
                gridsUploader.submit(new UploadGridDataTask(gridData));
            }
            System.out.println("Detected grid on receive: " + gridData.toString());
        } else {
            receivedGrids.add(gridData);
        }
    }

    /**
     * Trim all grids
     */
    void removeAllGrids() {
        System.out.println("Remove all grids");
        receivedGrids.clear();
        sessionAbsoluteRealPair.clear();
        sessionCounter.incrementAndGet();
    }

    private static class GridData {
        WeakReference<MCache.Grid> gridReference;
        int sessionId;
        long gridId;
        Coord virtualGridCoordinates;
        Coord absoluteGridCoordinates = null;

        GridData(MCache.Grid grid, int sessionId) {
            this.gridReference = new WeakReference<>(grid);
            this.virtualGridCoordinates = grid.gc;
            this.sessionId = sessionId;
            this.gridId = grid.id;
        }

        void calculateAbsoluteCoordinates(Coord sessionACGC, Coord sessionVCGC) {
            this.absoluteGridCoordinates = sessionACGC.add(this.virtualGridCoordinates.sub(sessionVCGC));
        }

        @Override
        public String toString() {
            return String.format("Grid (%d) V:%s, A:%s", gridId,
                    String.valueOf(virtualGridCoordinates), String.valueOf(absoluteGridCoordinates));
        }
    }

    private static void importGridDataToMap(String gridData, Map<Long, Coord> target) {
        String[] parts = gridData.split(",");
        if (parts.length == 3) {
            target.put(Long.valueOf(parts[0]), new Coord(Integer.valueOf(parts[1]), Integer.valueOf(parts[2])));
        }
    }

    private static class LoadIndexFileTask implements Runnable {
        private File source;
        private Map<Long, Coord> target;

        LoadIndexFileTask(File source, Map<Long, Coord> target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public void run() {
            try {
                Files.readAllLines(source.toPath(), StandardCharsets.UTF_8)
                        .forEach(line -> importGridDataToMap(line, target));
                System.out.println("Loaded index file: " + source);
            } catch (Exception ex) {
                System.err.println("Cannot load index file " + source + ": " + ex.getMessage());
            }
        }
    }

    private static class LoadRemoteIndexTask implements Runnable {
        private File cacheFile;
        private Map<Long, Coord> target;

        LoadRemoteIndexTask(File cacheFile, Map<Long, Coord> target) {
            this.cacheFile = cacheFile;
            this.target = target;
        }

        @Override
        public void run() {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(INDEX_FILE_URL).openConnection();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    target.clear();
                    reader.lines().forEach(line -> importGridDataToMap(line, target));
                    System.out.println("Loaded remote index");
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ex) {
                System.err.println("Cannot load remote index file: " + ex.getMessage());
            }
            try {
                // Update cache
                List<String> gridDataList = target.entrySet().stream().map(entry -> String.format("%d,%d,%d",
                        entry.getKey(), entry.getValue().x, entry.getValue().y)).collect(Collectors.toList());
                Files.write(cacheFile.toPath(), gridDataList, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ex) {
                System.err.println("Cannot save remote index file: " + ex.getMessage());
            }
        }
    }

    private static class SaveIndexFileTask implements Runnable {
        private File cacheFile;
        private Map<Long, Coord> source;

        SaveIndexFileTask(File cacheFile, Map<Long, Coord> source) {
            this.cacheFile = cacheFile;
            this.source = source;
        }

        @Override
        public void run() {
            try {
                // Update cache
                List<String> gridDataList = source.entrySet().stream().map(entry -> String.format("%d,%d,%d",
                        entry.getKey(), entry.getValue().x, entry.getValue().y)).collect(Collectors.toList());
                Files.write(cacheFile.toPath(), gridDataList, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ex) {
                System.err.println("Cannot save index file: " + ex.getMessage());
            }
        }
    }

    private class UploadGridDataTask implements Runnable {
        private GridData gridData;

        UploadGridDataTask(GridData gridData) {
            this.gridData = gridData;
        }

        @Override
        public void run() {
            try {
                MCache.Grid grid = this.gridData.gridReference.get();
                Glob glob = Glob.getByReference();
                if (grid != null && glob != null && glob.map != null) {
                    BufferedImage image = MinimapImageGenerator.drawmap(glob.map, grid);
                    if (image == null) {
                        throw new Loading();
                    }
                    try {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", outputStream);
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                        MultipartUtility multipart = new MultipartUtility(API_ENDPOINT + "/v2/updateGrid", "utf-8");
                        multipart.addFormField("id", String.valueOf(this.gridData.gridId));
                        multipart.addFormField("x", String.valueOf(this.gridData.absoluteGridCoordinates.x));
                        multipart.addFormField("y", String.valueOf(this.gridData.absoluteGridCoordinates.y));
                        multipart.addFilePart("file", inputStream, "minimap.png");
                        MultipartUtility.Response response = multipart.finish();
                        if (response.statusCode != 200) {
                            System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
                        } else {
                            System.out.println("Uploaded " + gridData);
                        }
                    } catch (IOException e) {
                        System.out.println("Cannot upload " + gridData + ": " + e.getMessage());
                        UI.instance.gui.message("Cannot upload"+gridData+" : "+e.getMessage(), Color.RED);
                    }
                }
            } catch (Loading ex) {
                // Retry on Loading
                gridsUploader.submit(this);
            }
        }
    }

    private static class UpdateCharacterPosition implements Runnable {
        @Override
        public void run() {
            if (Config.enableNavigationTracking && Navigation.getCharacterId() > -1) {
                Coord coordinates = Navigation.getAbsoluteCoordinates();
                Coord detectedAC = Navigation.getDetectedAbsoluteCoordinates();
                HashMap<String, Object> dataToSend = new HashMap<>();
                if (coordinates != null) {
                    dataToSend.put("x", (int) (coordinates.x / 11));
                    dataToSend.put("y", (int) (coordinates.y / 11));
                    dataToSend.put("type", "located");
                } else if (detectedAC != null) {
                    dataToSend.put("x", (int) (detectedAC.x / 11));
                    dataToSend.put("y", (int) (detectedAC.y / 11));
                    dataToSend.put("type", "detected");
                }
                if (dataToSend.size() > 0) {
                    dataToSend.put("name", StringEscapeUtils.escapeJson(Navigation.getCharacterName()));
                    dataToSend.put("id", Navigation.getCharacterId());
                    try {
                        HttpURLConnection connection =
                                (HttpURLConnection) new URL(API_ENDPOINT + "/v2/updateCharacter").openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        connection.setDoOutput(true);
                        try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                            out.writeBytes(new JSONObject(dataToSend).toString());
                        }
                        connection.getResponseCode();
                    } catch (Exception ignored) { }
                }
            }
        }
    }

    public static class MarkerData {
        String name;
        Coord gridOffset;
        String image = null;
        long gridId;

        public void setName(String name) {
            this.name = name;
        }

        public void setGridOffset(Coord gridOffset) {
            this.gridOffset = gridOffset;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public void setGridId(long gridId) {
            this.gridId = gridId;
        }

        @Override
        public String toString() {
            return String.format("%s - %s (%d) [%d:%d]", name, image, gridId, gridOffset.x, gridOffset.y);
        }

        public HashMap<String, Object> dataToSend() {
            HashMap<String, Object> tmp = new HashMap<>();
            tmp.put("name", name);
            tmp.put("gridId", gridId);
            tmp.put("x", gridOffset.x);
            tmp.put("y", gridOffset.y);
            tmp.put("image", image);
            return tmp;
        }
    }


    private static class UploadMarkerTask implements Runnable {
        private MarkerData markerData;

        public UploadMarkerTask(MarkerData markerData) {
            this.markerData = markerData;
            System.out.println("Upload!");
        }

        @Override
        public void run() {
                ArrayList<MarkerData> loadedMarkers = new ArrayList<>();
                loadedMarkers.add(this.markerData);
                System.out.println("Loaded " + loadedMarkers.size() + " markers");
                if (!loadedMarkers.isEmpty()) {
                    try {
                        HttpURLConnection connection = (HttpURLConnection)
                                new URL(API_ENDPOINT + "/v1/uploadMarkers").openConnection();
                        connection.setRequestMethod("POST");
                        connection.setRequestProperty("Content-Type", "application/json");
                        connection.setDoOutput(true);
                        try (OutputStream outputStream = connection.getOutputStream()) {
                            Collection collection = loadedMarkers.stream()
                                    .map(MarkerData::dataToSend).collect(Collectors.toCollection(ArrayList::new));
                            String json = new JSONArray(collection).toString();
                            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                        }
                        connection.getResponseCode();
                        System.out.println("Sent markers data");
                    } catch (Exception ex) {
                        System.out.println("Cannot upload markers: " + ex.getMessage());
                    }
                }

        }
    }

    private static String getUrlResponse(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                return reader.lines().collect(Collectors.joining());
            } finally {
                connection.disconnect();
            }
        } catch (Exception ex) {
            return null;
        }
    }
}