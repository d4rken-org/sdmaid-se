# Security

## Verifying APK authenticity

You can verify a downloaded or installed APK against the fingerprints below using
[`apksigner`](https://developer.android.com/tools/apksigner) or
[AppVerifier](https://github.com/soupslurpr/AppVerifier):

```bash
apksigner verify --print-certs eu.darken.sdmse-*.apk
```

### FOSS build (GitHub Releases, Obtainium, IzzyOnDroid F-Droid)

```
SHA-256: F4:90:03:2B:E5:38:3F:55:90:04:95:FA:7F:C2:07:EC:E2:E7:86:A5:C2:C6:CC:52:02:7B:99:54:8A:9C:E9:38
SHA-1:   AC:0D:A1:8D:AB:55:FD:C5:B2:A0:99:DE:0E:BF:5C:06:94:D0:D8:54
MD5:     2D:E4:C0:20:30:9E:B9:FB:E2:05:09:9E:CD:CF:1F:8A
```

### Google Play

```
SHA-256: AF:E3:7F:E1:EF:44:C1:7D:66:1E:2A:B0:4C:77:EE:72:84:FF:95:40:D5:68:BA:9A:1D:F5:6F:5F:59:CE:AE:12
SHA-1:   56:57:BE:4C:D1:78:BB:CA:E6:28:0B:C7:8E:9B:2C:64:B8:08:AB:5D
MD5:     10:E2:67:9F:4F:A3:CB:39:15:27:BA:5C:7F:48:5B:FC
```

### F-Droid main repository

The F-Droid main repository re-signs APKs with the F-Droid signing key. Use the F-Droid
signing fingerprint to verify those installs.
