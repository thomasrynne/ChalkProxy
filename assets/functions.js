$(function() {            
    var receiveEvent = function(event) {
        var data = JSON.parse(event.data)
        var update = $(data.html)
        var group = $('#groups')
        group.replaceWith(update)
        jQuery.each(data.enable, function(index, value) {
          var ele = $('#'+value).children()
 //         ele.css({opacity: 0.4})
//          ele.fadeTo(1000, 1.0)
        })
        jQuery.each(data.disable, function(index, value) {
          var ele = $('#'+value).children()
 //         ele.css({opacity: 1.0})
 //         ele.fadeTo(1000, 0.4)
        })
        jQuery.each(data.add, function(index, value) {
          var ele = $('#'+value)
          var height = ele.height()
          ele.css({height:0 })
          ele.animate({height: height}, {duration: 1000})
        })
        jQuery.each(data.updatedProperties, function(index, value) {
          var ele = $('#'+value)
          //var originalBg = ele.css("background-color")
	      //ele.css("backgroundColor", 'red').animate({backgroundColor: originalBg}, 2000)
        })
    }
    var reload = function() {
      $.get('/partial?random='+
        Math.floor(Math.random()*10000)+
        '&groups='+window.GROUPS, function(data) {
          $('#groups').replaceWith(data)
      })
    }
    WEB_SOCKET_DEBUG = true
    window.WEB_SOCKET_SWF_LOCATION = "/assets/WebSocketMain.swf";
    var connect = function() {
        var wsHost = window.location.hostname
        if (window.location.port != "") {
          wsHost = wsHost + ":" + window.location.port
        }
        var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
        var watchSocket = new WS("ws://"+wsHost+"/watch/"+window.GROUPS)
        watchSocket.onclose = function() {
            $('#status').html('Disconnected')
            setTimeout(connect, 5000);
        };
        watchSocket.onopen = function() {
            reload()
            $('#status').html('Connected')
        };
        watchSocket.onmessage = receiveEvent
    }
    connect()
    reload()
})
