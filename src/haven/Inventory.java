/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import com.google.common.collect.*;

public class Inventory extends Widget implements DTarget {
    private static final Tex obt = Resource.loadtex("gfx/hud/inv/obt");
    private static final Tex obr = Resource.loadtex("gfx/hud/inv/obr");
    private static final Tex obb = Resource.loadtex("gfx/hud/inv/obb");
    private static final Tex obl = Resource.loadtex("gfx/hud/inv/obl");
    private static final Tex ctl = Resource.loadtex("gfx/hud/inv/octl");
    private static final Tex ctr = Resource.loadtex("gfx/hud/inv/octr");
    private static final Tex cbr = Resource.loadtex("gfx/hud/inv/ocbr");
    private static final Tex cbl = Resource.loadtex("gfx/hud/inv/ocbl");
    private static final Tex bsq = Resource.loadtex("gfx/hud/inv/sq");
    public static final Coord sqsz = bsq.sz();
    public static final Coord isqsz = new Coord(40, 40);
    public static final Tex sqlite = Resource.loadtex("gfx/hud/inv/sq1");
    public static final Coord sqlo = new Coord(4, 4);
    public static final Tex refl = Resource.loadtex("gfx/hud/invref");

    private Comparator<WItem> sorter = null;
    
    private static final Comparator<WItem> cmp_asc = new WItemComparator();
    private static final Comparator<WItem> cmp_desc = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
	    return cmp_asc.compare(o2, o1);
	}
    };
    private static final Comparator<WItem> cmp_name = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
            try{
        	int result = o1.item.resname().compareTo(o2.item.resname());
        	if(result == 0)
        	{
        	    result = cmp_desc.compare(o1, o2);
        	}
        	return result;
            }catch(Loading l){return 0;}
        }
    };
    private static final Comparator<WItem> cmp_gobble = new Comparator<WItem>() {
	@Override
	public int compare(WItem o1, WItem o2) {
            try{
        	GobbleInfo g1 = ItemInfo.find(GobbleInfo.class, o1.item.info());
        	GobbleInfo g2 = ItemInfo.find(GobbleInfo.class, o2.item.info());
        	if (g1 == null && g2 == null)
        	    return cmp_name.compare(o1, o2);
        	else if (g1 == null)
        	    return 1;
        	else if (g2 == null)
        	    return -1;
        	int v1 = g1.mainTemper();
        	int v2 = g2.mainTemper();
        	if (v1 == v2)
        	    return cmp_name.compare(o1, o2);
        	return v2-v1;
            }catch(Loading l){
                return 0;
            }
        }
    };

    Coord isz,isz_client;
    Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();
    public int newseq = 0;

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    return(new Inventory(c, (Coord)args[0], parent));
	}
    }

    public void draw(GOut g) {
	invsq(g, Coord.z, isz_client);
	for(Coord cc = new Coord(0, 0); cc.y < isz_client.y; cc.y++) {
	    for(cc.x = 0; cc.x < isz_client.x; cc.x++) {
		invrefl(g, sqoff(cc), isqsz);
	    }
	}
	super.draw(g);
    }

    BiMap<Coord,Coord> dictionaryClientServer;
    boolean isTranslated = false;
    public Inventory(Coord c, Coord sz, Widget parent) {
	super(c, invsz(sz), parent);
	isz = sz;
        isz_client = sz;
        
        Widget window_parent = parent;
        while(!Window.class.isInstance(window_parent) && !RootWidget.class.isInstance(window_parent))
        {
            window_parent = window_parent.parent;
        }
        
        if(sz.equals(new Coord(1,1)) || !(Window.class.isInstance(window_parent)))
        {
            return;
        }
        dictionaryClientServer = HashBiMap.create();
        
        
        IButton sbtn = new IButton(Coord.z, window_parent, Window.obtni[0], Window.obtni[1], Window.obtni[2]){
            {tooltip = Text.render("Sort the items in this inventory by name.");}

            @Override
            public void click() {
                if(this.ui != null)
                {
                    Inventory.this.sorter = cmp_name;
                    Inventory.this.sortItemsLocally(cmp_name);
                }
            }
        };
        sbtn.visible = true;
        ((Window)window_parent).addtwdg(sbtn);
        IButton sgbtn = new IButton(Coord.z, window_parent, Window.gbtni[0], Window.gbtni[1], Window.gbtni[2]){
            {tooltip = Text.render("Sort the items in this inventory by gobble values.");}

            @Override
            public void click() {
                if(this.ui != null)
                {
                    Inventory.this.sorter = cmp_gobble;
                    Inventory.this.sortItemsLocally(cmp_gobble);
                }
            }
        };
        sgbtn.visible = true;
        ((Window)window_parent).addtwdg(sgbtn);
        IButton nsbtn = new IButton(Coord.z, window_parent, Window.lbtni[0], Window.lbtni[1], Window.lbtni[2]){
            {tooltip = Text.render("Undo client-side sorting.");}

            @Override
            public void click() {
                if(this.ui != null)
                {
                    Inventory.this.sorter = null;
                    Inventory.this.removeDictionary();
                }
            }
        };
        nsbtn.visible = true;
        ((Window)window_parent).addtwdg(nsbtn);
    }

    public void sortItemsLocally(Comparator<WItem> comp)
    {
        isTranslated = true;
        //if we can't sort the inventory due to items being updated while we're sorting
        //just abort
        List<WItem> array = new ArrayList<WItem>(wmap.values());
        try{
            Collections.sort(array, comp);
        }
        catch(IllegalArgumentException e){
            return;
        }
        //deciding the size of the sorted inventory
        int width = this.isz.x;
        int height = this.isz.y;
        if(this.equals(this.ui.gui.maininv))
        {
            //flexible size
            int nr_items = wmap.size();
            float aspect_ratio = 8/4;
            height = 4;
            width = 4;
            while(nr_items > height*width)
            {
                if(width==height*2 || width == 32) {
                    height++;
                }
                else {
                    width++;
                }
            }
        }
        //assign the new locations to each of the items and add new translations
        int index = 0;
        BiMap<Coord,Coord> newdictionary = HashBiMap.create();
        
        try{
            for(WItem w : array)
            {
                Coord newclientloc = new Coord((index%(width)),(int)(index/(width)));

                //adding the translation to the dictionary
                Coord serverloc = w.server_c;
                newdictionary.put(newclientloc,serverloc);

                //moving the widget to its ordered place
                w.c = sqoff(newclientloc);

                //on to the next location
                index++;
            }
            dictionaryClientServer = newdictionary;
        }
        catch(IllegalArgumentException iae)
        {
            //duplicate server coordinates, probably because we are swapping
            //no problem, we'll resort upon cdestroy of the old WItem
        }
        
        //resize the inventory to the new set-up
        this.updateClientSideSize();
    }
    
    public Coord translateCoordinatesClientServer(Coord client)
    {
        if(!isTranslated)
            return client;
        Coord server = client;
        if(dictionaryClientServer.containsKey(client))
        {
            server = dictionaryClientServer.get(client);
        }
        else if(dictionaryClientServer.containsValue(client))
        {
            //i.e. we don't have an item there but the server does: find a solution!
            int width = isz.x;
            int height = isz.y;
            int index = 0;
            Coord newloc;
            do{
                newloc = new Coord((index%(width-1)),(int)(index/(width-1)));
                index++;
            }while(dictionaryClientServer.containsValue(newloc));
            server = newloc;
            dictionaryClientServer.put(client,server);
        }
        return server;
    }
    
    Coord getEmptyLocalSpot(BiMap<Coord,Coord> dictionary, int width)
    {
        int index = 0;
        Coord newloc;
        do{
            newloc = new Coord((index%(width-1)),(int)(index/(width-1)));
            index++;
        }while(dictionary.containsKey(newloc));
        return newloc;
    }
    
    public Coord translateCoordinatesServerClient(Coord server)
    {
        if(!isTranslated)
            return server;
        Coord client = server;
        BiMap<Coord,Coord> dictionaryServerClient = dictionaryClientServer.inverse();
        if(dictionaryServerClient.containsKey(server))
        {
            client = dictionaryServerClient.get(server);
        }
        else{
            //find a spot for it
            int width = isz_client.x;
            int height = isz_client.y;
            Coord newloc = getEmptyLocalSpot(dictionaryClientServer, width);
            //if we're going too deep: add another row
            boolean expanded = false;
            if(newloc.y >= height && 2*height>=width)
            {
                newloc = new Coord(0,height);
                expanded = true;
            }
            client = newloc;
            dictionaryClientServer.put(client,server);
            if(expanded)
            {
                updateClientSideSize();
            }
        }
        return client;
    }
    
    public void removeDictionary()
    {
        isTranslated = false;
        dictionaryClientServer = HashBiMap.create();
        for(WItem w : wmap.values())
        {
            w.c = sqoff(w.server_c);
        }
        this.updateClientSideSize();
    }
    
    public Coord updateClientSideSize()
    {
        if(this.equals(ui.gui.maininv))
        {
            int maxx = 2;
            int maxy = 2;
            for(WItem w : wmap.values())
            {
                Coord wc = sqroff(w.c);
                maxx = Math.max(wc.x,maxx);
                maxy = Math.max(wc.y,maxy);
            }
            this.isz_client = new Coord(Math.min(maxx,30)+2,Math.min(maxy,30)+2);
            this.resize(invsz(isz_client));
            return isz_client;
        }
        else
        {
            return isz_client = isz;
        }
    }
    
    public static Coord sqoff(Coord c) {
	return(c.mul(sqsz).add(ctl.sz()));
    }

    public static Coord sqroff(Coord c) {
	return(c.sub(ctl.sz()).div(sqsz));
    }

    public static Coord invsz(Coord sz) {
	return(sz.mul(sqsz).add(ctl.sz()).add(cbr.sz()).sub(4, 4));
    }

    public static void invrefl(GOut g, Coord c, Coord sz) {
	Coord ul = g.ul.sub(g.ul.div(2)).mod(refl.sz()).inv();
	Coord rc = new Coord();
	for(rc.y = ul.y; rc.y < c.y + sz.y; rc.y += refl.sz().y) {
	    for(rc.x = ul.x; rc.x < c.x + sz.x; rc.x += refl.sz().x) {
		g.image(refl, rc, c, sz);
	    }
	}
    }

    public static void invsq(GOut g, Coord c, Coord sz) {
	for(Coord cc = new Coord(0, 0); cc.y < sz.y; cc.y++) {
	    for(cc.x = 0; cc.x < sz.x; cc.x++) {
		g.image(bsq, c.add(cc.mul(sqsz)).add(ctl.sz()));
	    }
	}
	for(int x = 0; x < sz.x; x++) {
	    g.image(obt, c.add(ctl.sz().x + sqsz.x * x, 0));
	    g.image(obb, c.add(ctl.sz().x + sqsz.x * x, obt.sz().y + (sqsz.y * sz.y) - 4));
	}
	for(int y = 0; y < sz.y; y++) {
	    g.image(obl, c.add(0, ctl.sz().y + sqsz.y * y));
	    g.image(obr, c.add(obl.sz().x + (sqsz.x * sz.x) - 4, ctl.sz().y + sqsz.y * y));
	}
	g.image(ctl, c);
	g.image(ctr, c.add(ctl.sz().x + (sqsz.x * sz.x) - 4, 0));
	g.image(cbl, c.add(0, ctl.sz().y + (sqsz.y * sz.y) - 4));
	g.image(cbr, c.add(cbl.sz().x + (sqsz.x * sz.x) - 4, ctr.sz().y + (sqsz.y * sz.y) - 4));
    }

    public static void invsq(GOut g, Coord c) {
	g.image(sqlite, c);
    }

    public boolean mousewheel(Coord c, int amount) {
        if(ui.modshift) {
            wdgmsg("xfer", amount);
        }
        return(true);
    }
    
    public void resort() {
        if(sorter == null) return;
        if(Config.alwayssort)
        {
            sortItemsLocally(sorter);
        }
        else
        {
            updateClientSideSize();
        }
    }
    
    public Widget makechild(String type, Object[] pargs, Object[] cargs) {
    	Coord server_c = (Coord)pargs[0];
        Coord c = translateCoordinatesServerClient(server_c);
	Widget ret = gettype(type).create(c, this, cargs);
	if(ret instanceof GItem) {
	    GItem i = (GItem)ret;
	    wmap.put(i, new WItem(sqoff(c), this, i, server_c));
	    newseq++;
            
            if(isTranslated)
            {
                resort();
            }
            
            if(this == ui.gui.maininv)
                OverviewTool.instance(ui).force_update();
	}
	return(ret);
    }

    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
            WItem wi = wmap.remove(i);
            
            Coord wc = sqroff(wi.c.add(isqsz.div(2)));
            
            if(isTranslated)
            {
                dictionaryClientServer.remove(sqroff(wi.c.add(isqsz.div(2))));
                resort();
            }
            if(this == ui.gui.maininv)
                OverviewTool.instance(ui).force_update();
	    ui.destroy(wi);
	}
    }

    public boolean drop(Coord cc, Coord ul) {
        Coord clientcoords = sqroff(ul.add(isqsz.div(2)));
        Coord servercoords = translateCoordinatesClientServer(clientcoords);
	wdgmsg("drop", servercoords);
	return(true);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }

    public void uimsg(String msg, Object... args) {
	if(msg.equals("sz")) {
	    isz = (Coord)args[0];
            if(isTranslated)
            {
                updateClientSideSize();
            }
            else
            {
                isz_client = isz;
                resize(invsz(isz));
            }
	}
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(msg.equals("transfer-same")){
            if(Config.limit_transfer_amount) {
                process(getSame((GItem) args[0],(Boolean)args[1]), "transfer", 72);
            }
            else {
                process(getSame((GItem) args[0],(Boolean)args[1]), "transfer");
            }
	} else if(msg.equals("drop-same")){
	    process(getSame((GItem) args[0], (Boolean) args[1]), "drop");
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    private void process(List<WItem> items, String action) {
	for (WItem item : items){
	    item.item.wdgmsg(action, Coord.z);
	}
    }
    
    private void process(List<WItem> items, String action, int limitation) {
        int count = 0;
	for (WItem item : items){
            if(++count > limitation)
                break;
	    item.item.wdgmsg(action, Coord.z);
	}
    }


    public List<WItem> getSameName(String name, Boolean ascending) {
	List<WItem> items = new ArrayList<WItem>();
	for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if (wdg.visible && wdg instanceof WItem) {
		if (((WItem) wdg).item.resname().contains(name))
		    items.add((WItem) wdg);
	    }
	}
	Collections.sort(items, ascending?cmp_asc:cmp_desc);
	return items;
    }
                  
    private List<WItem> getSame(GItem item, Boolean ascending) {
        String name = item.resname();
	List<WItem> items = new ArrayList<WItem>();
	for (Widget wdg = lchild; wdg != null; wdg = wdg.prev) {
	    if (wdg.visible && wdg instanceof WItem) {
                boolean same;
                if(Config.pickyalt)
                {
                    same = item.isSame(((WItem) wdg).item);
                }
                else
                {
                    String thatname = ((WItem) wdg).item.resname();
                    same = thatname.equals(name);
                }
		if (same)
		    items.add((WItem) wdg);
	    }
	}
	Collections.sort(items, ascending?cmp_asc:cmp_desc);
	return items;
    }
    
}
