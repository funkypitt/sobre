# Sobre

Lecteur audio des abonnements YouTube, 100 % texte. Sobre lit **uniquement l'audio**
des vidéos publiées par les chaînes que vous ajoutez vous-même : zéro image, zéro
miniature, zéro recommandation, zéro Shorts, zéro défilement infini. Un outil
anti-brainrot : trois onglets (Flux, Chaînes, Téléchargements), lecture en arrière-plan,
import/export OPML, vidéos courtes (< 5 min) filtrées. Les flux viennent des RSS Atom
de YouTube ; l'audio est résolu par NewPipeExtractor, et par yt-dlp quand celui-ci
ne trouve plus rien.

*English: a text-only, audio-only player for your YouTube subscriptions — no
thumbnails, no algorithmic feed, no video. Add channels by URL or OPML, listen in the
background, download for offline.*

## Build

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, WorkManager, OkHttp,
NewPipeExtractor (GPL-3.0), yt-dlp via youtubedl-android (GPL-3.0). Android 8.0+ (minSdk 26).

### Quand YouTube change

NewPipe est essayé d'abord : il est rapide et ne pèse rien. Mais YouTube change ses
signatures sans prévenir, et un NewPipe qui n'a pas été recompilé ne trouve plus rien —
l'app paraît cassée jusqu'à ce qu'une nouvelle version soit publiée. yt-dlp prend alors
le relais, pour la lecture comme pour le téléchargement, et **va chercher sa propre
nouvelle version sur le téléphone** : une fois par semaine, et tout de suite si YouTube
lui répond 403 (auquel cas l'essai est refait dans la foulée). Le secours se répare
donc tout seul. C'est ce qui fait passer l'APK de 12 à ~50 Mo : yt-dlp emporte son
Python, pour deux architectures (`arm64-v8a`, `x86_64`).

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
