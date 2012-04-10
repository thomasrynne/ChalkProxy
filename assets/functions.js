$(function() {            
    var receiveEvent = function(event) {
        var data = JSON.parse(event.data)
        var update = $(data.html)
        var group = $('#groups')
        group.replaceWith(update)
        jQuery.each(data.enable, function(index, value) {
          var ele = $('#'+value)
          ele.css({opacity: 0.3})
          ele.animate({opacity: 1.0}, {duration: 1000})
        })
        jQuery.each(data.disable, function(index, value) {
          var ele = $('#'+value)
          ele.css({opacity: 1.0})
          ele.animate({opacity: 0.3}, {duration: 1000})
        })
        jQuery.each(data.add, function(index, value) {
          var ele = $('#'+value)
          var height = ele.height()
          ele.css({height:0 })
          ele.animate({height: height}, {duration: 1000})
        })
    }
    var reload = function() {
      $('#groups').load('/partial?random='+
        Math.floor(Math.random()*10000)+
        '&groups='+window.GROUPS)
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
