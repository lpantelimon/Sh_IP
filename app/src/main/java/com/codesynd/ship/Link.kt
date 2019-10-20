package com.codesynd.ship

import com.google.android.gms.maps.model.Polyline

data class Link (val from: Ship, val to: Ship, val line: Polyline)