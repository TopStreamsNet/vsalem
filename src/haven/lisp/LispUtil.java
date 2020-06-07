package haven.lisp;

import haven.*;
import org.armedbear.lisp.*;


import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static java.lang.Thread.sleep;

public class LispUtil {
    public static long findObjectByNames(LispObject names) {
        Coord plc = UI.instance.gui.map.player().rc;
        double min = Double.MAX_VALUE;
        Gob nearest = null;
        synchronized (UI.instance.sess.glob.oc) {
            for (Gob gob : UI.instance.sess.glob.oc) {
                double dist = gob.rc.dist(plc);
                if (dist < min && dist > 0) {
                    boolean match = false;
                    LispObject inames = names;
                    LispObject name = inames.car();
                    try {
                        while (name.getBooleanValue()) {
                            if (gob.getres() != null && gob.getres().name.endsWith(name.getStringValue())) {
                                match = true;
                                break;
                            }
                            inames = inames.cdr();
                            name = inames.car();
                        }
                        if (match) {
                            min = dist;
                            nearest = gob;
                        }
                    } catch (Session.LoadingIndir ignored) {
                    } catch (Resource.Loading ignored) {
                    }
                }
            }
        }
        return ((nearest == null) ? 0 : nearest.id);
    }

    public static void pfRightClick(long gobid) {
        Gob destGob = UI.instance.sess.glob.oc.getgob(gobid);
        UI.instance.gui.map.pfRightClick(destGob, -1, 3, 0, null);
    }

    private static Coord coordToTile(Coord c) {
        return c.div(11);
    }

