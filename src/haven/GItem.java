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

import haven.ItemInfo.Name;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GItem extends AWidget implements ItemInfo.ResOwner, Comparable<GItem> {
    public static volatile long infoUpdated;
    static ItemFilter filter = null;
    private static long lastFilter = 0;
    public Indir<Resource> res;
    public int meter = 0;
    public int num = -1;
    private Object[] rawinfo;
    private List<ItemInfo> info = Collections.emptyList();
    public boolean marked = false;



    @Override
    public int compareTo(GItem that) {
        Alchemy thisalch = ItemInfo.find(Alchemy.class, this.info());
        Alchemy thatalch = ItemInfo.find(Alchemy.class, that.info());
        if(thisalch == thatalch)
        {
            return this.rawinfo.hashCode()-that.rawinfo.hashCode();
        }
        if(thisalch==null)
            return -1;
        if(thatalch==null)
            return 1;

        if(thisalch.a[0] == thatalch.a[0])
            return 0;
        else
            return (thisalch.a[0]-thatalch.a[0]<0)?-1:1;
    }

    public boolean sendttupdate = false;
    public boolean matched = false;
    private long filtered = 0;
    public boolean drop = false;
    private double dropTimer = 0;

    public static void setFilter(ItemFilter filter) {
        GItem.filter = filter;
        lastFilter = System.currentTimeMillis();
    }


    @RName("item")
    public static class $_ implements Factory {
        public Widget create(Coord c, Widget parent, Object[] args) {
            int res = (Integer)args[0];
            return(new GItem(c, parent, parent.ui.sess.getres(res)));
        }
    }

    public interface ColorInfo {
        public Color olcol();
    }

    public interface NumberInfo {
        public int itemnum();
    }

    public class Amount extends ItemInfo implements NumberInfo {
        private final int num;

        public Amount(int num) {
            super(GItem.this);
            this.num = num;
        }

        public int itemnum() {
            return(num);
        }
    }

    public GItem(Widget parent, Indir<Resource> res) {
        this(Coord.z, parent, res);
    }

    public GItem(Coord c, Widget parent, Indir<Resource> res) {
        super(parent);
        this.c = c;
        this.res = res;
    }

    public Glob glob() {
        return(ui.sess.glob);
    }

    public List<ItemInfo> info() {
        if(info == null)
        {
            info = ItemInfo.buildinfo(this, rawinfo);

            ItemInfo.Name nm = ItemInfo.find(ItemInfo.Name.class, info);
            if(nm!=null)
            {
                if(meter > 0)
                {
                    String newtext = nm.str.text + "   (" + meter + "% done)";
                    ItemInfo.Name newnm = new ItemInfo.Name(nm.owner, newtext);
                    int nameidx=info.indexOf(nm);
                    info.set(nameidx, newnm);
                }
            }
        }

        return(info);
    }

    public Resource resource() {
        return(res.get());
    }

    public String resname(){
        Resource res = null;
        try{
            res = resource();
        }
        catch(Loading l)
        {

        }
        if(res != null){
            return res.name;
        }
        return "";
    }

    public String name() {
        if(info != null) {
            Name name = ItemInfo.find(Name.class, info);
            return name != null ? name.str.text : null;
        }
        return null;
    }

    public void testMatch() {
        if(filtered < lastFilter){
            matched = filter != null && filter.matches(info());
            filtered = lastFilter;
        }
    }

    public boolean isSame(GItem that)
    {
        boolean same = true;

        if(!this.resname().equals(that.resname()))
            return false;

        List<ItemInfo> thisinfo = this.info();
        List<ItemInfo> thatinfo = that.info();

        for(ItemInfo this_ii : thisinfo)
        {
            //each adhoc on this object must also be found in the other
            if(ItemInfo.AdHoc.class.isInstance(this_ii))
            {
                ItemInfo.AdHoc this_adhoc = (ItemInfo.AdHoc)this_ii;
                boolean got_it = false;
                for(ItemInfo that_ii : thatinfo)
                {
                    if(ItemInfo.AdHoc.class.isInstance(that_ii))
                    {
                        ItemInfo.AdHoc that_adhoc = (ItemInfo.AdHoc)that_ii;
                        if(this_adhoc.str.text.equals(that_adhoc.str.text))
                        {
                            got_it = true;
                            break;
                        }
                    }
                }
                if(!got_it)
                    return false;
            }
        }

        for(ItemInfo that_ii : thatinfo)
        {
            //each adhoc on this object must also be found in the other
            if(ItemInfo.AdHoc.class.isInstance(that_ii))
            {
                ItemInfo.AdHoc that_adhoc = (ItemInfo.AdHoc)that_ii;
                boolean got_it = false;
                for(ItemInfo this_ii : thisinfo)
                {
                    if(ItemInfo.AdHoc.class.isInstance(this_ii))
                    {
                        ItemInfo.AdHoc this_adhoc = (ItemInfo.AdHoc)this_ii;
                        if(this_adhoc.str.text.equals(that_adhoc.str.text))
                        {
                            got_it = true;
                            break;
                        }
                    }
                }
                if(!got_it)
                    return false;
            }
        }

        return true;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(drop) {
            dropTimer += dt;
            if (dropTimer > 0.1) {
                dropTimer = 0;
                wdgmsg("take", Coord.z);
                ui.message("Dropping bat!", GameUI.MsgType.BAD);
            }
        }
    }

    public void uimsg(String name, Object... args) {
        if(name == "num") {
            int oldnum = num;
            num = (Integer)args[0];
        } else if(name == "chres") {
            res = ui.sess.getres((Integer)args[0]);
        } else if(name == "tt") {
            info = null;
            rawinfo = args;
            filtered = 0;
            if(sendttupdate){wdgmsg("ttupdate");}
            if(this.parent == ui.gui.maininv)
            {
                ui.gui.maininv.resort();
                OverviewTool.instance(ui).force_update();
            }
            //this is a hand object
            if(Config.autobucket && this.parent == ui.gui)
            {
                //this is an empty container
                if(rawinfo.length == 1 && ((Object[])rawinfo[0]).length>1)
                {
                    //this is a bucket
                    if(String.class.isInstance(((Object[])rawinfo[0])[1]))
                    {
                        String newname = (String) ((Object[])rawinfo[0])[1];
                        if(newname.equals("Bucket"))
                        {
                            //we are standing on a water tile
                            int tile = ui.sess.glob.map.gettile(ui.gui.map.player().rc.div(11.0f));
                            try{
                                Resource tilesetr = ui.sess.glob.map.tilesetr(tile);
                                if(tilesetr.name.contains("water"))
                                {
                                    //right-click once on our current location
                                    ui.gui.map.wdgmsg("itemact", ui.gui.map.player().sc, ui.gui.map.player().rc, 0);
                                }
                            }catch(Loading l) {//if the map is still loading, nothing should happen}
                            }
                        }
                    }
                }
            }
        } else if(name == "meter") {
            meter = (Integer)args[0];
        }
    }
    public Coord size() {
        Indir<Resource> res = resource().indir();
        if (res.get() != null && res.get().layer(Resource.imgc) != null) {
            Tex tex = res.get().layer(Resource.imgc).tex();
            if(tex == null)
                return new Coord(1, 1);
            else
                return tex.sz().div(30);
        } else {
            return new Coord(1, 1);
        }
    }

    public <T> Optional<T> getinfo(Class<T> type) {
        try {
            for (final ItemInfo info : info()) {
                if (type.isInstance(info)) {
                    return Optional.of(type.cast(info));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> Optional<T> getinfo(Class<T> type, List<ItemInfo> infolst) {
        try {
            for (final ItemInfo info : infolst) {
                if (type.isInstance(info)) {
                    return Optional.of(type.cast(info));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public <T> List<T> getinfos(Class<T> type) {
        final List<T> infos = new ArrayList<>();
        try {
            for (final ItemInfo info : info()) {
                if (type.isInstance(info)) {
                    infos.add(type.cast(info));
                }
            }
            return infos;
        } catch (Exception e) {
            return infos;
        }
    }
    public String[] getRawContents() {
        final ArrayList<String> contents = new ArrayList<>();

        for (ItemInfo.Contents cont : getinfos(ItemInfo.Contents.class)) {
            getinfo(ItemInfo.Name.Name.class, cont.sub)
                    .ifPresent((cnt) -> contents.add(cnt.str.text));
        }

        return contents.toArray(new String[0]);
    }
}
