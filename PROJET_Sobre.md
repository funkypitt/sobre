# Sobre — lecteur audio des abonnements YouTube, 100 % texte

> Spécification de projet destinée à **Claude Code**.
> Nom de travail : **Sobre** (à renommer librement). Package : `app.example.sobre` (à changer).

---

## 1. Intention du projet

Une application Android **open-source** qui permet d'écouter **uniquement l'audio** des vidéos publiées par les chaînes auxquelles on s'abonne. L'objectif est explicitement **anti-brainrot** : supprimer tout ce qui capte l'attention de façon passive — la vidéo, les miniatures, les recommandations algorithmiques, les Shorts, les commentaires, le défilement infini.

Le principe directeur, non négociable, qui prime sur toute autre considération esthétique :

- **ZÉRO image.** Aucune miniature, aucun avatar de chaîne, aucune icône bitmap, **aucune icône dans la navigation**. L'interface est 100 % texte (labels, titres, descriptions). La seule iconographie tolérée : de simples glyphes/caractères Unicode ou des contrôles de lecture dessinés en vecteur minimal (lecture/pause/avance). En cas de doute, on n'affiche **rien**.
- **Audio seulement.** La vidéo n'est jamais une option. Le code ne lit jamais les flux vidéo, ne demande jamais de surface de rendu vidéo. On ne « masque » pas la vidéo : elle n'existe pas dans l'app.
- **Aucun feed algorithmique.** Pas de recommandations, pas de tendances, pas de recherche YouTube globale. La seule source de contenu est la liste des chaînes que l'utilisateur a explicitement ajoutées.

Si une décision d'implémentation entre en conflit avec ces trois principes, ce sont les principes qui gagnent.

---

## 2. Pile technique

| Domaine | Choix | Notes |
|---|---|---|
| Langage | **Kotlin** | |
| UI | **Jetpack Compose** (Material 3) | Texte uniquement, contrastes élevés |
| Extraction YouTube | **NewPipeExtractor** | GPLv3 — voir §9 |
| Lecture audio | **Media3 / ExoPlayer** + `MediaSessionService` | Lecture en arrière-plan, écran verrouillé, Bluetooth |
| Persistance | **Room** | Chaînes + épisodes + état de téléchargement |
| Tâches de fond | **WorkManager** | Rafraîchissement périodique des flux RSS |
| Réseau | **OkHttp** | Downloader pour NewPipeExtractor + téléchargements |
| Asynchrone | **Coroutines + Flow** | |
| minSdk | **26** | desugaring requis (voir §9) |
| targetSdk | dernier stable | |

---

## 3. Architecture

MVVM + repository pattern, une seule activité (`MainActivity`) hébergeant la navigation Compose.

```
app/
├── data/
│   ├── db/                 # Room : entités, DAO, database
│   ├── rss/                # parsing du flux Atom YouTube
│   ├── extractor/          # wrapper NewPipeExtractor (init, résolution audio, chapitres)
│   ├── download/           # téléchargement et stockage local des fichiers audio
│   └── repository/         # SubscriptionRepository, EpisodeRepository
├── playback/
│   ├── PlaybackService.kt  # MediaSessionService (Media3)
│   └── PlayerController.kt # façade exposant l'état du lecteur en Flow
├── ui/
│   ├── channels/           # onglet 1 + écran détail chaîne
│   ├── feed/               # onglet 2
│   ├── downloads/          # onglet 3
│   ├── episode/            # vue épisode (titre, description, lecteur, chapitres)
│   ├── addchannel/         # ajout par collage d'URL
│   └── theme/              # thème texte, support e-ink
├── work/                   # RefreshFeedsWorker
└── MainActivity.kt
```

---

## 4. Modèle de données (Room)

