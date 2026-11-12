# Atlas — Privacy Policy

Last reviewed 10 November 2026.

This is the policy for Atlas (chizu, 地図), the app published from this repository.

## The short version

The developer collects nothing about you. There is no account, no sign-up, no analytics, no advertising and no crash reporting, and no server belonging to the developer that the app talks to. Atlas is an offline map: your position, searches, routes, favorites and recorded tracks are worked out and kept on your phone. The network is used to download the maps you choose, and for the few other things listed below, each because you used the feature. One of them is a route from a route server, for a place whose map is not on your phone: Atlas asks before it does that the first time.

## Where it comes from

Atlas is built from CoMaps (CoMaps Contributors), which continues Organic Maps (Organic Maps Contributors) and MAPS.ME (My.com B.V.). The map engine, search and routing are theirs; Atlas changes how the app looks and what it leaves out: it has no map editing and no OpenStreetMap sign-in. Atlas is a separate project and has no connection to CoMaps, Organic Maps or the OpenStreetMap Foundation.

Where the app processes personal data, that processing happens on your own device, under your control.

## What the app stores, and where

On the phone only:

- **Downloaded maps**, the region files you chose.
- **Favorites and recorded tracks**, with the names, notes and colors you give them.
- **Search history**, which you can clear in the search screen or switch off in Settings, Privacy.
- **Home and recent destinations**: the place you set as Home, and the last twelve places you started a drive to, so they are one tap away. "Clear Recent Places and Searches" on the search screen empties the list; holding the Home button lets you forget Home.
- **Settings**, and the last position of the map.

The manifest sets `allowBackup="false"` and excludes everything from Android's cloud backup and phone-to-phone transfer, so Android does not copy any of this off the phone. There is no cloud copy and no copy held by the developer.

Things leave the app's storage only when you ask:

- **Backup** (Settings) writes your favorites and tracks to a folder you choose, when you say or on the schedule you set.
- **Share and export** hand a place, your position, a favorites list or a track to the app you pick, through Android's share screen.

Uninstalling the app deletes all of it, apart from backup files and exports you saved elsewhere.

## Permissions, and what each is for

From the app's manifest (`android/app/src/main/AndroidManifest.xml`):

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`: your position on the map, and following a route. `ACCESS_LOCATION_EXTRA_COMMANDS`: asking the phone's positioning to refresh its satellite data for a faster fix.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`: navigation and track recording keep running with the screen off, with a notification that says so.
- `INTERNET`, `ACCESS_NETWORK_STATE`: downloading maps, and the other connections listed below; whether a connection is there, and whether it is Wi-Fi.
- `FOREGROUND_SERVICE_DATA_SYNC`: a map download carries on while the app is in the background.
- `POST_NOTIFICATIONS`: the notifications for navigation, track recording and downloads.
- `READ_CONTACTS`: asked for once, the first time you open the search, and the search works the same if you say no. If you allow it, typing a name in the search offers people from your contacts who have a postal address, and a tap searches the map for that address. The names and addresses are read on the phone as you type and are not stored or sent anywhere.
- `WAKE_LOCK`: keeping the phone awake during navigation and downloads.
- `androidx.car.app.NAVIGATION_TEMPLATES`, `androidx.car.app.ACCESS_SURFACE`: showing the map on a car's screen through Android Auto, when you connect to one.

Your position is used on the phone. It is not sent to the developer or to the map servers; searches are computed on the phone from the downloaded maps, and so are routes, unless you have agreed to online routes (below).

## Who else receives anything

- **The CoMaps map servers.** To list, download and update maps the app contacts the CoMaps servers (`cdn-us-1.comaps.app` for the list of mirrors, then one of the mirrors the CoMaps project and its volunteers run). As with any download they see your internet address and which region files you fetch. Settings lets you name a different map server instead.
- **The route server, only if you said yes to online routes.** When the map a route needs is not on your phone, Atlas asks once whether it may get the route online. After a yes, each such route sends the start (usually where you are), the destination, any stops, whether it is by car, on foot or by bicycle, and what you chose to avoid, to the routing server of FOSSGIS e.V. (`valhalla1.openstreetmap.de`), a German association whose volunteers run it. A new route is asked for when you leave the line. The server sees your internet address and the name of the app, and keeps requests in its server log; FOSSGIS does not say for how long. Nothing else is sent, and nothing at all for a route inside your downloaded maps. Settings, Navigation has the switch to turn online routes off again, and a field for the address of a route server of your own, which then receives the same instead.
- **The streamed map server, only if you said yes to showing the map online.** Where no map is on your phone, Atlas asks once whether it may show the map online. After a yes, the pieces of map under the screen are fetched as you look at them from OpenFreeMap (`tiles.openfreemap.org`), along with the lettering for the names. The server therefore sees which area you look at, and while you travel that follows where you are; your position itself, your searches and your routes are not sent to it. It sees your internet address and the name of the app. OpenFreeMap says it keeps no internet addresses in its access logs, keeps error logs with the address for seven days, and is reached through Cloudflare. Pieces already fetched are kept on the phone for a while so they need not be fetched again. Nothing is fetched for an area whose map is on your phone. Settings, Navigation has the switch to turn the streamed map off again, a switch that keeps it to Wi-Fi, and a field for the address of a map server of your own, which then receives the same instead.
- **The address servers, only if you said yes to looking an address up online.** Under an address typed into Search, Atlas offers a row "Look up … online". Nothing is sent while you type or before you press it; the first press asks. After a yes, each press sends the address as you typed it: to the geocoder of the US Census Bureau (`geocoding.geo.census.gov`) when your phone or the map is in the United States, and to Photon (`photon.komoot.io`, run by Komoot, from OpenStreetMap data) anywhere else and when the first finds nothing. The server sees the address, your internet address and the name of the app; your position is not sent. What comes back is kept until the app closes, so the same address is not asked for twice. Settings, Navigation has the switch to turn the lookup off again and a field for the address of a Photon server of your own, which then receives the addresses instead.
- **Google**, only on a phone that has Google Play services (or microG) and only while the "Google Fused Location Service" switch in Settings, Privacy is on. Android's fused location service then helps find your position and may contact Google. With the switch off, or on a phone without those services, only the phone's own positioning is used.
- **Your browser, phone and mail apps.** A website, phone number, email address or Wikipedia link on a place's page opens in the app that handles it; what that site or service learns is between you and it.

<!-- shared-about privacy section -->
## Who we are

Atlas is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Atlas keeps what you put in it on your phone, apart from what it sends to the CoMaps map servers and, if you agreed to online routes, the route server, as described above, and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Atlas is sold or shared.

Since Atlas keeps what it stores only on your phone, you can exercise most of these rights yourself: delete favorites, tracks and downloaded maps in the app, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.

Atlas connects to the CoMaps map servers and, if you agreed to them, to the FOSSGIS route server, the OpenFreeMap map server, and the address servers of the US Census Bureau and Komoot (Photon). They are separate from this app and from its developer, and what they receive is covered by their own privacy policies; ask them about what they hold.
<!-- /shared-about privacy section -->

## Changes

Changes to this policy are made in this file, and its history in the repository shows each one.
