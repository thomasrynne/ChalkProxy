$(function() {
    var checkedInstances = {}
    function updateSelectionStyle(instance, checked) {
      if (checked) {
        instance.css({"background-color":"#ffff99"})
        instance.css({"border":"solid 1px black"})
      } else {
        instance.css({
          "background-color":"white",
          "border": "1px solid white",
          "border-bottom": "1px solid black"
        })
      }
    }

    function updateFilterLink() {
      var filter = "?"
      $.each(checkedInstances, function(key,value) {
        filter = filter + ":" + value
      })
      $("#filter-link").html("<a href='" + filter + "'>Apply Filter</a>")
    }

    function toggleSelection(instance) {
        var checkBox = instance.find(".checkbox")
        var checked = checkBox.is(':checked')
        checkedInstances[instance.attr('id')] = checked
        updateSelectionStyle(instance, checked)
    }

    function setupSelectionHandlers(instances) {
      instances.each(function(index,value) { console.log(value)})
      instances.mouseenter(function() {
        console.log("over")
        $(this).find(".checkbox").show()
      })
      instances.mouseleave(function() {
        console.log("out")
        $(this).find(".checkbox").not(":checked").hide()
      })
      instances.find('.checkbox').click(function(e) {
        var thisInstance = $(this).parent().parent()
        toggleSelection(thisInstance)
        updateFilterLink()
      }).each(function(index, instance) {
        var thisInstance = $(instance).parent().parent()
        var instanceId = thisInstance.attr('id')
        if (checkedInstances[instanceId]) {
          updateSelectionStyle(thisInstance, true)
          thisInstance.find(".checkbox").prop('checked', true).show()
        }
      })
    }

    setupSelectionHandlers($('.instance'))

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
            existing.replaceWith(value.html)
            existing = $('#'+value.key)
            existing.css({height: 'auto'})
            var naturalHeight = existing.height()
            existing.height(height)
            existing.animate({height: naturalHeight}, {duration: 2000 * (height/naturalHeight),
              onComplete: function() { existing.css({height: 'auto'}) }
            })
            setupSelectionHandlers(existing)
          } else {
            var ele = $(value.html)
            if (value.after == "") {
              ele.prependTo($('#groups'))
            } else {
              ele.insertAfter( $('#'+value.after) )
            }
            var height = ele.height()
            ele.height(0)
            ele.animate({height: height}, {duration: 2000, complete: function(){
              ele.css({height: 'auto'})
            }})
            setupSelectionHandlers(ele)
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
          setTimeout(function() { location.reload(false) }, 0)
        } else if (data.messageType == "init") {
          $('#status').html('Connected')
          if (window.STATE !== data.state) {
            group.replaceWith($(data.html))
            setupSelectionHandlers($('.instance'))
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
          jQuery.each(data.updateIcons, function(index, icon) {
            var ele = $('#'+icon.key)
            ele.stop()
            ele.fadeTo(500, 0, function() {
              ele.html(icon.html)
              ele.fadeTo(500, 1.0)
            })
          })
          jQuery.each(data.updateProperties, function(index, prop) {
            var ele = $('#'+prop.key)
            ele.html(prop.html)
            ele.parent().parent().css({height: 'auto'})
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
