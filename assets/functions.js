$(function() {
    var group = $('#groups')
    var animate = function(ele, to) {
          ele.fadeTo(1000, to)
    }
    var enable = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          animate(ele.children('.main-link'), 1.0)
          animate(ele.children('.icons'), 1.0)
          animate(ele.children('.props'), 1.0)
        })
    }
    var disable = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          animate(ele.children('.main-link'), 0.4)
          animate(ele.children('.icons'), 0.15)
          animate(ele.children('.props'), 0.4)
        })
    }
    var add = function(adds) {
        jQuery.each(adds, function(index, value) {
          var existing = $('#'+value.key)
          if (existing.size() > 0) {
            existing.stop()
            var height = existing.height()
            existing.html(value.html)
            existing.css({height: 'auto'})
            var naturalHeight = existing.height()
            existing.height(height)
            existing.animate({height: naturalHeight}, {duration: 2000 * (height/naturalHeight)})
          } else {
            var ele = $(value.html)
            if (value.after == "") {
              ele.prependTo($('#groups'))
            } else {
              ele.insertAfter( $('#'+value.after) )
            }
            var height = ele.height()
            ele.height(0)
            ele.animate({height: height}, {duration: 2000})
          }
        })
    }
    var remove = function(ids) {
        jQuery.each(ids, function(index, value) {
          var ele = $('#'+value)
          ele.css({display: 'block' })
          ele.stop().animate({height: 0}, {duration: 2000, complete: function(){ele.remove()}})
        })
    }
    var receiveEvent = function(event) {
        var data = JSON.parse(event.data)
        if (data.messageType == "fullupdate") {
          if (window.STATE != data.state) {
            group.replaceWith($(data.html))
            window.STATE = data.state
          }
        } else {
          if ((window.STATE +1) != data.state) {
          }
          window.STATE = data.state
          enable(data.enable)
          disable(data.disable)
          add(data.add)
          remove(data.remove)
          jQuery.each(data.updateProperties, function(index, prop) {
            var ele = $('#'+prop.key)
            ele.html(prop.html)
            ele.stop()
	        ele.css({backgroundColor: '#ffff9C'}).animate({backgroundColor: '#ffffff'}, 20000)
         })
      }
    }
    var reload = function() {
      $.getJSON('/partial?random='+
        Math.floor(Math.random()*10000)+'&'+window.PARAMS+'&state='+window.STATE, function(data) {
          if (data.nonEmpty) {
            window.STATE = data.state
            $('#groups').replaceWith(data.html)
          }
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
            //watchSocket.send(window.STATE)
            //reload()
            $('#status').html('Connected')
        };
        watchSocket.onmessage = receiveEvent
    }
    window.onunload = function(){} //fixes firefox back
    connect()
    reload()
})
