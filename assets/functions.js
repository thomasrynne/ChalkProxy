$(function() {
    var animate = function(ele, from, to) {
          ele.css({opacity: from})
          ele.fadeTo(1000, to)
    }
    var enable = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          animate(ele.children('.main-link'), 0.4, 1.0)
          animate(ele.children('.icons'), 0.15, 1.0)
          animate(ele.children('.props'), 0.4, 1.0)
        })
    }
    var disable = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          animate(ele.children('.main-link'), 1.0, 0.4)
          animate(ele.children('.icons'), 1.0, 0.15)
          animate(ele.children('.props'), 1.0, 0.4)
        })
    }
    var show = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          var height = ele.height()
          ele.css({height:0 })
          ele.animate({height: height}, {duration: 1000})
        })
    }
    var hide = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          ele.css({display: 'block' })
          ele.animate({height: 0}, {duration: 1000})
          setTimeout(function() { ele.css({display: 'none' }) }, 1000)
        })
    }
    var receiveEvent = function(event) {
        var data = JSON.parse(event.data)
        var update = $(data.html)
        var group = $('#groups')
        group.replaceWith(update)
        if (window.SHOW_DISCONNECTED) {
          enable(data.enable)
          disable(data.disable)
        } else {
          show(data.enable)
          hide(data.disable)
        }
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
        Math.floor(Math.random()*10000)+'&'+window.PARAMS, function(data) {
          $('#groups').replaceWith(data)
      })
    }
    WEB_SOCKET_DEBUG = true
    window.WEB_SOCKET_SWF_LOCATION = "/assets/WebSocketMain.swf";
    var hasConnected = false
    var connect = function() {
        var wsHost = window.location.hostname
        if (window.location.port != "") {
          wsHost = wsHost + ":" + window.location.port
        }
        var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket
        var watchSocket = new WS("ws://"+wsHost+"/watch"+window.PATH)
        watchSocket.onclose = function() {
          //If we get onclose before onopen then the flash websocket plugin is not working
          var msg = 'Not connected'
          if (hasConnected) {
            msg = 'Disconnected'
            setTimeout(connect, 5000);
          }
          $('#status').html(msg)
        };
        watchSocket.onopen = function() {
            hasConnected = true
            reload()
            $('#status').html('Connected')
        };
        watchSocket.onmessage = receiveEvent
    }
    connect()
    reload()
})
