package chalkproxy;

import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.atomic.*;
import java.net.*;
import java.io.*;
import org.json.*;

/**
 * Registers a web server instance with a chalkproxy server
 */
public class ChalkProxyClient {
	
	private final AtomicBoolean keepConnected = new AtomicBoolean(false);
	private final AtomicReference<Socket> socket = new AtomicReference<Socket>(null);
	
	private final String chalkProxyHostname;
	private final int chalkProxyPort;
	private final String serverName;
	private final String serverHostname;
	private final int serverPort;
	private final List<Icon> icons = new LinkedList<Icon>();
	private final List<Property> properties = new LinkedList<Property>();
    public ChalkProxyClient(String chalkProxyHostname, int chalkProxyPort,
    		String serverName, String serverHostname, int serverPort) {
    	this.chalkProxyHostname = chalkProxyHostname;
    	this.chalkProxyPort = chalkProxyPort;
    	this.serverName = serverName;
    	this.serverHostname = serverHostname;
    	this.serverPort = serverPort;
    }
    public ChalkProxyClient(String hostname, String serverName, String serverHost, int serverPort) {
    	this(hostname, 4000, serverName, serverHost, serverPort);
    }
    /**
     * Adds an icon to the registration details
     * @param imageURL the image to use for the icon
     * @param url the link for the icon
     * @param text the text for the icon
     */
    public void addIcon(String url, String text, String imageURL) {
    	icons.add(new Icon(url, text, imageURL));
    }
    /**
     * Adds a text based icon to the registration details
     * @param url the link for the icon
     * @param text the text for the icon
     */
    public void addIcon(String url, String text) {
    	icons.add(new Icon(url, text, null));
    }
    public void addProperty(String name, String value, String url) {
    	properties.add(new Property(name, value, url));
    }
    public void addProperty(String name, String value) {
    	addProperty(name, value, null);
    }
    public void start() {
    	boolean wasStopped = keepConnected.compareAndSet(false, true);
    	if (!wasStopped) throw new IllegalStateException("Already running");
		new Thread(new Runnable() { public void run() {
			int retryInterval = 5000;
			while (keepConnected.get()) {
				try {
					connectAndWait();
					retryInterval = 5000;
				} catch (Exception e) {
					retryInterval = Math.min(retryInterval * 2, 5*60*1000);
				}
		        if (keepConnected.get()) {
		    	  try {
		    		  Thread.sleep(retryInterval);
		    	  } catch (InterruptedException e) {}
		        }
		    }
	  } }, "ChalkProxy").start();
    }
    /**
     * Indicates wheter this registration is still being made
     * @return true if the registration is still being made
     */
    public boolean isStarted() {
    	return keepConnected.get();
    }
	/**
	 * Updates the value of an existing property
	 * @param name the name of the property to update
	 * @param value the new value for the property
	 */
	public void updateProperty(String name, String value) {
		Property found = null;
		for (Property property : properties) {
			if (property.name.equals(name)) {
				property.value = value;
				found = property;
			}
		}
		if (found == null) throw new IllegalStateException("Property " + name + " not found");
		Socket s = socket.get();
		final Property f = found;
		if (s != null) {
			try {
				s.getOutputStream().write((f.json() + "\n").toString().getBytes("utf8"));
			} catch (IOException e) {
		    } catch (JSONException e) {
		    	throw new RuntimeException(e);
		    }
		}
	}
	/**
	 * Explicitly removes this entry from the chalk proxy server's listing.
	 * There is usually no need to call this as the entry is automatically removed
	 * when the connection drops.
	 */
	public void stop() {
		keepConnected.set(false);
	    Socket s = socket.get();
	    if (s != null) {
	    	try {
	    		s.close();
	    	} catch (IOException e) {}
	    }
	    socket.set(null);
	}

	class Icon {
		Icon(String linkURL, String text, String imageURL) {
			this.linkURL = linkURL;
			this.imageURL = imageURL;
			this.text = text;
		}
		public String linkURL;
		public String imageURL;
		public String text;
		JSONObject json() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("url", linkURL);
			json.put("text", text);			
			if (imageURL != null) {
				json.put("image", imageURL);
			}
			return json;
		}
	}
	class Property {
		Property(String name, String value, String url) {
			this.name = name;
			this.value = value;
			this.url = url;
		}
		public String name;
		public String value;
		public String url;
		JSONObject json() throws JSONException {
		    JSONObject json = new JSONObject();
		    json.put("name", name);
		    json.put("value", value);
		    if (url != null) {
		  	  json.put("url", url);
		    }
		    return json;
		}
	}

	private JSONObject json() throws JSONException {
	    JSONObject json = new JSONObject();
	    json.put("name", serverName);
	    json.put("hostname", serverHostname);
	    json.put("port", Integer.toString(serverPort));
	    json.put("icons", jsonIcons());
	    json.put("props", jsonProperties());
	    return json;
	}
	private JSONArray jsonIcons() throws JSONException {
		JSONArray array = new JSONArray();
		for (int i = 0; i < icons.size(); i++) {
			array.put(i, icons.get(i).json());
		}
		return array;
	}
	private JSONArray jsonProperties() throws JSONException {
		JSONArray array = new JSONArray();
		for (int i = 0; i < properties.size(); i++) {
			array.put(i, properties.get(i).json());
		}
		return array;
	}
	private void connectAndWait() throws SocketException, IOException, JSONException {
    	Socket s = new Socket(chalkProxyHostname, chalkProxyPort);
	    socket.set(s);
	    s.getOutputStream().write((json() + "\n").getBytes("utf8"));
	    BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
	    String response = reader.readLine();
	    if (!response.equals("OK")) {
	    	System.out.println("Registration failed: " + response);
	    }
	    while(keepConnected.get()) {
	    	reader.readLine();
	    }
	}
	
	public static void main(String[] args) {
		//Just here to check that the code in About compiles
        ChalkProxyClient chalkProxyClient = new ChalkProxyClient("chalkproxyhost", "My Server Name", "myhostname", 8080);
        chalkProxyClient.addProperty("version", "v123");
        chalkProxyClient.addProperty("status", "OK");
        chalkProxyClient.addProperty("started", new java.util.Date().toString());
        chalkProxyClient.start();		
	}
}
