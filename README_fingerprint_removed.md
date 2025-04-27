### Fingerprint (Chromaprint/fpcalc) removal

Audio fingerprinting and native Chromaprint/fpcalc-android dependencies were removed. The search algorithm now uses only ISRC and text/SBERT similarity. This significantly reduces APK size, removes all native builds, and simplifies project setup. If bestScore < 75, no match is returned. See SearchEngine.kt for details.
