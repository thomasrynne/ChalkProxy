/* 
    ChalkProxy - a directory for a team's test web servers
    Copyright (C) 2012 Thomas Rynne

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    See http://www.gnu.org/copyleft/gpl.html

*/
package chalkproxy
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class About(registry:Registry, properties:ServerProperties) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    response.setContentType("text/html")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
  
  val title = if ("Chalk Proxy" == registry.name) "Chalk Proxy" else "Chalk Proxy: " + registry.name
  
  def page = <html>
    <head>
        <title>{title} : About</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap-2.0.2.css"/>
        <link rel="stylesheet" media="screen" href={registry.page.assetsHandler.url("/assets/main.css")}/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
        <style>{"""
         table, td {
           margin-left: auto;
           margin-right: auto;
           border-spacing: 3px;
           padding: 4px;
           border: 1px solid black;
         }
         body {
           margin-bottom: 4em;
         }
         #settings {
           width: 10em;
           margin-left: auto;
           margin-right: auto;
           text-align: center;
         }
        """}</style>
    </head>
    <body>
        <div class="container-fluid">
          <div class="row-fluid title">
            <div class="span3"><a href="/">Home</a></div>
            <h1  class="span6">{title}</h1>
            <div class="span3">&nbsp;</div>
          </div>
          <div class="row-fluid">
            <h2>About</h2>
            <p><a href="http://github.com/thomasrynne/ChalkProxy">Chalk Proxy</a> provides a single page to access
               a number of web servers which run on different hosts and ports.</p>
            <p>It is intended for use in a situation where a web based product is being developed and there are
               a number of different test instances running. These can become hard to manage. They often end up
               running on machines with host names which look like passwords and on ports which are hard to remember.</p>
            <p>By running ChalkProxy and getting the servers to register with it you get a single page which lists 
               all the servers.</p>
            <p>As well as providing a name, servers can provide arbitary properties so at a glance you can see things
               like which servers are on which branch/host/os/database/...</p>
            <p>ChalkProxy also proxies /&lt;serverName&gt;/ to the server so you know which server you are using by looking at the url.
                However, this only works if all local links are relative.</p>
            <h2 id="settings">Settings</h2>
            <div>
              <table>
                <tr><td>Http port</td><td>{properties.httpPort}</td></tr>
                <tr><td>Registration port</td><td>{properties.registrationPort}</td></tr>
                <tr><td>PID</td><td>{properties.pid}</td></tr>
                <tr><td>Pwd</td><td>{properties.pwd}</td></tr>
                <tr><td>Currently connected pages (approx)</td><td>{registry.watcherCount}</td></tr>
                <tr><td>Started</td><td>{properties.started}</td></tr>
              </table>
            </div>  
            <h2>Registering</h2>
            <p>To register a server with this ChalkProxy connect to port {properties.registrationPort} and send json like the following (but on ONE LINE)</p>
            <pre>{"""{
    "name": "D9",
    "hostname": "localhost", "port": "5005",
    "icons":[
      {"text":"Launch 2","image":"/assets/blowfish.png","url":"/go.jnlp"},
      {"text":"p","image":"","url":"/go.jnlp"}
    ],
    "props":[
      {"name":"branch","value":"master","url":"http://git/branches/master"},
      {"name":"commit","value":"fjfiwj5osj32"},
      {"name":"pwd","value":"/home/thomas/code/server/main"},
      {"name":"started","value":"20Apr2012 14:54"},
      {"name":"pid","value":"2543"}
    ]
}"""}</pre>
            <p>ChalkProxy will respond with 'OK' if the registration was sucessful. The registration will remain valid for as
            long as the tcp connection stays open. Whilst connected you can update properties by sending more json:</p>
            <pre>{""" {"name":"status", "value":"Amber", "url":"status"} """}</pre>

            <h2>Java Client</h2>
            <p>A java client is provided to make registrations easier. You will need chalkproxy-client.jar and json.jar in your
            classpath. These are both included in the download.</p>
            <pre><code>
            import chalkproxy.ChalkProxyClient
            ...
            ChalkProxyClient chalkProxyClient = new ChalkProxyClient("chalkproxyhost", "My Server Name", "myhostname", 8080);
            chalkProxyClient.addProperty("version", "v123");
            chalkProxyClient.addProperty("status", "OK");
            chalkProxyClient.addProperty("started", new java.util.Date().toString());
            chalkProxyClient.start();
            </code></pre>
            
            <h2>Links</h2>
            <p>A property can optionaly provide a url to make the property a link.</p>
            <p>If the url is relative (does not start with / or http://) then it will be updated with the prefix
            of the server name. So in the example above the status property will link to /D9/status</p>
            
            <h2>Listing</h2>
            <p>The url <a href='/list'>/list</a> lists all the registered servers in plain text</p>
            
            <h2>DNS</h2>
            <p>Half of the benifit of Chalk Proxy is that you don't need to remember strange host names and ports.
            With this in mind, consider setting up a sensible hostname for the machine running ChalkProxy (this machine)
            and using port 80. It is a bit tedious but at least you only need to do it once and then all the servers
            get pretty urls.</p>
            <p>The following commands might help with forwarding port 8080 to 80.
               <pre>sudo socat TCP-LISTEN:80,fork TCP:localhost:8080</pre>
               or<pre>iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080</pre> 
            </p>
            
            <h2>Debug</h2>
            <p>To help with diagnosing issues on Internet Explorer this link shows 
            the <a href="/?debug=firebuglite">home page with firebug lite</a>.</p>

          </div>
        </div>
    </body>
</html>

}