    public static void waitForFlowerMenu() {
        UI.instance.gui.syslog.append("_ waitForFlowerMenu", Color.RED);
        FlowerMenu menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        while (menu == null || menu.opts == null) {
            try {
                sleep(15);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        }
        UI.instance.gui.syslog.append("^ waitForFlowerMenu", Color.RED);
    }

    public static boolean choosePetal(String name) {
        UI.instance.gui.syslog.append("_ Choose petal", Color.RED);
        FlowerMenu menu = UI.instance.gui.ui.root.findchild(FlowerMenu.class);
        if (menu != null) {
            for (FlowerMenu.Petal opt : menu.opts) {
                if (opt.name.equals(name)) {
                    menu.choose(opt);
                    menu.destroy();
                    UI.instance.gui.syslog.append("^ Choose petal", Color.RED);
                    return true;
                }
            }
        }
        UI.instance.gui.syslog.append("^ Choose petal", Color.RED);
        return false;
    }

    public static boolean waitFlowerMenuClose() {
        while (UI.instance.gui.ui.root.findchild(FlowerMenu.class) != null) {
            try {
                sleep(25);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static int carryWeight() {
        return UI.instance.gui.weight;
    }

    public static int maxWeight() {
        return UI.instance.sess.glob.cattr.get("carry").getComp();
    }

    public static Cons findObjectsByNames(LispObject names) {
        Coord plc = UI.instance.gui.map.player().rc;
        double min = Double.MAX_VALUE;
        Cons objs = null;
        synchronized (UI.instance.sess.glob.oc) {
            for (Gob gob : UI.instance.sess.glob.oc) {
                double dist = gob.rc.dist(plc);
                if (dist < min && dist > 0) {
                    LispObject inames = names;
                    LispObject name = inames.car();
                    try {
                        while (name.getBooleanValue()) {
                            if (gob.getres() != null && gob.getres().name.endsWith(name.getStringValue())) {
                                if (objs == null) {
                                    objs = new Cons(LispInteger.getInstance(gob.id));
                                }else{
                                    objs = new Cons(LispInteger.getInstance(gob.id), objs);
                                }
                                break;
                            }
                            inames = inames.cdr();
                            name = inames.car();
                        }
                    } catch (Session.LoadingIndir ignored) {
                    } catch (Resource.Loading ignored) {
                    }
                }
            }
        }

        return objs;
    }

    public static boolean pfRightClick(LispObject xxx){
        System.out.println(xxx);
        return true;
    }

    public static void waitForWindow(LispObject name){
        Window window = UI.instance.gui.waitfForWnd(name.car().getStringValue(), 8000);
    }

    public static void waitForWindowClose(LispObject name){
        while(UI.instance.gui.getwnd(name.car().getStringValue()) != null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeWindow(LispObject name){
        Window window = UI.instance.gui.getwnd(name.car().getStringValue());
        if (window != null){
            window.reqdestroy();
        }
    }

    public static LispObject getWindow(LispObject name){
        Window window = UI.instance.gui.getwnd(name.car().getStringValue());
        return JavaObject.getInstance(window);
    }

    public static LispObject getInventory(Window window){
        for(Widget wdg = window.lchild; wdg!=null; wdg = wdg.prev) {
            if (wdg instanceof Inventory){
                return JavaObject.getInstance((Inventory)wdg);
            }
        }
        return null;
    }

    public static void listInventory(Inventory inv){
        for(Widget witm = inv.child; witm != null; witm = witm.next) {
            synchronized(witm) {
                if(witm instanceof WItem) {
                    WItem witem = (WItem) witm;
                    System.out.println(witem.item.resname()+" "+ witem.c.div(Inventory.sqsz));

                }
            }
        }
    }

    public static Cons getInventoryItemsByNames(Inventory inv, LispObject names){
        Cons objs = null;
        for(Widget witm = inv.child; witm != null; witm = witm.next) {
            synchronized(witm) {
                if(witm instanceof WItem) {
                    WItem witem = (WItem) witm;
                    LispObject inames = names;
                    LispObject name = inames.car();
                    try {
                        while (name.getBooleanValue()) {
                            if (witem.item.resname() != null && witem.item.resname().endsWith(name.getStringValue())) {
                                if (objs == null) {
                                    objs = new Cons(JavaObject.getInstance(witem));
                                }else{
                                    objs = new Cons(JavaObject.getInstance(witem), objs);
                                }
                                break;
                            }
                            inames = inames.cdr();
                            name = inames.car();
                        }
                    } catch (Session.LoadingIndir ignored) {
                    } catch (Resource.Loading ignored) {
                    }

                }
            }
        }
        return objs;
    }

    public static void activateItemMod(WItem witem, int mod){
        witem.item.wdgmsg("iact", Coord.z, mod);
    }

    public static void activateItem(WItem witem){
        witem.item.wdgmsg("iact", Coord.z);
    }

    public static LispObject getItemAtHand(){
        if(UI.instance.gui.vhand == null){
            return null;
        }else{
            return JavaObject.getInstance(UI.instance.gui.vhand);
        }
    }

    public static void takeItem(WItem witem) {
        witem.item.wdgmsg("take", Coord.z);
        while(getItemAtHand() != null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public static void itemact(WItem witem, int mod){
        witem.item.wdgmsg("itemact", mod);
    }

    public static void drop(Inventory inv, int x, int y){
        inv.wdgmsg("drop", new Coord(x, y));
    }

    public static void drop(Inventory inv, Coord coord){
        inv.wdgmsg("drop", coord);
    }

    public static void transferItem(WItem witem){
        witem.item.wdgmsg("transfer", Coord.z);
    }

    public static void login(String account, String password) {
        for (Map.Entry<Integer, Widget> entry: UI.instance.widgets.entrySet()){
            if(entry.getValue() instanceof LoginScreen){
                entry.getValue().wdgmsg("login", account, password);
                return;
            }
        }
    }

    public static void play(String name){
        while(true) {
            for (Map.Entry<Integer, Widget> entry : UI.instance.widgets.entrySet()) {
                if (entry.getValue() instanceof Charlist) {
                    entry.getValue().wdgmsg("play", name);
                    return;
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void waitForEmptyHand() {
        while(getItemAtHand() != null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public static void waitForItemHand() {
        while(getItemAtHand() == null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void waitForTextEntry() {
        while(getTextEntry() == null){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void enterText(TextEntry t, String arg) {
        t.wdgmsg("activate", arg);
    }

    public static LispObject getTextEntry(){
        for (Map.Entry<Integer, Widget> entry: UI.instance.widgets.entrySet()){
            if(entry.getValue() instanceof TextEntry){
                TextEntry t = (TextEntry) entry.getValue();
                    return JavaObject.getInstance(t);
            }
        }
        return null;
    }

    public static LispObject getFreeSlotForItem(Inventory inv, WItem item){
        int[][] inventoryMatrix = containerMatrix(inv);
        int[][] d = new int[inventoryMatrix.length][inventoryMatrix[0].length];

        int sizeX = item.item.size().x;
        int sizeY = item.item.size().y;
        System.out.println(item.item.resname()+" size "+sizeX+"x"+sizeY);
        System.out.println("Inventory matrix: "+inventoryMatrix[0].length+"x"+inventoryMatrix.length);

        for (int i=0;i<inventoryMatrix.length;i++){
            for(int j=0;j<inventoryMatrix[0].length;j++){
                if(slotsFree(inventoryMatrix, i, j, sizeX, sizeY)){
                    return JavaObject.getInstance(new Coord(i,j));
                }
                //System.out.print(inventoryMatrix[i][j]);
            }
            //System.out.println("");
        }

        return null;
    }

    public static boolean slotsFree(int[][] slots, int x, int y, int sizeX, int sizeY){
        if(!(x+sizeX < slots.length) || !(y+sizeY < slots[0].length)){
            return false;
        }
        System.out.print(x+" : "+y);
        for(int i=x;i<x+sizeX;i++){
            for(int j=y;j<y+sizeY;j++){
                System.out.println(" "+i+" : "+j+"  = "+slots[i][j]);
                if(slots[i][j] == 1){
                    return false;
                }
            }

        }
        return true;
    }

    public static int[][] containerMatrix(Inventory inv) {
        int[][] ret = new int[inv.isz.x][inv.isz.y];
        for(WItem item: inv.getInventoryContents()) {
            int xSize = item.item.size().x;
            int ySize = item.item.size().y;
            int xLoc = item.c.div(Inventory.sqsz.x).x;
            int yLoc = item.c.div(Inventory.sqsz.y).y;
            //System.out.println(item.item.resname()+" size "+xSize+"x"+ySize+" location "+xLoc+"x"+yLoc);

            for(int i = 0; i < xSize; i++) {
                for(int j = 0; j < ySize; j++) {
                    ret[i + xLoc][j + yLoc] = 1;
                }
            }
        }
        return ret;
    }

    public static Cons findFruitTrees() {
        synchronized (UI.instance.sess.glob.oc) {
            for (Gob gob : UI.instance.sess.glob.oc) {
                ResDrawable rd = gob.getattr(ResDrawable.class);
                boolean fruittree = false;

                if (rd != null && rd.res != null) {
                    fruittree = rd.res.get().name.contains("apple") ||
                            rd.res.get().name.contains("cherry") ||
                            rd.res.get().name.contains("mulberry") ||
                            rd.res.get().name.contains("pear") ||
                            rd.res.get().name.contains("peach") ||
                            rd.res.get().name.contains("persimmon") ||
                            rd.res.get().name.contains("plum") ||
                            rd.res.get().name.contains("snozberry");
                }
                try {
                    if (fruittree) {
                        System.out.println("" + rd.res.get().name);
                        Field privateSpriteField = ResDrawable.class.getDeclaredField("spr");
                        privateSpriteField.setAccessible(true);
                        Sprite spr = (Sprite) privateSpriteField.get(rd);
                        System.out.println(((StaticSprite) spr).parts.length);
                        Field privateMessageField = ResDrawable.class.getDeclaredField("sdt");
                        privateMessageField.setAccessible(true);
                        Message sdt = (Message) privateMessageField.get(rd);
                        System.out.println("QQ"+sdt.toString());
                    }
                }catch (NoSuchFieldException|IllegalAccessException ex){

                }
            }
        }
        return null;
    }


}