```kotlin
@Entity
data class Channel(
    @PrimaryKey val channelId: String,   // ex. "UCxxxxxxxx"
    val title: String,
    val rssUrl: String,                  // feeds/videos.xml?channel_id=...
    val lastEpisodeAt: Long,             // timestamp du plus récent épisode connu → tri onglet Chaînes
    val addedAt: Long
)

@Entity(
    foreignKeys = [...],                 // channelId → Channel
    indices = [Index("channelId"), Index("publishedAt")]
)
data class Episode(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val channelTitle: String,            // dénormalisé pour l'onglet Flux
    val title: String,
    val description: String,             // remplie complètement à l'ouverture (voir §7.4)
    val publishedAt: Long,
    val durationSec: Long? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val lastPositionMs: Long = 0         // reprise de lecture
)
```

Les **chapitres** ne sont pas stockés : ils sont résolus à la volée via NewPipeExtractor au moment d'ouvrir l'épisode (voir §7.4).

---

## 5. Import de chaînes — exigence centrale

Deux points d'entrée, qui aboutissent au même pipeline :

### 5.1 Collage d'une adresse
Écran « Ajouter une chaîne » avec un champ texte + bouton « Ajouter ». L'utilisateur colle n'importe quelle URL YouTube.

### 5.2 Partage depuis l'app YouTube
`MainActivity` déclare un intent-filter pour recevoir du texte partagé :

```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
</intent-filter>
```

