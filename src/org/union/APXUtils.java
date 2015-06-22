package org.union;

import haven.AuthClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class APXUtils {
	static {
		// Load Accounts Data
		accounts = new HashMap<String, AccountInfo>();
		_sa_load_data();
	}
        
	public static class AccountInfo implements Serializable {
		private static final long serialVersionUID = -8211372962031061806L;
		public String login;
		public AuthClient.Credentials cred;
		public byte[] token;

		public AccountInfo(String l, AuthClient.Credentials p, byte[] t) {
			login = l;
			cred = p;
			token = t;
		}

		public AccountInfo(String l, AuthClient.Credentials p) {
			this(l, p, null);
		}
	}

	public static HashMap<String, AccountInfo> accounts;

	public static void _sa_add_data(String login, AuthClient.Credentials cred) {
		if (!accounts.containsKey(login)) {
                    accounts.put(login, new AccountInfo(login,cred));
                    _sa_save_data();
		}
	}

	public static void _sa_save_data() {
                ObjectOutputStream sstream = null;
		try {
			File file = new File("data.bin");
			if (!file.exists()) {
				file.createNewFile();
			}
			FileOutputStream files = new FileOutputStream(file);
			sstream = new ObjectOutputStream(files);
			sstream.writeObject(accounts);
			sstream.flush();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
                finally 
                {
                    if(sstream != null)
                        try {
                            sstream.close();
                        } catch (IOException ex) {ex.printStackTrace();}
                }
	}

	public static void _sa_delete_account(String name) {
		accounts.remove(name);
		_sa_save_data();
	}

	@SuppressWarnings("unchecked")
	public static void _sa_load_data() {
		accounts.clear();
                ObjectInputStream sstream = null;
		try {
			FileInputStream file = new FileInputStream("data.bin");
			sstream = new ObjectInputStream(file);
			accounts = (HashMap<String, AccountInfo>) sstream.readObject();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} catch (ClassNotFoundException e) {
		}
                finally 
                {
                    if(sstream != null)
                        try {
                            sstream.close();
                        } catch (IOException ex) {ex.printStackTrace();}
                }
	}
}