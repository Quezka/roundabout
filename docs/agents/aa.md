# AA receiver (native, no HUR)

## goal
tablet = AA head unit ; no Headunit Reloaded. reverse-engineer + implement the protocol in Kotlin.

## transport
- USB: AOAP (Android Open Accessory) ; `UsbManager` host ; phone as accessory
- WiFi: BT SDP discovery (uuid `4de17a00-52cb-11e6-bdf4-0800200c9a66`) -> RFCOMM WPP handshake -> phone joins HU AP -> TCP + TLS -> normal AA framing
- WPP framing != AA framing: `[2B len BE][2B type BE][protobuf]`

## TLS
- mutual TLS 1.2 ; phone = TLS **server**, HU = TLS **client** (roles reversed)
- phone cert = `O=CarService`, signed by `Google Automotive Link CA` (root, 2014-2044)
- HU verifies phone chain against GAL CA ; bad key -> `AUTH_COMPLETE` status `-3`
- need GAL CA cert in the app (extract from opencardev/aasdk or openauto)

## framing  (done -> `aa/AaFraming.kt`)
- `[1B channel][1B flags][2B payload-len BE][payload]`
- flags: `0x01 FIRST` `0x02 LAST` `0x08 ENCRYPTED` ; frame <= `0x4000` ; payload <= `0x3FFC`
- logical message = FIRST..LAST frames

## control channel (ch 0) msg ids
```
1 VERSION_REQUEST  HU->phone  raw 4B
2 VERSION_RESPONSE phone->HU  raw 6B
3 SSL_HANDSHAKE    both
4 AUTH_COMPLETE    HU->phone  proto(status)
5 SERVICE_DISCOVERY_REQUEST   phone->HU
6 SERVICE_DISCOVERY_RESPONSE  HU->phone
7 CHANNEL_OPEN_REQUEST        phone->HU (on target ch)
8 CHANNEL_OPEN_RESPONSE       HU->phone
0xb PING_REQUEST / 0xc PING_RESPONSE
0xd NAV_FOCUS_REQ / 0xe RESP
0xf SHUTDOWN_REQ / 0x10 RESP
0x11 VOICE_SESSION_REQ
0x12 AUDIO_FOCUS_REQ / 0x13 RESP
```

## service channels (open after discovery)
- `MEDIA_AUDIO` PCM sink ; `SYSTEM_AUDIO`; `SPEECH_AUDIO` ; `AUDIO_IN` mic
- `VIDEO` H.264 -> `MediaCodec` ; input: touch/keys
- `SENSOR` gps/speed/night ; `BLUETOOTH` ; `NAVIGATION` status

## android mapping
- USB `UsbManager` ; TCP `Socket` ; TLS `SSLEngine` / `SSLSocket`
- video `MediaCodec` (avc) ; audio out `AudioTrack` (pcm 16) ; mic `AudioRecord`
- proto: `opencardev/aasdk` (254 proto) or `mrmees/open-android-auto` (248) -> protobuf-lite codegen

## phone side
- install Android Auto ; enable "Head unit server" @ dev settings (wireless)
- phone must be unlocked + AA running

## refs
- `github.com/opencardev/aasdk` + `opencardev/openauto` (C++ reference)
- `github.com/mrmees/open-android-auto` (proto surface)
- `github.com/mretallack/AndroidAuto` (Kotlin/Java architecture writeup)

## roadmap
1. framing + tests  [done]
2. control state machine: version -> tls -> auth -> svc-discovery -> channel-open
3. transport: USB AOAP ; then WiFi (WPP + TCP/TLS)
4. TLS with GAL CA (mutual, HU=client)
5. proto-lite codegen
6. channels: video/audio/input/sensor
7. render AA video fullscreen in roundabout + forward touch

## risk
- GAL CA cert required -> without it, auth fails ; get from aasdk repo
- phone-side gating: AA must allow the HU (head-unit server mode)
- 1080p60 H.264 on Intel SoFIA = maybe slow -> start 480p/30
