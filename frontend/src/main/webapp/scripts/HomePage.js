let wrapper =
  {
    infiniteScrollOffset:10,
    minId : null,
    maxId : null,
    appendNewMessagesAtBottom : true,
    scrollFetchSize : 30,
    initialFetchSize : 70,
    lastScrollValue : null,
    socket : null,
    init : function()
    {
      wrapper.socket = new WebSocket("ws://localhost:8081/logreceiver/websocket/api");
      wrapper.socket.onopen = function(e)
      {
        wrapper.send( "get_all_hosts" );
      };
      /*
       * Handle websocket message
       */
      wrapper.socket.onmessage = function(event) {
        let resp = JSON.parse(event.data);

        if (resp.cmd == 'get_all_hosts') {
          // Received all hosts, update drop-down selection
          let hosts = resp.payload; // payload is an array
          wrapper.getAllHosts(hosts);
        }
        else if ( resp.cmd == 'lazy_load' )
        {
          let messages = Array.from( resp.payload );

          let unloadTop = resp.top;
          let unloadBottom = ! resp.top;

          for ( let i = 0 ; i <messages.length ; i++ ) {
            wrapper.log( i+": received 'lazy_load' message: "+messages[i].id);
          }
          // note: server will send messages ordered ascending by message ID
          if ( messages.length > 0 )
          {
            let div = document.getElementById("container");

            let newElementCount = 0;
            if ( ! div.firstElementChild ) {
              // no messages displayed yet, just append whatever we received
              newElementCount = wrapper.appendMessages(messages);
            } else {
              // server will always send messages ordered ascending by ID
              let minNewId = messages[0].id;
              let maxNewId = messages[messages.length-1].id;

              let minOldId = wrapper.minId;
              let maxOldId = wrapper.maxId;
              if ( maxNewId <= minOldId || minNewId < minOldId ) {
                // insert at top
                wrapper.log("Inserting at top");
                messages = wrapper.removeDuplicates(messages);
                newElementCount = wrapper.prependMessages(messages);
              } else if ( minNewId >= maxOldId || maxNewId > maxOldId ) {
                // insert at bottom
                wrapper.log("Inserting at bottom");
                messages = wrapper.removeDuplicates(messages);
                newElementCount = wrapper.appendMessages(messages);
              } else {
                wrapper.log("Server sent ["+minNewId+"-"+maxNewId+"] while we have ["+minOldId+"-"+maxOldId+"]");
              }
            }

            // handle unloading before adding more messages
            // we'll only unload as many items as we received
            wrapper.log("new elements: "+newElementCount);
            if ( newElementCount > 0 )
            {
              if (unloadTop)
              {
                wrapper.log("unloading "+newElementCount+" elements at top");
                for (let toRemove = newElementCount; toRemove > 0 && div.firstElementChild ; toRemove--) {
                  div.firstElementChild.remove();
                }
              }
              else
              {
                wrapper.log("unloading "+newElementCount+" elements at bottom");
                for (let toRemove = newElementCount; toRemove > 0 && div.lastElementChild ; toRemove--) {
                  div.lastElementChild.remove();
                }
              }
              // update min/max IDs
              let children = div.children;
              if ( children.length > 0 )
              {
                wrapper.minId = children[0].getAttribute("entryId");
                wrapper.maxId = children[0].getAttribute("entryId");
                for (var i = 1; i < children.length; i++)
                {
                  let id = children[i].getAttribute("entryId");
                  wrapper.minId = Math.min( wrapper.minId, id);
                  wrapper.maxId = Math.max( wrapper.maxId, id);
                }
              } else {
                wrapper.minId = null;
                wrapper.maxId = null;
              }
            }
          }
        }
        else if ( resp.cmd == 'subscribe' )
        {
          // note: server will send messages ordered ascending by message ID
          let messages = Array.from( resp.payload );
          if ( wrapper.appendNewMessagesAtBottom )
          {
            for ( let i = 0 ; i <messages.length ; i++ ) {
              wrapper.log( i+": received 'subscribe' message: "+messages[i].id);
            }
            wrapper.appendMessages(messages);
            wrapper.scrollToBottom();
          } else {
            wrapper.log("Ignoring new messages, not at bottom");
          }
        } else {
          wrapper.log("error: unhandled websocket request "+resp);
        }
      };
      /*
       * Handle websocket getting closed.
       */
      wrapper.socket.onclose = function(event) {
        if (event.wasClean) {
          wrapper.log("[close] Connection closed cleanly, code="+event.code+" reason="+event.reason);
        } else {
          // e.g. server process killed or network down
          // event.code is usually 1006 in this case
          wrapper.log("[close] Connection died");
        }
      };
      /*
       * Handle websocket errors.
       */
      wrapper.socket.onerror = function(error) {
        wrapper.log("[error] "+error.message);
      };
    },
    scrollToBottom : function() {
      wrapper.lastScrollValue = null;
      let objDiv = document.getElementById("wrapper");
      objDiv.scrollTop = objDiv.scrollHeight;
    },
    hasMessages : function() {
      let div = document.getElementById("container");
      if ( div.firstElementChild ) {
        return true;
      }
      return false;
    },
    removeDuplicates : function(messages)
    {
      if ( wrapper.minId == null ) {
        return messages;
      }
      let minOldId = wrapper.minId;
      let maxOldId = wrapper.maxId;
      wrapper.log("checking "+messages.length+" messages for duplicates.");
      let elements = messages.length;
      for ( let i = 0 ; elements > 0 && i < messages.length ; ) {
        let msgId = messages[i].id;
        if ( msgId >= minOldId && msgId <= maxOldId ) {
          wrapper.log("removing duplicate msg with ID "+msgId);
          messages = messages.slice(i,1);
          elements--;
        } else {
          i++;
        }
      }
      wrapper.log("done checking.");
      return messages;
    },
    updateMinMax : function(msg) {
      if ( wrapper.minId == null ) {
        wrapper.minId = msg.id;
        wrapper.maxId = msg.id;
      } else {
        wrapper.minId = Math.min(wrapper.minId, msg.id);
        wrapper.maxId = Math.min(wrapper.maxId, msg.id);
      }
    },
    /**
     * Repaint log messages
     */
    appendMessages : function(messages)
    {
      // add child nodes to DOM
      let div = document.getElementById("container");
      for ( let i = 0 ; i < messages.length ; i++ ) {
        let msg = messages[i];
        wrapper.updateMinMax(msg);
        div.append(wrapper.createLogDiv(msg));
      }
      return messages.length;
    },
    prependMessages : function(messages) {
      // add child nodes to DOM
      let div = document.getElementById("container");
      if (div.childNodes.length == 0) {
        for (let i = 0; i < messages.length; i++) {
          let msg = messages[i];
          wrapper.updateMinMax(msg);
          div.append(wrapper.createLogDiv(msg));
        }
      } else {
        let previous = div.childNodes[0];
        for (let i = messages.length-1; i >= 0 ; i--) {
          let msg = messages[i];
          wrapper.updateMinMax(msg);
          let newNode = wrapper.createLogDiv(msg);
          div.insertBefore(newNode,previous);
          previous = newNode;
        }
        return messages.length;
      }
    },
    createLogDiv : function(msg) {
      let newDiv = document.createElement("div");
      newDiv.setAttribute("entryId" , msg.id ); // TODO: actually the entry timestamp as <seconds>.<nanos>
      newDiv.innerText = msg.text;
      return newDiv;
    },
    /**
     * Update UI with available hosts
     * @param hosts
     */
    getAllHosts : function(hosts)
    {
      var select = document.getElementById("hostSelection");
      let previousSelectedHostId = select.value;
      // remove all hosts that have gone missing in the meantime
      let existingOptions = Array.from( select.children );
      existingOptions.forEach( option =>
      {
        if ( ! hosts.find( host => host.id == option.getAttribute("value") ) ) {
          select.removeChild( option );
        }
      });
      // add new hosts
      hosts.forEach( host =>
      {
        let existingOptions = Array.from( select.children );
        let found = false;
        for (let i = 0; i < existingOptions.length ; i++)
        {
          if ( existingOptions[i].getAttribute("value") == host.id ) {
            found = true;
            break;
          }
        }
        if ( ! found ) {
          let option = document.createElement("option");
          option.setAttribute("value",host.id);
          option.innerText = host.name;
          select.append(option);
        }
      });
      // check whether the current selection changed
      let currentSelectedHostId = select.value;
      if ( previousSelectedHostId != currentSelectedHostId ) {

        if ( hosts.length > 0 )
        {
          let host = hosts[0];
          wrapper.send("subscribe", {hostId:host.id,maxCount:wrapper.initialFetchSize,regex:".*"});
        } else {
          wrapper.removeAllMessages();
        }
      }
    },
    /*
     * Remove all syslog messages from the div
     */
    removeAllMessages : function() {
      let div = document.getElementById("container");
      wrapper.minId = null;
      wrapper.maxId = null;
      wrapper.removeAllChildren(div);
    },
    /*
     * Send command via websocket
     */
    send : function(cmd,parameters)
    {
      if ( ! parameters ) {
        parameters = {};
      }
      parameters.cmd = cmd
      wrapper.socket.send( JSON.stringify(parameters) );
    },
    /*
     * Print a log message
     */
    log:function(msg) {
      if ( console && console.log ) {
        console.log(msg);
      } else {
        alert("ERROR: "+msg);
      }
    },
    /*
     * Remove all direct children of an element
     */
    removeAllChildren:function(elem)
    {
      while (elem.firstChild) {
        elem.removeChild(elem.firstChild);
      }
    },
    criteriaChanged:function()
    {
      var pattern = document.getElementById("regex").value;
      var host_id = document.getElementById("hostSelection").value;
      wrapper.removeAllMessages();
      wrapper.appendNewMessagesAtBottom = true;
      wrapper.send("subscribe",{hostId:host_id,maxCount:wrapper.initialFetchSize,regex: pattern});
    },
    checkInfiniteScroll : function() {

      if (!wrapper.hasMessages()) {
        return;
      }


      // check scroll direction
      let wrapperDiv = document.getElementById("wrapper");

      if ( wrapper.lastScrollValue == null ) {
        // sets lastscrollvalue
        wrapper.lastScrollValue = wrapperDiv.scrollTop;
        return;
      }

      let scrollingUp;
      if (wrapperDiv.scrollTop > wrapper.lastScrollValue) {
        wrapper.lastScrollValue = wrapperDiv.scrollTop;
        scrollingUp = false;
        wrapper.log("Scrolling down");
      } else if (wrapperDiv.scrollTop < wrapper.lastScrollValue) {
        wrapper.lastScrollValue = wrapperDiv.scrollTop;
        scrollingUp = true;
        wrapper.appendNewMessagesAtBottom = false;
        wrapper.log("Scrolling up");
      }

      let firstDiv = document.querySelector("#container > div:first-child");
      let lastDiv = document.querySelector("#container > div:last-child");

      let div = document.getElementById("container");
      let divChildren = Array.prototype.slice.call( div.children );

      /*
       * offsetTop - relative to the top of the "offset parent" (which is the closest
       *             RELATIVELY positioned parent)
       * clientHeight - inner height of an element in pixels (includes padding but excludes
       *                borders,margins and horizontal scrollbars)
       * scrollTop - An element's scrollTop value is a measurement of the
       *             distance from the element's top to its topmost visible content.
       */

      // ----
      // check whether we should try to get more entries for the
      // top of the page
      let firstDivBottomY = firstDiv.offsetTop + firstDiv.clientHeight;
      let pageTopY = wrapperDiv.scrollTop;

      if ( scrollingUp && pageTopY < firstDivBottomY + wrapper.infiniteScrollOffset )
      {
        let minId = divChildren[0].getAttribute("entryId");
        wrapper.log("at top of page, asking for ID < "+minId);
        wrapper.send("lazy_load", {refEntryId:minId, forwards:false,maxCount:wrapper.scrollFetchSize})
        return;
      }

      // check whether we should try to get more entries for the
      // bottom of the page
      let lastDivBottomY = lastDiv.offsetTop + lastDiv.clientHeight;
      let pageBottomY = wrapperDiv.scrollTop + wrapperDiv.clientHeight;

      if ( ! scrollingUp && pageBottomY > lastDivBottomY - wrapper.infiniteScrollOffset ) {
        let maxId = divChildren[divChildren.length-1].getAttribute("entryId");
        wrapper.log("at bottom of page, asking for ID > "+maxId);
        wrapper.appendNewMessagesAtBottom = true;
        wrapper.send("lazy_load", {refEntryId:maxId, forwards:true,maxCount:wrapper.scrollFetchSize})
      }
    }
  };