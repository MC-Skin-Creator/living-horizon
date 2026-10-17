# Living Horizon — Journal des modifications

## Prochaine version

### Nouveautés
- **Joueurs déconnectés** — ils vivent leur vie autour de l'endroit où ils sont partis, à l'échelle d'un village : ils se promènent, s'assoient, regardent les coffres et les blocs de métier, passent les portes pour aller au lit la nuit, et lèvent un bouclier face aux monstres en vue et aux coups ; ils restent au sol : cassez le bloc sous eux, ils tombent ; enterrez-les, ils remontent.
- **Noms hors ligne** — les joueurs déconnectés affichent leur nom marqué *(hors ligne)*, et en viser un de près indique quand il a été vu pour la dernière fois ; des réglages masquent leurs noms, ou les joueurs eux-mêmes.
- **Oiseaux en 3D** — chaque oiseau est maintenant un petit modèle, comme les mobs du jeu, qui bat des ailes et se pose sur ses pattes ; au loin, il devient une image légère, comme les créatures lointaines quand les imposteurs sont activés. *Style des oiseaux* dans les réglages remet les oiseaux plats en 2D.
- **Hmm…** — j'ai cru voir quelque chose dans le ciel. 😉

### Améliorations
- **Créatures lointaines** — les slimes, les cubes de magma et les lapins se déplacent maintenant par bonds au lieu de glisser, le slime s'écrasant en retombant, et les poules battent des ailes en descendant d'un bloc.
- **Canards** — quand vous approchez, ils s'envolent ensemble et se reposent plus loin sur l'eau au lieu de partir pour de bon.

### Corrections
- **Longue-vue** — les créatures lointaines vues à la longue-vue sont mesurées à leur taille zoomée : *Taille animation* correspond maintenant à ce qu'on voit, et elles continuent de marcher quand elles paraissent grosses.
- **Ouverture d'un monde** — les créatures lointaines marchent et broutent dès le début, au lieu de seulement tourner sur elles-mêmes.
- **Oiseaux posés** — les mésanges, rouges-gorges et canards ne font plus de va-et-vient, ne se posent plus dans le vide ou dans les feuilles, ne nagent plus sur la terre ferme, et les canards n'apparaissent plus juste à côté de vous pour s'envoler aussitôt ; les canards nagent maintenant tranquillement sur leur mare au lieu de sauter en boucle, et les rouges-gorges et mésanges sautillent plus et s'envolent moins.

## 0.6.0 — 2026-10-15

### Nouveautés
- **Imposteurs** — les images des créatures sont enregistrées sur le disque et réutilisées à la partie suivante : elles ne sont redessinées qu'après un changement de packs de ressources, de mods ou de version du jeu.

### Améliorations
- **Icône du mod** — une nouvelle icône en pixel art : Steve au premier plan, et des créatures, des villageois, des joueurs et des oiseaux bien vivants jusqu'aux montagnes derrière lui.
- **En solo** — les créatures lointaines se téléportent ou foncent bien moins souvent vers un autre endroit quand tu reviens : elles suivent désormais les vraies créatures tant que le jeu les fait bouger, et disparaissent quand celles-ci meurent.

### Corrections
- **Écran des imposteurs** — deux silhouettes du même nom, comme des villageois aux tenues différentes, n'échangent plus leur place en clignotant.
- **Imposteurs** — les fins traits sombres et les pixels noirs qui apparaissaient parfois en haut ou en bas des créatures lointaines ont disparu, et les créatures longues comme les chevaux et les renifleurs ne sont plus coupées au museau ou à la queue.
- **Créatures GeckoLib sur Minecraft 1.21.2 à 1.21.8** — un monde ne plante plus quelques secondes après son chargement quand une créature d'un mod GeckoLib a été croisée au loin ; ces créatures ne sont pas encore montrées au loin.
- **Imposteurs** — le jeu ne ralentit plus avec les imposteurs activés : les mêmes créatures ne sont plus redessinées en boucle dans de nouvelles images, et les créatures qui se ressemblent partagent une seule image, gardée d'un monde à l'autre.

## 0.5.0 — 2026-10-13

### Nouveautés
- **Imposteurs sur Minecraft 1.20.1 à 1.21.8** — les joueurs et créatures lointains sont dessinés en images légères d'eux-mêmes sur ces versions aussi, et le Gestionnaire d'imposteurs les montre.

### Corrections
- **Forge 1.20.1 à 1.20.4** — le jeu ne plante plus au démarrage avec le mod installé.
- **Écran des imposteurs sur Minecraft 1.21.8 et avant** — l'ouvrir ne fait plus planter le jeu.
- **En rejoignant un monde** — les créatures déjà autour de toi à ton arrivée sont elles aussi dessinées au loin, et plus seulement celles rencontrées ensuite.

## 0.4.2 — 2026-10-12

### Corrections
- **Créatures qui s'éloignent** — une créature ne disparaît plus pendant quelques images avant que sa copie lointaine n'apparaisse.
- **Créatures qui s'éloignent** — la copie lointaine regarde maintenant dans la même direction que la créature, au lieu de parfois se tourner ailleurs.
- **Minecraft 1.21.9 à 26.1** — des morceaux de créatures lointaines, comme les vêtements d'un villageois ou un objet tenu en main, ne traversent plus le terrain de Voxy avec les shaders, ni celui de Distant Horizons.
- **Minecraft 26.1 et plus** — les parties transparentes des créatures lointaines, comme l'enveloppe d'un slime, ne traversent plus le terrain de Voxy avec les shaders, ni celui de Distant Horizons.

