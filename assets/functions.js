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
    var receiveEvent = function(data) {
        if (data.messageType == "refresh") {
          location.reload(true)
        } else if (data.messageType == "init") {
          $('#status').html('Connected')
          if (window.STATE !== data.state) {
            group.replaceWith($(data.html))
          }
          window.STATE = data.state
        } else {
          if ((window.STATE + 1) != data.state) {
            //??
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

    //-------------

    var wsHost = window.location.hostname
    if (window.location.port != "") {
      wsHost = wsHost + ":" + window.location.port
    }

    var reconnectInterval = 1
    var sessionID = Math.floor(Math.random()*10000)
    var longPolling = function() {
      $.ajax({
		type: "get",
		url: "http://"+wsHost +"/poll?" + window.PARAMS,
		dataType: 'text',
        cache: false,
		data: {
          "serverStartId": window.SERVER_START_ID,
          "state": window.STATE,
          "sessionid":sessionID },
		success: function(data) {
          var data = JSON.parse(data)
          jQuery.each(data, function(index, value) {
            receiveEvent(value)
          })
          reconnectInterval = 1
          longPolling()
        },
		error: function (xhr, status, err) {
          if (status == "timeout") {
            longPolling()
          } else {
            $('#status').html("Disconnected.") 
            reconnectInterval = Math.min(reconnectInterval * 2, 20)
            setTimeout(longPolling, reconnectInterval*1000);
          }
		}
	  });
    }
    var wsConnect = function() {
        var watchSocket = new WebSocket("ws://"+wsHost+"/watch"+window.PATH)
        reconnectInterval = Math.min(reconnectInterval * 2, 20)
        watchSocket.onclose = function() {
          setTimeout(wsConnect, reconnectInterval*1000);
          $('#status').html("Disconnected")
        }
        watchSocket.onmessage = function(event) { 
          var data = JSON.parse(event.data)
          receiveEvent(data)
        }
        watchSocket.onopen = function() {
          reconnectInterval = 1
          watchSocket.send(JSON.stringify({
            "serverStartId":window.SERVER_START_ID,
            "state":window.STATE,
            "path":window.PATH}
          ))
        }
    }
    window.onunload = function(){} //fixes firefox back
    var f = window.WebSocket ? wsConnect : longPolling
    f()
})