Au lancement via `ACTION_SEND`, on extrait l'URL du `Intent.EXTRA_TEXT` et on la passe au même pipeline d'import, en affichant une confirmation (« S'abonner à *Nom de la chaîne* ? »).

### 5.3 Pipeline de résolution (le cœur)
L'URL partagée peut être une **chaîne** OU une **vidéo**. Les deux doivent fonctionner.

1. Détecter le type d'URL par regex :
   - Chaîne : `/channel/UC...`, `/@handle`, `/c/Nom`, `/user/Nom`
   - Vidéo : `youtu.be/ID`, `/watch?v=ID`, `m.youtube.com/...`
2. **Si vidéo** → résoudre via `StreamInfo.getInfo(...)` puis récupérer `getUploaderUrl()` pour obtenir la chaîne. (UX clé : partager une vidéo qu'on aime → s'abonner à sa chaîne.)
3. **Résoudre l'URL de chaîne en `channelId` canonique** : les `@handle`, `/c/`, `/user/` ne sont pas directement utilisables par le flux RSS. Utiliser `ChannelInfo.getInfo(ServiceList.YouTube, url)` qui renvoie l'identifiant canonique `UC...`. (Fallback éventuel : récupérer le HTML de la page et parser `externalId`/`channelId`, mais NewPipeExtractor doit suffire.)
4. Construire le flux RSS : `https://www.youtube.com/feeds/videos.xml?channel_id=<UC...>`
5. Insérer le `Channel` en base, puis déclencher un premier rafraîchissement RSS pour peupler ses épisodes.
6. Gérer les doublons (chaîne déjà abonnée → message, pas d'erreur).

---

## 6. Rafraîchissement des épisodes (RSS + fond)

Approche **podcast-like**, robuste et légère :

- Source de fraîcheur : le **flux Atom** YouTube de chaque chaîne (`videos.xml?channel_id=...`). Il contient les ~15 dernières vidéos avec `yt:videoId`, `title`, `published`, et `media:group/media:description`.
- Parsing avec `XmlPullParser` (pas de dépendance lourde). Champs à extraire par `<entry>` : videoId, title, published, description.
- Un `RefreshFeedsWorker` (WorkManager, périodique ~ toutes les 2-6 h + déclenchable manuellement par pull-to-refresh) parcourt toutes les chaînes, insère les nouveaux épisodes (upsert sur `videoId`), et met à jour `Channel.lastEpisodeAt`.
- Pour la **liste complète** d'une chaîne (au-delà des 15 du RSS), prévoir une pagination optionnelle via `ChannelTabInfo` de NewPipeExtractor, chargée à la demande dans l'écran détail chaîne. Le RSS reste la source par défaut.

---

## 7. Écrans

Navigation : **TabRow Compose en haut avec trois onglets texte** — `Chaînes` · `Flux` · `Téléchargés`. (Pas de bottom navigation avec icônes : les icônes sont des images.)

### 7.1 Onglet « Chaînes »
- Liste des chaînes triées par `lastEpisodeAt` **décroissant** (la chaîne ayant publié le plus récemment en haut).
- Chaque ligne : **titre de la chaîne** + date relative du dernier épisode, en texte. Rien d'autre.
- Appui sur une chaîne → **écran détail chaîne** : liste de ses épisodes, plus récent en haut (titre + date). Appui sur un épisode → vue épisode (§7.4).
- Appui long sur une chaîne → menu texte : « Se désabonner ».

### 7.2 Onglet « Flux »
- Tous les épisodes, **toutes chaînes confondues**, triés par `publishedAt` décroissant.
- Chaque ligne : **titre de l'épisode** + **nom de la chaîne** + date relative. Texte uniquement.
- Pull-to-refresh → déclenche `RefreshFeedsWorker`.

### 7.3 Onglet « Téléchargés »
- Épisodes où `isDownloaded == true`, lisibles hors-ligne.
- Même rendu texte. Appui long → « Supprimer le téléchargement ».

### 7.4 Vue épisode
Affichée à l'ouverture d'un épisode. **100 % texte, zéro image.** Contenu :

1. **Titre** complet.
2. **Nom de la chaîne** + date de publication.
3. **Lecteur audio** : barre de progression, position/durée, lecture/pause, avance/recul ±15 s, vitesse de lecture (0.75×–2×), bouton **Télécharger**. Contrôles vectoriels minimalistes ou glyphes — pas de pochette/artwork.
4. **Description complète** (texte brut, liens cliquables autorisés).
5. **Chapitres** s'ils existent : liste texte `mm:ss — Titre du chapitre`, chaque ligne cliquable pour sauter à la position.

Résolution à l'ouverture :
- Appeler `StreamInfo.getInfo(ServiceList.YouTube, videoUrl)` (en IO).
- Description complète ← `getDescription().getContent()` (met à jour `Episode.description`).
- Chapitres ← `getStreamSegments()` : chaque `StreamSegment` a un titre et `getStartTimeSeconds()`.
- Fallback chapitres (optionnel) : si `streamSegments` est vide, parser les timestamps du texte de description (regex `(\d{1,2}:)?\d{1,2}:\d{2}`).
- Audio ← choisir un `AudioStream` dans `getAudioStreams()` (privilégier m4a/itag 140 ou opus ; choisir le débit le plus élevé raisonnable). **Ne jamais** toucher `getVideoStreams()` / `getVideoOnlyStreams()`.

⚠️ Les URL de flux YouTube **expirent** (quelques heures) et sont liées à l'extraction : il faut résoudre l'audio **juste avant de lire** (ou juste avant de télécharger), jamais le stocker durablement.

---

## 8. Lecture et téléchargement

### 8.1 Lecture (Media3)
- `PlaybackService : MediaSessionService` avec un `ExoPlayer`.
- `MediaItem.fromUri(audioStreamUrl)` (les flux audio progressifs m4a/opus sont lus directement ; ExoPlayer gère aussi le DASH si besoin).
- Notification de lecture **sans artwork** (titre + chaîne en texte), boutons lecture/pause/avance.
- Gestion du focus audio, reprise depuis `lastPositionMs`, sauvegarde régulière de la position.
- `PlayerController` expose l'état (position, lecture en cours, épisode courant) en `StateFlow` pour l'UI.

### 8.2 Téléchargement
- Bouton « Télécharger » dans la vue épisode → résout l'`AudioStream` puis télécharge le fichier (OkHttp) vers le stockage interne de l'app (`filesDir/audio/<videoId>.<ext>`).
- À la fin : `isDownloaded = true`, `localFilePath` renseigné.
- En lecture, si `isDownloaded`, lire le fichier local plutôt que de ré-extraire.
- Suppression → effacer le fichier + remettre les champs à zéro.

---

## 9. Pièges et contraintes connues

- **NewPipeExtractor exige une initialisation** : `NewPipe.init(downloader)` avec une implémentation de `Downloader` (wrapper OkHttp). À faire une fois au démarrage de l'app. S'inspirer du `DownloaderImpl` de l'app NewPipe.
- **Desugaring** : pour minSdk < 33, activer `isCoreLibraryDesugaringEnabled = true` et ajouter `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:...")`. Sans cela, NewPipeExtractor plante à l'exécution.
- **ProGuard/R8** : conserver les classes Mozilla Rhino requises par l'extracteur :
  ```
  -keep class org.mozilla.javascript.** { *; }
  -keep class org.mozilla.classfile.ClassFileWriter
  -dontwarn org.mozilla.javascript.tools.**
  ```
- **Licence** : NewPipeExtractor est **GPLv3**. L'app entière hérite donc du copyleft. Parfait pour un usage personnel et une distribution **F-Droid** ; **incompatible Play Store**. Publier le code source.
- **Fragilité de l'extraction** : YouTube casse périodiquement l'extraction. Garder NewPipeExtractor en **dépendance versionnée** (JitPack ou snapshots Maven Central) pour pouvoir bumper la version facilement quand l'upstream corrige. Ne pas vendoriser le code.
- **Robustesse réseau** : gérer proprement les erreurs d'extraction (vidéo supprimée, privée, géo-bloquée, live en cours) avec des messages texte clairs, sans crasher la liste.

---

## 10. Considérations e-ink

(Cible secondaire : tablettes/liseuses e-ink Android.)

- Thème **noir sur blanc**, contraste maximal, pas de gris subtils.
- **Désactiver les animations** Compose (transitions, ripples animés, défilement fluide) — préférer des changements d'état nets pour éviter le ghosting.
- Cibles tactiles larges (≥ 48 dp), typographie nette et grande.
- Pas de rafraîchissements continus (pas de spinners animés ; utiliser un état texte « Mise à jour… »).

---

## 11. Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Media3)
- `POST_NOTIFICATIONS` (Android 13+)
- Stockage interne uniquement → pas de permission de stockage externe nécessaire.

---

## 12. Ordre de construction suggéré (jalons)

1. **Squelette** : projet Gradle, dépendances, thème texte, `MainActivity` + TabRow à 3 onglets vides.
2. **NewPipeExtractor** : init + `Downloader` OkHttp ; fonction `resolveChannelId(url)` testée sur les 4 formats d'URL + URL de vidéo.
3. **Room** : entités, DAO, repositories.
4. **Import** : écran collage d'URL + intent-filter de partage → pipeline §5 → chaîne en base.
5. **RSS** : parser Atom + `RefreshFeedsWorker` + pull-to-refresh ; remplir les onglets Chaînes et Flux.
6. **Vue épisode** : résolution `StreamInfo`, description complète, chapitres texte.
7. **Lecture** : `PlaybackService` Media3, notification sans image, reprise de position, vitesse.
8. **Téléchargements** : téléchargement audio local + onglet Téléchargés + lecture hors-ligne.
9. **Finitions e-ink** + gestion d'erreurs + désabonnement/suppression.

---

## 13. Critères d'acceptation (rappel des invariants)

- [ ] Aucune image n'est jamais chargée nulle part (ni miniature, ni avatar, ni icône bitmap, ni artwork de notification).
- [ ] Aucun flux vidéo n'est jamais demandé ni lu.
- [ ] On peut s'abonner en collant une URL **ou** en partageant depuis l'app YouTube, qu'il s'agisse d'une URL de chaîne ou de vidéo.
- [ ] Onglet Chaînes trié par activité récente décroissante ; détail chaîne = liste d'épisodes.
- [ ] Onglet Flux = tous épisodes, plus récent en haut.
- [ ] Onglet Téléchargés = épisodes hors-ligne.
- [ ] Vue épisode = titre + description complète + lecteur audio + chapitres, en texte intégral.
- [ ] Lecture en arrière-plan, écran verrouillé, contrôles Bluetooth fonctionnels.
