(ns onyx.plugin.simple-plugin)

(defprotocol SimplePlugin
  (start [this]
    "Initialize the plugin, generally assoc'ing any initial state.")

  (stop [this event]
    "Shutdown the input and close any resources that needs to be closed.
     This can also be done using lifecycles."))

(extend-type Object
  SimplePlugin

  (start [this] this)

  (stop [this event] this))
