# Sobre

Lecteur audio des abonnements YouTube, 100 % texte. Sobre lit **uniquement l'audio**
des vidéos publiées par les chaînes que vous ajoutez vous-même : zéro image, zéro
miniature, zéro recommandation, zéro Shorts, zéro défilement infini. Un outil
anti-brainrot : trois onglets (Flux, Chaînes, Téléchargements), lecture en arrière-plan,
import/export OPML, vidéos courtes (< 5 min) filtrées. Les flux viennent des RSS Atom
de YouTube ; l'audio est résolu par NewPipeExtractor.

*English: a text-only, audio-only player for your YouTube subscriptions — no
thumbnails, no algorithmic feed, no video. Add channels by URL or OPML, listen in the
background, download for offline.*

## Build

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, WorkManager, OkHttp,
NewPipeExtractor (GPL-3.0). Android 8.0+ (minSdk 26).

```
./gradlew assembleDebug
```

## Install

- **F-Droid :** ajouter le dépôt `https://funkypitt.github.io/fdroid-repo/repo`
  et chercher « Sobre »
- **APK :** dernière version sur <https://funkypitt.github.io/fdroid-repo/repo/>
- Partagez une URL de chaîne YouTube vers Sobre pour vous y abonner.

## Crédits / Credits

© 2026 Pierre Gallaz. Développé avec [Claude Code](https://claude.com/claude-code) (Anthropic).
Licence GPL-3.0-only, voir `LICENSE`.

© 2026 Pierre Gallaz. Developed with [Claude Code](https://claude.com/claude-code) (Anthropic).
GPL-3.0-only licence, see `LICENSE`.
