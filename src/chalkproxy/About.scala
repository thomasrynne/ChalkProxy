package chalkproxy
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.Request
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class About(title:String, registerPort:Int) extends AbstractHandler {
  override def handle(target:String, request:Request, httpRequest:HttpServletRequest, response:HttpServletResponse) {
    response.setContentType("text/html")
    response.getWriter.println("<!DOCTYPE html>")
    response.getWriter.println(page)
    request.setHandled(true)
  }
  
  val page = <html>
    <head>
        <title>{title} : About</title>
        <link rel="stylesheet" media="screen" href="/assets/bootstrap.css"/>
        <link rel="stylesheet" media="screen" href="/assets/main.css"/>
        <link rel="shortcut icon" type="image/png" href="/assets/favicon.png"/>
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
            <p><a href="http://github.com/thomas.rynne/ChalkProxy">Chalk Proxy</a> provides a single page to access a number of web servers which run on different hosts and ports.</p>
            <p>It is intended for use in a situation where a web based product is being developed and there are
               a number of different test instances running. These can become hard to manage. They often end up
               running on machines with host names which look like passwords and on ports which are hard to remember.</p>
            <p>By running ChalkProxy and getting the servers to register with it you get a single page which lists 
               all the servers. ChalkProxy also proxies for each server so the server name is in the url
               so you always know which one you are using.</p>
            <p>As well as providing a name, servers can provide arbitary properties so at a glance you can see things
               like which servers are on which branch/host/os/database/...</p>  
            <h2>Registering</h2>
            <p>To register a server with this ChalkProxy connect to port {registerPort} and send json like the following (but on ONE LINE)</p>
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
          </div>
        </div>
    </body>
</html>

}