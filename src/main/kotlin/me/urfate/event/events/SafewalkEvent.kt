package me.urfate.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event

class SafewalkEvent(var sneak : Boolean) : Event, Cancellable()