## 0.4.1 — 2026-10-11

### Corrections
- **Shaders** — les silhouettes lointaines affichées en imposteurs ne sont plus sombres avec les shaders d'Iris comme Photon ou Complementary : elles prennent le soleil comme les vrais modèles.
- **Photon avec Voxy** — les créatures lointaines ne portent plus l'ombre du terrain situé derrière elles, et l'eau de Voxy reste telle quelle.
- **Shaders sur Minecraft 26.2 et plus** — les créatures lointaines se cachent derrière les collines de Voxy et de Distant Horizons avec un pack de shaders, anciennes versions d'Iris comprises, comme sans.
- **Minecraft 26.1** — les créatures lointaines se cachent derrière les collines de Voxy et de Distant Horizons, comme sur les autres versions.
- **Voxy 0.2.18** — le mod lit de nouveau le terrain de Voxy : les créatures lointaines s'y tiennent et se cachent derrière ses collines.
- **Distant Horizons sur Minecraft 26.3 avec Iris installé** — les créatures lointaines se cachent de nouveau derrière les collines de Distant Horizons.
- **Voxy sur Minecraft 26.3 avec un pack de shaders** — les créatures lointaines se cachent derrière les collines de Voxy.
- **Distant Horizons avec la résolution réduite de Photon** — les créatures lointaines se cachent derrière les bonnes collines, et non derrière des collines décalées à l'écran.

## 0.4.0 — 2026-10-10

### Nouveautés
- **Premier scan automatique** — la première fois que tu entres dans un monde solo, les mobs des 32 chunks autour de toi sont lus tout de suite, pour que l'horizon soit vivant dès le départ. La distance du scan est maintenant donnée en chunks, comme la distance d'affichage, et les autres distances indiquent des *blocs*.
- **Réglages d'animation** — deux nouveaux curseurs choisissent quand les mobs lointains arrêtent leurs animations : sous une certaine taille à l'écran, ou au-delà d'une distance.
- **Préréglages de qualité** — Bas, Normal, Élevé et Ultra règlent d'un coup la portée des imposteurs, le nombre et la portée des créatures lointaines et les oiseaux, pour les machines puissantes qui veulent un horizon plus fourni. Les imposteurs ont leur propre catégorie, et *Rétablir les valeurs par défaut* est passé à côté de *Terminé*.

### Corrections
- **Créatures lointaines noires** — sans shaders, les créatures lointaines ne deviennent plus noires par moments, surtout en vol.

## 0.3.2 — 2026-10-09

### Corrections
- **Photon** — avec Voxy et l'upscaling de Photon (TAAU), les créatures lointaines se cachent de nouveau derrière les bonnes collines.

## 0.3.1 — 2026-10-08

### Corrections
- **Minecraft 1.20 à 1.21.4** — les créatures lointaines se cachent désormais derrière les collines de Voxy et de Distant Horizons, comme sur les versions récentes.
- **Anciennes versions de Distant Horizons** — fonctionne avec Distant Horizons 2.3 et plus, pas seulement la dernière version.

## 0.3.0 — 2026-10-07

### Nouveautés
- **Vulkan** — avec le moteur Vulkan du jeu, les créatures lointaines se cachent aussi derrière les collines, Distant Horizons compris.

### Corrections
- **Distant Horizons sans shaders** — les créatures lointaines ne disparaissent plus dans le terrain.
- **Distant Horizons** — plus de traînées là où s'arrête la distance d'affichage normale.

## 0.2.0 — 2026-10-07

### Nouveautés
- **Créatures lointaines** — animaux, villageois et golems restent vivants à l'horizon, au-delà de la distance d'affichage, à brouter et à se promener.
- **Joueurs lointains** — les autres joueurs continuent de marcher au loin, avec leur skin, leur armure et leur monture. Les joueurs déconnectés restent endormis là où ils sont partis.
- **Oiseaux** — nuées, oies en V, goélands sur la côte, chauves-souris la nuit.
- **Compatibilité Distant Horizons** — fonctionne avec Distant Horizons comme avec Voxy.
- **Cachées derrière les collines** — les créatures lointaines derrière une colline ne sont pas dessinées, pour garder le jeu fluide.
- **Imposteurs** (désactivés par défaut) — très loin, créatures et joueurs sont dessinés comme des images plates, bien plus légères quand ils sont nombreux.
- **Scanner les chunks autour** — en solo, les créatures des chunks autour de toi apparaissent tout de suite à l'horizon.
- **Réglages** — un bouton « Living Horizon... » dans le menu Options, avec le monde visible derrière, et un bouton pour revenir aux valeurs par défaut.
- **Quilt, NeoForge et Forge** — le mod fonctionne aussi sur Quilt, NeoForge et Forge, de Minecraft 1.20 à 26.3.
- **Un logo** dans la liste des mods et celle des data packs.
