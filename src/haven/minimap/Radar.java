package haven.minimap;

import java.awt.Color;
import java.util.*;

import haven.*;
import haven.Utils;

public class Radar {
    private static Resource borkasound = Resource.load("sfx/hud/reset");
    private final MarkerFactory factory;
    private final Map<Long, Marker> markers = new HashMap<Long, Marker>();
    private final Map<Long, GobRes> undefined = new HashMap<Long, GobRes>();
    private final Object markerLock = new Object();

    public Radar() {
        RadarConfig rc = new RadarConfig();
        factory = new MarkerFactory(rc);
        OptWnd2.setRadarInfo(rc, factory);
    }

    public void add(Gob g, Indir<Resource> res) {
        synchronized (markerLock) {
            if (this.contains(g))
                return;
            boolean added = false;
            String suffix = "";
            try {
                Resource r = res.get();
                if (r != null && r.name != null && r.name.length() != 0) {
                    if (r.name.endsWith("gfx/terobjs/leanto")){
                        ResDrawable d = (ResDrawable)g.getattr(Drawable.class);
                        if (d.sdtnum() == 0)
                            suffix="_vacant";
                    }else if (r.name.endsWith("gfx/terobjs/thornbush")){
                        ResDrawable d = (ResDrawable)g.getattr(Drawable.class);
                        if (d.sdtnum() == 1)
                            suffix="_flowers";
                    }
                    add(r.name+suffix, g);
                    added = true;
                }
            } catch (Session.LoadingIndir ignored) {
            } catch (Resource.Loading ignored) {
            }
            if (!added) {
                // resource isn't loaded yet?
                undefined.put(g.id, new GobRes(g, res));
            }
        }
    }

    public void update() {
        synchronized (markerLock) {
            checkUndefined();
        }
    }

    private void add(String name, Gob gob) {
        Marker m = factory.makeMarker(name, gob);
        if (m != null) {
            KinInfo ki = gob.getattr(KinInfo.class);
            if(ki != null)
            {
                m.override(ki.name, BuddyWnd.gc[ki.group]);
            }
            markers.put(gob.id, m);
            gob.setattr(new GobBlink(gob, m));
        }
    }

    private void checkUndefined() {
        if (undefined.size() == 0)
            return;
        GobRes[] gs = undefined.values().toArray(new GobRes[undefined.size()]);
        for (GobRes gr : gs) {
            try {
                Resource r = gr.res.get();
                if (r != null && r.name != null && r.name.length() != 0) {
                    add(r.name, gr.gob);
                    undefined.remove(gr.gob.id);
                }
            } catch (Session.LoadingIndir ignored) {
            } catch (Resource.Loading ignored) {
            }
        }
    }

    private boolean contains(Gob g) {
        return undefined.containsKey(g.id) || markers.containsKey(g.id);
    }

    public Marker[] getMarkers() {
        synchronized (markerLock) {
            checkUndefined();
            Marker[] collection = markers.values().toArray(new Marker[markers.size()]);
            Arrays.sort(collection);
            return collection;
        }
    }
    
    public Marker getMarker(Long gobid)
    {
        synchronized (markerLock) {
            checkUndefined();
            return markers.get(gobid);
        }
    }

    public Marker get(Gob gob){
	return markers.get(gob.id);
    }

    public void remove(Long gobid) {
        synchronized (markerLock) {
            markers.remove(gobid);
            undefined.remove(gobid);
        }
    }

    public void reload() {
        synchronized (markerLock) {
            undefined.clear();
            RadarConfig rc = new RadarConfig();
            OptWnd2.setRadarInfo(rc, factory);
            factory.setConfig(rc);
            Marker[] ms = markers.values().toArray(new Marker[markers.size()]);
            markers.clear();
            for (Marker m : ms) {
                add(m.name, m.gob);
            }
        }
    }
    
    private static class GobRes {
        public final Gob gob;
        public final Indir<Resource> res;
        
        public GobRes(Gob gob, Indir<Resource> res) {
            this.gob = gob;
            this.res = res;
        }
    }
    
    public static class GobBlink extends GAttrib {
	private final Marker marker;
	Material.Colors fx;
	int time = 0;
	
	public GobBlink(Gob gob, Marker marker) {
	    super(gob);
	    this.marker = marker;
	    Color c = new Color(255, 100, 100, 100);
	    fx = new Material.Colors();
	    fx.amb = Utils.c2fa(c);
	    fx.dif = Utils.c2fa(c);
	    fx.emi = Utils.c2fa(c);
	}

	public void ctick(int dt) {
	    int max = 2000;
	    time = (time + dt)%max;
	    float a = (float)time/max;
	    if(a > 0.6f){
		a = 0;
	    } else if(a > 0.3f){
		a = 2.0f - a/0.3f;
	    } else {
		a = a/0.3f;
	    }
	    fx.amb[3] = a;
	    fx.dif[3] = a;
	    fx.emi[3] = a;
	}

	public GLState getfx() {
	    return(fx);
	}

	public boolean visible() {
	    return marker.template.visible;
	}
    }
}
