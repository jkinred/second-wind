package io.github.jkinred.secondwind.data

import io.github.jkinred.secondwind.proto.Payload

/**
 * Fictional conversations for screenshots. Hidden behind a seven-tap gesture on the About
 * screen; see [io.github.jkinred.secondwind.Messenger.loadDemo]. Settings (device, credentials)
 * are left as they are so the device sheet still describes the real Yellowbrick.
 */
object Demo {
    private const val H = 3_600_000L
    private const val D = 24 * H

    private class Builder(private val now: Long) {
        val messages = ArrayList<Message>()
        private var n = 0

        fun received(from: String, agoMs: Long, text: String, unread: Boolean = false, credit: Int? = null) {
            messages += Message(
                id = "demo-${++n}", direction = Direction.IN, at = now - agoMs, from = from,
                inParts = mapOf(1 to text), threadId = n and 0xFF, unread = unread, creditAfter = credit,
            )
        }

        fun sent(to: String, agoMs: Long, text: String) {
            val at = now - agoMs
            val parts = Payload.split(Payload.build(listOf(to), text, false)).indices.map { i ->
                OutPart(no = i + 1, ybId = 60_000 + n * 4 + i, sentAt = at + 20_000, acceptedAt = at + 45_000)
            }
            messages += Message(
                id = "demo-${++n}", direction = Direction.OUT, at = at, recipients = listOf(to),
                text = text, outParts = parts, threadId = n and 0xFF,
            )
        }
    }

