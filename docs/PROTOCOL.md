# Yellowbrick v3 Bluetooth protocol

Interoperability specification for the Yellowbrick v3 (MkII) satellite tracker's
Bluetooth messaging interface, firmware 02.07.02. This document is the sole
input for the protocol code in this repository. It records facts established by
interoperability analysis of the discontinued vendor Android app and by
observation of a real device; it contains no vendor code.

Conventions: byte offsets are zero-based; multi-byte integers are big-endian;
`u16` is an unsigned 16-bit integer; hex dumps are lower-case without spaces.

## 1. Transport

- Bluetooth Classic, RFCOMM channel 1. The device advertises SPP (`0x1101`) and
  Apple iAP in SDP, but on Android the SDP route and insecure channel-1 sockets
  fail: the device's Bluetooth module (Roving Networks RN-41) initiates Secure
  Simple Pairing (General Bonding, no MITM) on every link, and an insecure
  Android socket cannot complete it. The connection that works is a **secure
  channel-1 socket**, which pairs on first use. Android exposes this only
  through the hidden `BluetoothDevice.createRfcommSocket(int)`.
- Clients should try, in order: SDP (`createInsecureRfcommSocketToServiceRecord`),
  insecure channel 1, secure channel 1, and remember which rung connected.
- The module stores effectively **one** host link key. Pairing from a second
  host evicts the first; the evicted host then fails every connect with a
  socket read error (`read ret: -1`) until it forgets the bond and re-pairs.
  Recovery: when all rungs fail while the phone holds a bond, remove the bond
  (hidden `BluetoothDevice.removeBond()`), wait ~1.5 s for the stack to settle,
  and redial; the system pairing dialog appears.
- Link keys survive device Bluetooth toggles and full power cycles.
- The device sometimes discards the **first frame after connect** (observed
  2 of 5 sessions). Send a frame whose reply is optional first (STATUS_REQUEST).
- The device is a serial stream: frames may arrive split or coalesced across
  reads; decode incrementally.

## 2. Framing

```
offset  size  field
0       1     SOF = 0x01
1       2     len   (u16) — counts from the len field through the end of body
3       6     header
9       1     type
10      n     body
10+n    2     sum   (u16) — 16-bit sum of all bytes from len through body
12+n    1     EOT = 0x04
```

- `len = 2 + 6 + 1 + n`. Minimum valid `len` is 9.
- Outbound header = 4-byte ASCII **keyword** + `00 00`.
- Inbound header is an opaque 6 bytes; ignore it.
- Decoder: scan to `0x01`, read `len`, wait for `1 + len + 3` bytes, verify sum,
  emit, continue. If `len < 9`, skip the SOF byte and rescan.

## 3. Credentials

Two 4-character ASCII secrets issued per device by the service:

- **keyword** — in every outbound header, and repeated in the TEXT_OUT body.
- **password** — only in the TEXT_OUT body.

They are validated by the device/back end, never locally. A wrong pair produces
a TEXT_IN whose body is ≤ 4 bytes (see §4.7).

## 4. Message types

| dir | type | name           | body                                                   |
|-----|------|----------------|--------------------------------------------------------|
| out | 0x01 | TEXT_OUT       | §4.1                                                   |
| out | 0x02 | REQUEST        | empty — hand over the next queued inbound message      |
| out | 0x04 | ACK            | `ybId u16` — device drops that inbound from its queue  |
| out | 0x05 | MAILBOX_CHECK  | empty — hotspot mode: poll the satellite mailbox       |
| out | 0x06 | STATUS_REQUEST | empty                                                  |
| in  | 0x80 | CONFIRMATION   | `ybId u16` — reply to one TEXT_OUT part                |
| in  | 0x81 | NO_TEXT        | empty — nothing queued                                 |
| in  | 0x82 | TEXT_IN        | §4.6                                                   |
| in  | 0x83 | STATUS         | §4.8                                                   |

