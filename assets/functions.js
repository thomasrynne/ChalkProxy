$(function() {
    var animate = function(ele, from, to) {
          ele.css({opacity: from})
          ele.fadeTo(1000, to)
    };
    var receiveEvent = function(event) {
        var data = JSON.parse(event.data)
        var update = $(data.html)
        var group = $('#groups')
        group.replaceWith(update)
        jQuery.each(data.enable, function(index, value) {
           var ele = $('#'+value)
         animate(ele.children('.main-link'), 0.4, 1.0)
         animate(ele.children('.icons'), 0.15, 1.0)
         animate(ele.children('.props'), 0.4, 1.0)
        })
        jQuery.each(data.disable, function(index, value) {
            var ele = $('#'+value)
          animate(ele.children('.main-link'), 1.0, 0.4)
          animate(ele.children('.icons'), 1.0, 0.15)
          animate(ele.children('.props'), 1.0, 0.4)
        })
        jQuery.each(data.add, function(index, value) {
          var ele = $('#'+value)
          var height = ele.height()
          ele.css({height:0 })
          ele.animate({height: height}, {duration: 1000})
        })
        jQuery.each(data.updatedProperties, function(index, value) {
          var ele = $('#'+value)
	      ele.css("backgroundColor", '#ffff9C').animate({backgroundColor: '#ffffff'}, 10000)
        })
    }
    var reload = function() {
      $.get('/partial?random='+
        Math.floor(Math.random()*10000)+
        '&groupBy='+window.GROUPBY+'&filter='+window.FILTER+"&design="+window.DESIGN, function(data) {
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
        var watchSocket = new WS("ws://"+wsHost+"/watch/"+window.GROUPBY+"/"+window.FILTER+"/"+window.DESIGN)
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
