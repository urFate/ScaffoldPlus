package me.urfate

import com.lambda.client.plugin.api.Plugin
import me.urfate.modules.ScaffoldPlus

internal object ScaffoldPlusPlugin : Plugin() {

    override fun onLoad() {
        // Load any modules, commands, or HUD elements here
        modules.add(ScaffoldPlus)

    }

    override fun onUnload() {
        // Here you can unregister threads etc...
    }
}