Types 0x03, 0x07 and above outbound, and 0x84+ inbound, are **not** part of
this interface. Firmware analysis shows some of them delete records, write
files or start a firmware update. Never send them.

### 4.1 TEXT_OUT body

```
offset  size  field
0       2     ybId        u16, client-chosen id for this part (§5.2)
2       1     includePos  1 = attach GPS position, 0 = don't
3       1     sendNow     1 = request a transmission session now (§5.4)
4       1     totalParts  parts in this message (0 for a mode command, §4.2)
5       1     partNo      } present only when totalParts > 1
6       1     threadId    } 1-byte id shared by all parts of one message
+0      4     password    ASCII
+4      4     keyword     ASCII
+8      n     payload     ISO-8859-1, §4.3
```

### 4.2 Mode command

A TEXT_OUT with `totalParts = 0`, `includePos = 0`, and payload `S`
(stand-alone) or `H` (hotspot). The device does not reply.

- **Stand-alone**: the device polls the satellite mailbox on its own schedule
  (§4.8 byte 5) and holds inbound messages until the phone sends REQUEST.
- **Hotspot**: the phone drives mailbox polls with MAILBOX_CHECK. The vendor
  flow was: empty TEXT_OUT (`totalParts = 0`), wait 120 s, MAILBOX_CHECK,
  wait 30 s, REQUEST. Each poll costs airtime credits. This mode has not been
  exercised by this project.

Send the mode command on the first connection after credentials, mode or
device change.

### 4.3 Payload

```
recipient[,recipient][,g]:text
```

- Recipients are e-mail addresses or international phone numbers, lower-cased;
  `:` and `,` inside a recipient are replaced by `-`.
- `g` as a recipient means "my group" (service-side distribution list).
- The text follows the first `:`.
- **Length limits** apply to the whole payload including recipients:
  - ≤ 307 characters: one part.
  - otherwise: first part 305 characters, each further part 321 characters.
  Each part is a separate TEXT_OUT with its own `ybId`, the same `threadId`,
  `partNo` 1..N, `totalParts` N. Only part 1 carries `includePos = 1`.

### 4.4 Credit cost (vendor-published billing rule)

- Up to 50 payload characters = 1 credit; each further 50 = 1 more.
  Characters in e-mail addresses and phone numbers count.
- Plus 1 credit per SMS (phone-number) recipient.
- Group cost is not published. Costs are estimates; the device never reports
  a per-message cost.

### 4.5 CONFIRMATION semantics

One CONFIRMATION per TEXT_OUT part, carrying that part's `ybId`. Firmware
analysis shows it is emitted **before** the insertion result is known: a full
queue (500 shared records) or a storage error still produces a CONFIRMATION.
Therefore CONFIRMATION means "the device replied for this part", not "the
device stored it", and never "transmitted" or "delivered". No later
notification of transmission or delivery exists in this interface.

Deduplication on the device compares `ybId` + the 6-byte header. A resend with
the same `ybId` and different text is silently ignored (old text kept) and
still confirmed. Completed records are deleted and their ids become reusable
after device-side compaction.

### 4.6 TEXT_IN body

```
offset  size  field
0       2     ybId        u16, device-assigned; echo it in ACK
2       2     credit      u16 remaining balance; 0xFFFF = unknown
4       1     totalParts
5       1     partNo      } present only when totalParts > 1
6       1     threadId    }
+0      n     sender:text  ISO-8859-1 (part 1); continuation parts carry text only
```

- Single part: text region is `sender:text`.
- Multi-part, part 1: text region is `sender:text`; later parts: text only.
- Reassemble by `threadId`; parts may arrive in any order.
- The credit balance is the only balance source; it arrives **only** on
  TEXT_IN. Record the time it was observed.
- Always ACK after processing, then REQUEST again promptly: the device hands
  over one message per REQUEST.

### 4.7 Invalid credentials