    fun data(real: AppData, now: Long = System.currentTimeMillis()): AppData {
        val b = Builder(now)

        // --- Summit day: out early, up, and back to base camp -------------------------------
        val sarah = "+61412345678"
        b.sent(sarah, 3 * D + 22 * H, "Leaving high camp 03:40. Cold, no wind, stars out. Five of us on the rope. Next msg from the top or when we turn round.")
        b.received(sarah, 3 * D + 21 * H + 50 * 60_000, "Go safe. Watching the dot. Kids say climb fast. x")
        b.sent(sarah, 3 * D + 17 * H + 20 * 60_000, "SUMMIT 08:12. All five. Clear to the horizon, can see the coast. 10 min then down.")
        b.received(sarah, 3 * D + 17 * H, "YES!!! So proud of you. Crying in the kitchen. Now get down carefully please.")
        b.sent(sarah, 3 * D + 9 * H, "Back at high camp. Legs gone, everyone OK. Brew on. Base camp tomorrow.")
        b.sent(sarah, 2 * D + 6 * H, "Base camp. Tea, biscuits, Pete got the chess out. Walk out starts Thu, phone signal Sat. Love you.")
        b.received(sarah, 2 * D + 5 * H + 40 * 60_000, "Welcome back to earth. Photos the second you have signal. Bath is already planned.", credit = 38)

        // --- Companion with a broken leg: practical traffic with the rescue co-ordinator ----
        val rcc = "+61280001234"
        b.sent(rcc, 1 * D + 7 * H, "Tom Harker fell on moraine above Hooker Hut. L lower leg broken, obvious deformity. Splinted, conscious, warm. Cannot walk. Pos -43.7182 170.1075. Weather clear, light N wind. Pls advise.")
        b.received(rcc, 1 * D + 6 * H + 48 * 60_000, "RCC Wellington. Received. Confirm party size, phone nos, and any other injuries. Keep him warm, do not move unless in danger. Reply when done.")
        b.sent(rcc, 1 * D + 6 * H + 35 * 60_000, "Party 3. Tom 44, me (Jess 39), Ana 41. No other injuries. Tom in bivvy bag + 2 jackets, pain 7/10. Shelter rock 50 m SW. Mobile 0427 555 019 when in signal.")
        b.received(rcc, 1 * D + 6 * H + 10 * 60_000, "Heli tasked from Mt Cook. ETA 14:30 local weather permitting. Lay out bright gear in open ground, stay clear of rotor wash. Reply every 30 min or on change.")
        b.sent(rcc, 1 * D + 5 * H + 30 * 60_000, "Copy. Orange fly laid out on flat ground 80 m E of us. Tom stable, dozing. Cloud building on the divide but clear here.")
        b.received(rcc, 1 * D + 4 * H + 55 * 60_000, "Heli airborne 14:05. 20 min. Stay put.")
        b.sent(rcc, 1 * D + 4 * H + 20 * 60_000, "Heli has him. Thank you. Ana and I walking out to Hooker Hut tonight, White Horse Hill car park tomorrow.")
        b.received(rcc, 1 * D + 4 * H + 12 * 60_000, "Noted. Tom en route to Timaru Hospital. Please confirm when you reach the car park. Well done.", credit = 31)

        // --- Goodbyes: snowed in above the col, out of gas --------------------------------
        val anna = "anna.k@example.com"
        b.sent(anna, 10 * D + 20 * H, "Still at 6400. Third day in the tent. Gas finished last night so no water. Fingers going. Storm not moving. Being honest with you: not sure I get down from this.")
        b.received(anna, 10 * D + 19 * H + 20 * 60_000, "I'm here. I'm not going anywhere. Rescue have your position and are waiting for a window. Keep talking to me.")
        b.sent(anna, 10 * D + 11 * H, "Tell Finn the chess set is his and he already beats me. Tell Mae I kept her drawing in the lid pocket the whole way. They are the best thing I did.")
        b.received(anna, 10 * D + 10 * H + 30 * 60_000, "I will. They know. They love you. Forecast says Thursday morning could open up. Hold on for Thursday.")
        b.sent(anna, 9 * D + 23 * H, "I love you Anna. You were right about everything and I would still have come. I'm not scared. Sleep now.")
        b.received(anna, 9 * D + 22 * H, "Then sleep. I'll be here when you wake up. Thursday. I love you.")

        // --- Singletons for the list ---------------------------------------------------------
        b.received("harbourmaster@example.com", 5 * H, "Lyttelton Harbour: berth F14 held for you until 1800 Sat. Call ch 12 on approach.", unread = true, credit = 46)
        b.sent("+447700900123", 9 * H, "Landed safe at Punta Arenas, all good. Walk-in starts tomorrow, next msg from the hut. Love you Mum.")
        b.received("routing@example.com", 16 * H, "Front passes 0300Z. Expect SW 35 gusting 45 for 6 h then easing. Suggest heave-to or run off NE until 0900Z.", unread = true)
        b.sent("+64211234567", 2 * D + 2 * H, "Position 1800: 42 11S 174 55E. Boat speed 6.8, wind 18 kn SE. Everyone eating and sleeping. Tracker on.")
        b.received("+61400111222", 4 * D + 3 * H, "Credit top-up done, 50 credits added. Account renews 1 Nov. Dad.")
        b.sent("mike.r@example.com", 6 * D + 1 * H, "At the hut, both roos in the bag, back at the vehicle Sunday arvo. Beers on me.")
        b.received("+447700900456", 8 * D + 5 * H, "Weather station on the col shows -22C overnight. Pack the extra fuel.")

        val contacts = listOf(
            Contact(sarah, "Sarah", pinned = true),
            Contact(rcc, "RCC Wellington"),
            Contact(anna, "Anna", pinned = true),
            Contact("harbourmaster@example.com", "Lyttelton Harbourmaster"),
            Contact("+447700900123", "Mum", pinned = true),
            Contact("routing@example.com", "Weather router"),
            Contact("+64211234567", "Race control"),
            Contact("+61400111222", "Dad", pinned = true),
            Contact("mike.r@example.com", "Mike"),
            Contact("+447700900456", "Base camp"),
        )

        return real.copy(
            messages = b.messages,
            contacts = contacts,
            credit = 46, creditAt = now - 5 * H,
            lastConnectedAt = now - 20 * 60_000, lastConnectRung = "sdp", lastPullAt = now - 20 * 60_000,
            device = DeviceInfo(firmware = "02.07.02", storageNearlyFull = false, trackingInterval = "15 min", inboxCheckInterval = "1 h", at = now - 20 * 60_000),
        )
    }
}
