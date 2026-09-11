package com.daengs.app.territory.support

internal const val OWNED_PET = "00000000-0000-0000-0000-000000000001"
internal const val OWNED_SITE = "territory-site:hex-v1:140:324:777"
internal fun ownedJson(petId: String? = null) = """{
  "status":"READY","season_id":"first","server_now_ms":1800000000000,"pet_id":${petId?.let { "\"$it\"" } ?: "null"},
  "total_count":2,"next_cursor":"next+/=","items":[{
  "site_id":"$OWNED_SITE","version":2,"pet_id":"$OWNED_PET","pet_name":"두부","pet_breed":"dog_bichon_frise",
  "certification":"VERIFIED","occupied_at":"2027-01-15T08:00:00Z","expires_at":"2027-01-18T08:00:00Z",
  "location":{"lat":37.5,"lng":127.0},"location_status":"AVAILABLE"}]}"""
