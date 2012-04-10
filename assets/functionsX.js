        $(function() {
            
            var receiveEvent = function(event) {
                var data = JSON.parse(event.data)
                
                console.log("Event " + data.id + " " + data)
                // Handle errors
                if (data.error) {
                    watchSocket.close()
                    //$("#instances").clear()
                    $("#onError span").text(data.error)
                    $("#onError").show()
                    return
                } else {
                    //$("#onChat").show()
                }
                
                var ele = $("#"+data.id);
console.log("e" + ele.size())
                if (ele.size() > 1) {
//                  ele.replaceWith($(data.html))
                  ele.replaceWith($('<p>OK</p>'))
console.log("found")
                } else {
                  var group = $('#'+data.groupId)
                  if (group.size() == 0) {
                    group = $('<div id="' + data.groupId + '"><h2>'+
                      data.groupName+'</h2></div>')
                    group.appendTo($('#groups'))
                  }
                  $(data.html).appendTo(group)
                }
                //ele.animate({opacity: 1.0});
                //ele.animate({opacity: 0.4});
            }
            
            WEB_SOCKET_SWF_LOCATION = "/assets/WebSocketMain.swf";
            
            var connect = function() {
              //console.log("trying to connect");
			  var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
              var watchSocket = new WS("ws://" + HOSTNAME + "/watch")
              var reconnect = function() { };
              watchSocket.onclose = function() {
                setTimeout(connect, 5000);
              };
              watchSocket.onopen = function() {
                 $('#groups').load('/partial');
                 $('<p>JJ</p>').appendTo($("#groups"))

              };
              watchSocket.onmessage = receiveEvent
              //console.log("connected");
            }
            connect();
        })
    

