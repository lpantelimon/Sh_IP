package com.codesynd.ship

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

data class Ship(
    var name: String,
    var location: LatLng,
    val destination: Ship?,
    var marker: Marker?,
    var time: Double = 0.0,
    var links: MutableList<Link> = mutableListOf(),
    var distance: Int = Int.MAX_VALUE,
    var online: Boolean = true,
    var maxThroughPut: Double = 0.0,
    var ownTraffic: Double = 0.0,
    var outsideTraffic: Double = 0.0,
    var speed: Double = 0.0,
    var gateway: Ship? = null
){
    override fun toString(): String {
        return name
    }
}