package haven;

import haven.*;
import java.awt.Color;

public class DrinkTea implements Runnable {

	GameUI gui;
        String need;

	public DrinkTea(GameUI gui, String need) {
		this.gui = gui;
                this.need = need;
	}

	@Override
	public void run() {
		drink();
	}

	private void drink() {
                gui.syslog.append("Drinking Tea - "+need, Color.CYAN);
                
		// Don't attempt to drink if flower menu is already open or we are already drinking
		if(gui.ui.root.findchild(FlowerMenu.class) != null || gui.drinkingTea)
			return;
		gui.drinkingTea = true;
		Equipory e = gui.getEquipory();
                if(e == null)
                    return;
                WItem flask = e.slots[6];
                if(flask == null) return;
                String liquid = "";
                if(need.endsWith("/phlegmplus")){
                    liquid = "White Tea";
                }else if(need.endsWith("/ybileplus")){
                    liquid = "Yellow Tea";
                }else if(need.endsWith("/bloodplus")){
                    liquid = "Green Tea";
                }else if(need.endsWith("/bbileplus")){
                    liquid = "Black Tea";
                }
                if (flask.contentName.get().endsWith(liquid)){
                    gui.syslog.append("Drinking "+liquid+" for "+need, Color.GREEN);
                    flask.item.wdgmsg("iact", Coord.z, 3);
                    FlowerMenu menu = gui.ui.root.findchild(FlowerMenu.class);
                    int retries = 0;
                    while (menu == null || menu.opts == null) {
                        if (retries++ > 100) {
                            gui.drinkingTea = false;
                            return;
                        }
                        sleep(50);
                        menu = gui.ui.root.findchild(FlowerMenu.class);
                    }
                    for (FlowerMenu.Petal opt : menu.opts) {
                        if (opt.name.equals("Sip")) {
                            menu.choose(opt);
                            menu.destroy();
                        }
                    }
                    gui.lastDrinkingSucessful = true;
                }else{
                    gui.syslog.append("Wrong liquid "+flask.contentName.get()+" for "+need, Color.RED);
                    gui.lastDrinkingSucessful = false;
                }
                gui.drinkingTea = false;
	}

	private boolean canDrinkFrom(WItem item) {
		ItemInfo.Contents contents = getContents(item);
		if (contents != null && contents.sub != null) {
			synchronized(item.item.ui) {
				for(ItemInfo info : contents.sub) {
					if(info instanceof ItemInfo.Name) {
						ItemInfo.Name name = (ItemInfo.Name) info;
						if(name.str != null && name.str.text.contains("Water"))
							return true;
					}
				}
			}
		}
		return false;
	}

	private void sleep(int timeInMs) {
		try {
			Thread.sleep(timeInMs);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	private ItemInfo.Contents getContents(WItem item) {
		if(item == null)
			return null;
		synchronized(item.item.ui) {
			try {
				for(ItemInfo info : item.item.info())
					if(info != null && info instanceof ItemInfo.Contents)
						return (ItemInfo.Contents) info;
			} catch(Loading ignored) {
			}
		}
		return null;
	}
}
