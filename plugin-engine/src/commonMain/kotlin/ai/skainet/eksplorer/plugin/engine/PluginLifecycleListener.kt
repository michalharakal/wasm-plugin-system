package ai.skainet.eksplorer.plugin.engine

import ai.skainet.eksplorer.plugin.PluginDescriptor

interface PluginLifecycleListener {
    fun onPluginLoaded(descriptor: PluginDescriptor)
    fun onPluginUnloaded(descriptor: PluginDescriptor)
    fun onPluginLoadFailed(sourceName: String, error: PluginException)
}
