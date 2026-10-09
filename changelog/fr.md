# Living Horizon — Journal des modifications

## Prochaine version

### Nouveautés
- **Compatibilité Distant Horizons** — le mod fonctionne désormais avec Distant Horizons comme avec Voxy, et utilise celui qui est installé. Oiseaux, créatures et lieux lointains lisent son terrain ; les créatures lointaines sont cachées par ses collines pixel par pixel, avec ou sans pack de shaders.
- **Joueurs lointains** — les autres joueurs continuent de marcher là où ils sont, au-delà de la distance d'affichage du serveur, avec leur skin, leur armure et leur monture.
- **Créatures lointaines** — animaux, villageois et golems restent à l'horizon, à brouter et à se promener autour des villages que Voxy affiche.
- **Oiseaux** — nuées, oies en V, goélands sur la côte, chauves-souris la nuit.
- **Créatures plus vivantes** — les créatures lointaines jouent des promenades jusqu'à six blocs, choisies d'après les blocs autour d'elles, avec des pauses pour brouter et jeter des coups d'œil, au lieu de tourner en rond. L'herbe, les fleurs et les chemins creusés ne les arrêtent plus ; les barrières et les falaises, toujours. Elles ne se téléportent jamais : quand vous approchez, elles retournent à l'endroit où elles étaient, puis marchent jusqu'à la vraie créature avant que le jeu prenne le relais, et elles marchent vers leur nouvelle place quand elles se déplacent.
- **Mobs cachés par ce qui les cache** — un mob lointain entièrement derrière une colline, de Voxy ou de Distant Horizons compris, n'est plus dessiné du tout, ce qui fait beaucoup gagner avec beaucoup de mobs. Activé par défaut ; les réglages sont passés dans *Debug...*.
- **Joueurs déconnectés** — ils restent endormis là où ils sont partis, jusqu'à leur retour.
- **Réglages à côté du jeu** — les écrans de réglages passent à gauche, sans flou derrière, pour voir les changements sur le monde en direct.
- **Imposteurs** (désactivés par défaut) — assez loin, les joueurs et les créatures lointains sont dessinés comme une image plate d'eux-mêmes au lieu de leur modèle complet, ce qui coûte bien moins avec beaucoup d'entre eux. Les images suivent les skins et les packs de ressources ; un écran *Imposteurs...* dans les réglages les montre et les régénère.
- **Nombre de polygones sur F3** — l'écran de debug indique combien de polygones coûtent les joueurs et les créatures lointains.
- **Réglages dans le menu Options** — un bouton « Living Horizon... » à côté de Terminé, une nouvelle limite de distance pour les mobs et les joueurs lointains, et un nombre de mobs illimité.
- **Scanner les chunks autour** — la première fois que tu entres dans un monde solo, un scan de 32 chunks (comme une distance d'affichage de 32) se lance tout seul. Ensuite, en solo, un bouton dans les réglages (ou `/livinghorizon scan`) lit les mobs des chunks autour de toi directement dans le monde : ils apparaissent à l'horizon sans avoir à y aller d'abord.
- **Réglages réorganisés** — les joueurs lointains et les créatures lointaines s'activent maintenant séparément, le plan lointain passe dans *Debug...* et le contour lumineux devient ses *Contours...*, les imposteurs sont activés dès 128 blocs, les créatures sont limitées à 512 et 512 blocs par défaut (illimité sur les deux peut faire planter le jeu), les joueurs déconnectés sont assis, la soucoupe volante s'appelle « Easter egg », et le rayon de scan monte à 1024 chunks (la commande accepte n'importe quel rayon), et un bouton *Valeurs par défaut* remet tous les réglages à zéro.
- **Quilt, NeoForge et Forge** — le mod fonctionne désormais aussi sur Quilt (avec la Fabric API), NeoForge et Forge, en plus de Fabric, sur Minecraft 1.20 à 1.21.11 et 26.1 à 26.3 (NeoForge à partir de 1.20.6).
- **Un logo** — le mod et le data pack ont leur propre icône, dans la liste des mods et celle des data packs.
- **Contours** (*Debug...* > *Contours...*) — un contour visible à travers le relief autour de chaque type de silhouette, chacun avec son interrupteur, aux couleurs des boîtes de debug : blanc pour les mobs que dessine le jeu, cyan pour les vrais mobs dessinés par le mod, vert pour les copies 3D, bleu pour les joueurs, magenta pour les imposteurs, et une croix là où un mob lointain n'est pas dessiné du tout. Pratique pour des captures qui comparent ce que le mod dessine et ce qu'il économise.

### Corrections
- **Avant Minecraft 1.21.5** — les créatures lointaines sont elles aussi cachées derrière les collines de Distant Horizons et de Voxy, et la vue de la profondeur du debug n'est plus toute blanche.
- **Anciennes versions de Distant Horizons** — les créatures lointaines sont cachées derrière les collines de Distant Horizons et marchent sur son terrain dès la version 2.3, plus seulement avec la dernière.