A TEXT_IN whose body is ≤ 4 bytes (`ybId`, `credit`, no text) means the
keyword/password pair was rejected. ACK it, then stop the session.

### 4.8 STATUS body

```
byte  meaning                                            observed
0-2   firmware version major.minor.patch (decimal)       02 07 02
3     storage high-water flag: 1 = ≥ 490 of 500 records   00
4     tracking interval index                            05 = 30 min
5     inbox-check interval index                         00 = 5 min
```

Tracking index 0..14: continuous, 5 m, 10 m, 15 m, 20 m, 30 m, 1 h, 90 m,
2 h, 3 h, 4 h, 6 h, 8 h, 12 h, burst.
Inbox-check index 0..12: 5 m, 10 m, 15 m, 20 m, 30 m, 1 h, 90 m, 2 h, 3 h,
4 h, 6 h, 8 h, 12 h.

Both intervals are *configured* values. Whether tracking or inbox checking is
*enabled* is not reported. Byte 5 = 0 means "5 minutes", not "off". Record
storage is shared by inbound and outbound messages. Bodies shorter than 6
bytes: treat the missing fields as unknown.

Nothing in this interface reports battery, GPS fix, signal or queue length.

## 5. Session behaviour

### 5.1 Sequence

1. Connect (rung ladder, §1).
2. STATUS_REQUEST as primer (reply optional).
3. Mode command if credentials/mode/device changed since the last session.
4. Send queued outbound parts (§5.3).
5. Loop: REQUEST every 30 s; also STATUS_REQUEST until a STATUS has arrived.
6. On TEXT_IN: ACK, record credit, then REQUEST again after ~0.5 s.

### 5.2 Part identity

`ybId` is chosen by the client per part. Requirements that follow from §4.5:

- Persist each part's `ybId` with the message; never regenerate on reconnect.
- Never reuse a `ybId` that may still be pending on the device.
- Correlate a CONFIRMATION only against the exact `ybId` of a part that is
  awaiting one; ignore unknown ids.
- Only parts without a CONFIRMATION are retransmitted.

### 5.3 Pacing

- ≥ 1 s between any two outbound frames.
- ~3 s between the parts of one message, and after the last part.
- First reply after connect may take up to ~5 s; subsequent replies ~0.1 s.

### 5.4 `sendNow`

`sendNow = 1` asks the device to start a transmission session immediately.
`sendNow = 0` only omits that request; the part remains eligible for the next
scheduled session. It is not a hold/release mechanism.

## 6. Test vectors

Inbound frames captured from a device running 02.07.02:

| frame | hex |
|-------|-----|
| STATUS `02 07 02 00 05 00` | `01000f0000000000008302070200050000a204` |
| NO_TEXT | `01000900000000000081008a04` |

Outbound frames for placeholder keyword `abcd`, password `wxyz` (computed from
§2 and §4.1; the framing is deterministic):

| frame | hex |
|-------|-----|
| STATUS_REQUEST | `01000961626364000006019904` |
| REQUEST | `01000961626364000002019504` |
| ACK `0x1234` | `01000b61626364000004123401df04` |
| MAILBOX_CHECK | `01000961626364000005019804` |
| TEXT_OUT `a@b.c:hi`, ybId `0x1234`, pos 1, now 1, 1 part | `01001e6162636400000112340101017778797a616263646140622e633a686907fd04` |
| Mode `S`, ybId `0x0001` | `0100176162636400000100010001007778797a6162636453056304` |

## 7. Provenance

Established by interoperability analysis of the vendor's discontinued Android
app (information not otherwise available; no vendor code is reproduced here),
HCI tracing of the pairing behaviour (btmon, BlueZ host), live exchanges with
a Yellowbrick v3 MkII, and static/offline analysis of firmware 02.07.02 for
the STATUS layout and CONFIRMATION semantics. Where behaviour was inferred
rather than observed, the text says so.
