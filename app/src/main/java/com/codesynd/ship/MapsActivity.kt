package com.codesynd.ship

import android.graphics.Color
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

import kotlin.random.Random
import android.widget.TextView
import android.graphics.Typeface
import android.os.AsyncTask
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Button
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    val random = Random(System.currentTimeMillis())
    val newYork = Ship("New York", LatLng(40.6638, -74.0896),null, null)
    val southampton = Ship("Southampton", LatLng(50.8979, -1.4242), null,null)
    val ships: MutableList<Ship> = mutableListOf()

    val departures = K.departures + (K.departures.map { Pair(it.first+24, it.second) })

    var globalTime = 0.0
    val advance30Minutes: Runnable = object: Runnable {
        override fun run() {
            advance(6.0)
            renderPins()
        }
    }

    private lateinit var infoContainer: LinearLayout
    private lateinit var infoName: TextView
    private lateinit var infoDetail: TextView
    private lateinit var infoConnect: Button
    private lateinit var nyLoad: TextView
    private lateinit var shLoad: TextView
    private var currentShip: Ship? = null
    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        findViewById<View>(R.id.advance).setOnClickListener {
            advance30Minutes.run()
        }
        infoContainer = findViewById(R.id.infoContainer)
        infoName = findViewById(R.id.name)
        infoDetail = findViewById(R.id.status)
        infoConnect = findViewById(R.id.connect)
        nyLoad = findViewById(R.id.nyLoad)
        shLoad = findViewById(R.id.shLoad)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {

            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {

                val info = LinearLayout(this@MapsActivity)
                info.orientation = LinearLayout.VERTICAL

                val title = TextView(this@MapsActivity)
                title.setTextColor(Color.BLACK)
                title.gravity = Gravity.CENTER
                title.setTypeface(null, Typeface.BOLD)
                title.text = marker.title

                val snippet = TextView(this@MapsActivity)
                snippet.setTextColor(Color.GRAY)
                snippet.text = marker.snippet

                info.addView(title)
                info.addView(snippet)

                return info
            }
        })

        mMap.setOnMarkerClickListener {
            if(!it.isInfoWindowShown){
                for(ship in ships){
                    if(ship.marker?.isInfoWindowShown == true){
                        ship.marker!!.hideInfoWindow()
                    }
                }

                val marker = it
                currentShip = ships.find { it.marker == marker }
                infoContainer.visibility = View.VISIBLE
                infoName.text = currentShip?.name ?: ""
                infoDetail.text = detailForShip(currentShip)+throughputInfoForShip(currentShip)
                if(currentShip?.online == true){
                    infoConnect.setText("Disconnect")
                } else {
                    infoConnect.setText("Connect")
                }
                it.showInfoWindow()
            } else {

            }
            true
        }

        mMap.setOnInfoWindowCloseListener {
            currentShip = null
            infoContainer.visibility = View.GONE
        }

        infoConnect.setOnClickListener( {
            if(currentShip != null){
                currentShip!!.online = !currentShip!!.online
                renderPins()
            }
        })

        newYork.marker = mMap.addMarker(MarkerOptions().position(newYork.location).title(newYork.name))
        southampton.marker = mMap.addMarker(MarkerOptions().position(southampton.location).title(southampton.name))

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(45.818162, -37.491974), 10f))

        advance(8*24.0)
        renderPins()
    }

    fun advance(allHours: Double){
        var hours = allHours
        while(hours > 24){
            advance(24.0)
            hours -= 24.0
        }

        var oldTime = globalTime - (globalTime.toLong()/24)*24.0
        val newTime = oldTime + hours
        for( departure in departures){
            if(departure.first > oldTime && departure.first <= newTime){
                advanceInternal(departure.first - oldTime)
                oldTime = departure.first
                addShips(newYork, departure.second)
            }
        }
        advanceInternal(newTime - oldTime)
    }

    fun advanceInternal(hours: Double){
        if(hours > 0.000001) {
            globalTime += hours
            val advanceFactor = 1 / (8 * 24 / hours)
            val advance = LatLng(
                (southampton.location.latitude - newYork.location.latitude) * advanceFactor,
                (southampton.location.longitude - newYork.location.longitude) * advanceFactor
            )
            for (ship in ships) {
                advanceShip(ship, hours, advance)
            }
        }
    }

    fun addShips(port: Ship, count: Int){
        for(i in 0 until count){
            val name = K.shipNames.get(random.nextInt(K.shipNames.size))
            val offset = K.rowOffsets.get(count).get(i)
            val location = LatLng(port.location.latitude + offset.latitude, port.location.longitude + offset.longitude)
            val destination = if(port==newYork) southampton else newYork
            val marker = mMap.addMarker(MarkerOptions().position(location).title(name))
            val ship = Ship(name, location, destination, marker)
            ships.add(ship)
        }
    }

    fun advanceShip(ship: Ship, hours: Double, advance: LatLng){
        val time = ship.time + hours
        if(time >= K.travelTimeHours && ship.marker != null){
            ship.marker!!.remove()
            ship.marker = null
        } else {
            val direction = if (ship.destination == southampton) 1 else -1
            val finalLocation = LatLng(
                ship.location.latitude + advance.latitude * direction,
                ship.location.longitude + advance.longitude * direction
            )
            ship.location = finalLocation
            ship.time += hours
        }
    }

    fun linkShips(){
        ships.sortBy { it.location.longitude }
        val clusterOffset = Math.abs((newYork.location.longitude - southampton.location.longitude)/K.clusters)
        for(ship in ships){
            updateLink(newYork, ship)
            updateLink(southampton, ship)
        }
        for(i in 0 until (ships.size-1)){
            for(j in (i+1) until ships.size){
                val first = ships.get(i)
                val second = ships.get(j)
                if((first.location.longitude - second.location.longitude)<clusterOffset) {
                    updateLink(first, second)
                }
            }
        }
    }

    fun updateLink(first: Ship, second: Ship){
        val distance = FloatArray(1)
        if(first.marker == null || !first.online){
            first.links.forEach {
                if(it.from == first){
                    it.to.links.remove(it)
                    it.line.remove()
                } else {
                    it.from.links.remove(it)
                    it.line.remove()
                }
                it.line.remove()
            }
            first.links.clear()
        }
        if(second.marker == null || !second.online){
            second.links.forEach {
                if(it.from == second){
                    it.to.links.remove(it)
                    it.line.remove()
                } else {
                    it.from.links.remove(it)
                    it.line.remove()
                }
                it.line.remove()
            }
            second.links.clear()
        }
        if(first.marker != null && first.online && second.marker != null && second.online){
            var existing = first.links.find {
                (it.from == first && it.to == second) || (it.from == second && it.to == first)
            }
            Location.distanceBetween(
                first.location.latitude,
                first.location.longitude,
                second.location.latitude,
                second.location.longitude,
                distance);

            if(distance[0] < K.connectionRangeMeters) {
                if (existing == null) {
                    val line =
                        mMap.addPolyline(PolylineOptions().add(first.location, second.location))
                    existing = Link(first, second, line)
                    first.links.add(existing)
                    second.links.add(existing)
                }
                val points = mutableListOf<LatLng>()
                points.add(first.location)
                points.add(second.location)
                existing.line.points = points

                existing.line.color = K.lineColorDisconnected
            } else {
                if (existing != null){
                    first.links.remove(existing)
                    second.links.remove(existing)
                    existing.line.remove()
                }
            }
        }
    }

    fun removeShips(){
        ships.removeIf { it.marker == null }
    }

    private val next = mutableListOf<Pair<Ship, Int>>()
    fun updateDistances(){
        next.clear()
        for(ship in ships){
            ship.distance = Int.MAX_VALUE
        }
        newYork.distance = 0
        newYork.gateway = newYork
        southampton.distance = 0
        southampton.gateway = southampton
        next.add(Pair(newYork, 0))
        next.add(Pair(southampton, 0))
        updateDistance()
    }

    fun updateThroughput(){
        updateThroughput(newYork)
        updateThroughput(southampton)
        for (ship in ships){
            updateThroughput(ship)
        }
    }

    fun updateThroughput(ship: Ship){
        if(ship == newYork || ship == southampton){
            ship.maxThroughPut = K.throughputPort
        } else if(!ship.online){
            ship.maxThroughPut = 0.0

        } else {
            ship.maxThroughPut = K.throughputAntenna
        }
    }

    fun updateOwnTraffic(){
        updateOwnTraffic(newYork)
        updateOwnTraffic(southampton)
        for(ship in ships) {
            updateOwnTraffic(ship)
        }
    }

    fun updateOwnTraffic(ship: Ship){
        if(ship == newYork || ship == southampton){
            ship.ownTraffic = 0.0
        } else if(!ship.online){
            ship.ownTraffic = 0.0

        } else {
            ship.ownTraffic = Math.random() * K.maxOwnTraffic
        }
    }

    fun updateOutsideTraffic(){
        val sortedByDistance = ships.sortedBy { -it.distance }
        newYork.outsideTraffic = 0.0
        southampton.outsideTraffic = 0.0
        for(ship in ships){
            ship.outsideTraffic = 0.0
        }
        for(ship in sortedByDistance){
            updateOutsideTraffic(ship)
        }
    }

    fun updateOutsideTraffic(ship: Ship){
        if(!ship.online){
            ship.outsideTraffic = 0.0
        } else {
            propagateOutsideTraffic(ship)
        }
    }

    fun updateSpeed(){
        val newYorkEdgeLoad = newYork.outsideTraffic / newYork.links.sumByDouble {
            if(it.from == newYork) it.to.maxThroughPut else it.from.maxThroughPut
        }
        val southamptonEdgeLoad = southampton.outsideTraffic / southampton.links.sumByDouble {
            if(it.from == southampton) it.to.maxThroughPut else it.from.maxThroughPut
        }
        runOnUiThread({
            nyLoad.setText(String.format("New York edge load: %.2f", newYorkEdgeLoad))
            shLoad.setText(String.format("Southampton edge load: %.2f", southamptonEdgeLoad))
        })
        for(ship in ships){
            ship.speed = ship.ownTraffic / (if (ship.gateway == newYork) newYorkEdgeLoad else southamptonEdgeLoad)
        }
    }

    private fun propagateOutsideTraffic(ofShip: Ship): Unit {
        val outbound = ofShip.links.map {
            if(it.to == ofShip && (it.from.distance < ofShip.distance)){
                it.from
            } else if(it.from == ofShip && (it.to.distance < ofShip.distance)) {
                it.to
            } else null
        }.filter {it != null}

        val spread = (ofShip.ownTraffic + ofShip.outsideTraffic)*K.redundancy/outbound.size
        for(outship in outbound){
            outship!!.outsideTraffic += spread
        }
    }

    fun updateDistance(){
        if(next.isEmpty()){
            return
        }
        val pair = next.get(0)
        val ship = pair.first
        val distance = pair.second
        next.removeAt(0)
        if(ship.distance < distance){
            //skip as we already have a route w/ fewer hops
            updateDistance()
            return
        }
        ship.distance = distance
        ship.links.forEach {
            it.line.color = K.lineColorConnected
        }
        val d = distance+1
        for(link in ship.links){
            if((link.to == ship) && (link.from.distance > d)){
                link.from.distance = d
                link.from.gateway = ship.gateway
                next.add(Pair(link.from, d))
            } else if((link.from == ship) && (link.to.distance > d)){
                link.to.distance = d
                link.to.gateway = ship.gateway
                next.add(Pair(link.to, d))
            }
        }
        updateDistance()
    }

    fun renderPins(){
        preRender()
        for(ship in ships){
            ship.marker!!.position = ship.location
            ship.marker!!.snippet = detailForShip(ship)

            if(!ship.online){
                ship.marker!!.setIcon(BitmapDescriptorFactory.defaultMarker(K.pinHueDisabled))
            } else if(ship.distance == Int.MAX_VALUE){
                ship.marker!!.setIcon(BitmapDescriptorFactory.defaultMarker(K.pinHueDisconnected))
            } else {
                ship.marker!!.setIcon(BitmapDescriptorFactory.defaultMarker(K.pinHueConnected))
            }
            if(ship == newYork || ship == southampton){
                ship.marker!!.setIcon(BitmapDescriptorFactory.defaultMarker(K.pinHuePort))
            }
        }
    }

    fun preRender(){
        randomDisconnects()
        linkShips()
        removeShips()
        updateDistances()
        object: AsyncTask<Void?, Void?, Void?>(){
            override fun doInBackground(vararg params: Void?): Void? {
                updateThroughput()
                updateOwnTraffic()
                updateOutsideTraffic()
                updateSpeed()
                return null
            }

            override fun onPostExecute(result: Void?) {
                runOnUiThread({
                    infoDetail.setText(detailForShip(currentShip)+throughputInfoForShip(currentShip))
                })
            }
        }.execute(null)
    }

    fun randomDisconnects(){
        for(ship in ships){
            if(random.nextDouble() < K.failureChance){
                ship.online = false
            }
            if(random.nextDouble() < K.repairChance){
                ship.online = true
            }
        }
    }

    fun detailForShip(ship: Ship?): String {
        if(ship == null){
            return ""
        }
        return String.format("%.6f lat\n%.6f lon\n%.2f days at sea\n%d linked ships\n%d hops", ship.location.latitude, ship.location.longitude, ship.time/24, ship.links.size, ship.distance)
    }

    fun throughputInfoForShip(ship: Ship?): String{
        if(ship == null){
            return ""
        }
        return String.format("\n\n%s\nup to %.2f mb/s\n\n%.2f mb/s own traffic\n%.2f mb/s relayed traffic\n%.2f mb/s max throughput\n%.2f mb/s unused\n%d ms latency\n",
            if(!ship.online) "Out of service" else if(ship.distance != Int.MAX_VALUE) "Connected" else "Disconnected",
            if(ship.online && ship.distance != Int.MAX_VALUE) ship.speed else 0.0,
            ship.ownTraffic, ship.outsideTraffic, ship.maxThroughPut,
            ship.maxThroughPut - ship.ownTraffic - ship.outsideTraffic,
            if(ship.online && ship.distance != Int.MAX_VALUE) ship.distance*K.nodeLatency else 0)
    }
}
