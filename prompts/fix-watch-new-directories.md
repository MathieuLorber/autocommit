# Fix : les nouveaux répertoires ne sont pas surveillés par le watcher

## Contexte

`autocommit watch` surveille des repos git et committe/push automatiquement à chaque
modification, via un `java.nio.file.WatchService`
(`src/main/kotlin/net/mlorber/autocommit/thread/WatchThread.kt`).

## Bug constaté

Les répertoires ne sont enregistrés auprès du `WatchService` **qu'une seule fois, au
démarrage du process** (`WatchThread.register()` est appelé récursivement dans le
constructeur, puis plus jamais).

Conséquence : tout répertoire créé (ou déplacé dans le repo) **après** le démarrage du
process n'est jamais enregistré. Les modifications de fichiers à l'intérieur ne génèrent
aucun event, donc aucun commit.

Symptôme observé en pratique : un « train de retard ». Les modifs dans le nouveau dossier
restent unstaged, et ne sont committées que lorsqu'un event survient ailleurs dans un
dossier surveillé (ex : Obsidian qui écrit `.obsidian/workspace.json` au changement de
note) — le `git add --all` du cycle suivant ramasse alors la modif précédente.

## Fix attendu

Dans la boucle d'events de `WatchThread` :

1. Quand un event `ENTRY_CREATE` arrive, résoudre le chemin complet de l'entrée créée
   (`dir.resolve(event.context())` — attention, `event.context()` est relatif au
   répertoire associé à la `WatchKey`, il faut donc garder une map `WatchKey -> Path`
   puisque l'API ne fournit pas le répertoire directement).
2. Si c'est un répertoire, appeler `register()` dessus — **récursivement**, pour couvrir
   un `mkdir -p`, un déplacement/copie d'une arborescence entière, ou des fichiers créés
   dans le dossier avant son enregistrement.
3. Continuer à exclure `.git` (le check actuel `event.context().toString() == ".git"` ne
   couvre que la racine ; avec la map `WatchKey -> Path` on peut exclure proprement tout
   chemin contenant un segment `.git`).
4. Après l'enregistrement d'un nouveau répertoire, déclencher un `saveAndUpdate()` : des
   fichiers ont pu être écrits dans le dossier entre sa création et son enregistrement,
   et ils n'émettront jamais d'event.

## Points d'attention

- macOS : le `WatchService` de la JVM est un `PollingWatchService` (pas d'implémentation
  native FSEvents). Les events arrivent avec quelques secondes de latence et peuvent être
  coalescés — ne pas supposer qu'on reçoit un event par fichier créé, d'où l'importance de
  l'enregistrement récursif + `saveAndUpdate()` au point 4.
- Ne pas casser le comportement existant : exclusion de `.git`, gestion de `OVERFLOW`,
  arrêt propre via `running` / `interrupt()`.
- Style : le projet utilise ktfmt (`./ktfmt`).

## Validation

- Test manuel : lancer `watch` sur un repo de test, créer `mkdir -p a/b/c`, écrire un
  fichier dans `a/b/c/`, vérifier qu'un commit part sans avoir à toucher un fichier
  ailleurs. Répéter en copiant une arborescence complète (`cp -r`).
- Vérifier qu'une modif dans `.git` (ex : `git fetch` manuel) ne déclenche pas de cycle.
