# autocommit — arrêter l'amplification d'événements

## Contexte

Repo : `/Users/mlo/git/autocommit` (Kotlin + GraalVM native-image), branche `watcher-resilience`.

autocommit part en roue libre sur `~/git-pap/pap`, un vault Obsidian. Obsidian réécrit en
continu `.obsidian/workspace.json`, qui est **ignoré par git**, et chaque écriture déclenche un
cycle complet : `git branch --show-current` → `add --all` → `diff --name-status --cached` →
`pull --rebase` → `push`. Relevé dans `~/work/autocommit/autocommit.log` : **4074 cycles pour
1691 commits**, avec des pics à 44 cycles/minute pour des cycles qui durent ~3 s (deux
opérations réseau). Le watcher ne rattrape jamais sa file d'événements : il sature un cœur à
forker `sh`/`git` et à parler au remote pour rien.

Trois causes cumulées.

**1. Un cycle par événement, pas par lot** — `Watcher.kt` :

```kotlin
key.pollEvents().forEach { event ->
    ...
    update(repositoryConfig)   // <-- un cycle complet PAR événement
}
```

Une rafale de 20 événements = 20 cycles = ~60 s de git, pendant lesquels les événements
continuent de s'empiler.

**2. `pull` et `push` inconditionnels** — `GitUtils.saveAndUpdate()` :

```kotlin
if (currentBranch == repositoryConfig.branch) {
    save(repositoryConfig)     // sort tôt si le diff est vide
    pull(repositoryConfig)     // <-- tourne quand même
    push(repositoryConfig)     // <-- idem
}
```

D'où l'écart 4074 / 1691 : ~2400 allers-retours réseau qui n'avaient rien à transporter.

**3. Le watcher ignore `.gitignore`** — un événement sur un fichier ignoré déclenche un cycle
comme les autres. Dans `~/git-pap/pap`, `git status --porcelain --ignored` remonte
`.DS_Store`, `CRIDF/.DS_Store` et `.obsidian/workspace.json` : c'est exactement ce qui alimente
la boucle.

## Tâche

### 1. Debounce dans `Watcher`

Drainer **tous** les événements de la clé, décider une seule fois s'il y a lieu d'agir, faire
**un** cycle, puis observer un délai de calme avant le suivant : consommer et jeter les
événements qui arrivent pendant ce délai, mais sans perdre le fait qu'il y a eu du changement —
un cycle doit toujours suivre la dernière écriture, jamais l'inverse.

- Délai en constante nommée, valeur 3 s.
- Utiliser `poll(timeout)` plutôt que `take()` pour la fenêtre de calme, sinon `stop()` n'est
  plus réactif : l'arrêt ne doit pas être retardé de plus du délai, et le thread doit sortir
  proprement sur `InterruptedException`.
- Garder les filtres actuels : entrée nommée `.git`, et `OVERFLOW`. Un `OVERFLOW` doit être
  traité comme « du changement, contenu inconnu » → un cycle, pas un `return@forEach` qui
  perdrait l'information.

### 2. `pull`/`push` seulement s'il y a du travail

Dans `saveAndUpdate`, n'appeler `pull`/`push` que si un commit vient d'être créé, ou si local et
remote divergent :

```
git rev-list --count @{u}..HEAD    # à pousser
git rev-list --count HEAD..@{u}    # à récupérer
```

Ces deux commandes échouent s'il n'y a pas d'upstream : tolérer le cas (pas de crash, pas de
warning à chaque cycle). `save()` doit remonter s'il a committé ou non plutôt que de laisser
l'appelant le redeviner.

### 3. Ne pas réagir aux fichiers ignorés

Filtrer les chemins du lot d'événements avec un **seul** `git check-ignore --stdin` pour tout
le lot. Si tous les chemins sont ignorés, aucun cycle du tout.

- `event.context()` est un nom relatif au répertoire surveillé : reconstruire le chemin avec
  `key.watchable() as Path`, sinon le filtrage porte sur le mauvais chemin.
- `git check-ignore` sort en **code 1** quand aucun chemin n'est ignoré : ce n'est pas une
  erreur. `ShellRunner` remonte le code, ne le confonds pas avec un échec.
- Un fichier non suivi mais non ignoré doit continuer de déclencher un cycle.

### 4. Enregistrer les répertoires créés après le démarrage

`register()` n'est appelé qu'à la construction : un sous-dossier créé ensuite n'est jamais
surveillé, et son contenu n'est vu que par ricochet. Enregistrer les nouveaux répertoires sur
les `ENTRY_CREATE`.

### 5. Les logs de commande en TRACE, et vérifier que `logback.xml` est bien embarqué

`src/main/resources/logback.xml` met `net.mlorber.autocommit` en INFO, or le log de production
est plein de DEBUG **avec le pattern par défaut de logback**
(`HH:mm:ss.SSS [thread] LEVEL logger -- msg`), pas celui du fichier : la configuration n'est pas
dans l'image native, donc logback tombe sur ses défauts (root DEBUG). Vérifier l'inclusion de la
ressource dans le bloc `graalvmNative` et la corriger si besoin.

En complément, passer les logs de commande de `ShellRunner` (`Run '...'`, `Command result`,
`Command output/error`) de DEBUG à TRACE : même en DEBUG volontaire, ils noient tout. Les
commits restent en INFO.

## Contraintes

- Pas de nouvelle dépendance, pas de coroutines : on reste sur un `Thread` par dépôt, compatible
  native-image.
- Ne pas casser les comportements documentés dans `README.md` : cycle au démarrage, HEAD détaché
  = on ne touche à rien, branche autre que celle configurée = warning sans commit, et une erreur
  pendant un cycle est loggée sans tuer le thread.
- `fix-branch-switch.prompt.md` (même répertoire) modifie aussi `saveAndUpdate`, sur la branche
  « mauvaise branche ». S'il a déjà été appliqué, composer avec plutôt que de le défaire ;
  sinon, ne pas empiéter sur son périmètre.
- `./gradlew compileKotlin` doit passer. Formater avec `./ktfmt` (ktfmt 0.49, `--dropbox-style`),
  et écarter du commit les fichiers qu'il reformate sans rapport avec le sujet.
- Un commit par correctif, message en anglais à l'impératif, comme les précédents.

## Vérification

Ne pas tester sur les vrais repos de `~/autocommit-config.yaml`. Recette avec un repo jetable,
en lançant le `main` en JVM avec un `user.home` bidon :

```sh
S=/tmp/ac-amp && rm -rf $S && mkdir -p $S/home
git init -q --bare $S/origin.git
git init -q -b main $S/repo
cd $S/repo && git config user.email t@e.com && git config user.name T
printf '.DS_Store\nignored/\n' > .gitignore
echo hello > note.md && git add -A && git commit -qm init
git remote add origin $S/origin.git && git push -qu origin main
cat > $S/home/autocommit-config.yaml <<EOF
commitMessagePrefix: '[test]'
repositories:
  - name: testrepo
    path: $S/repo
    branch: main
EOF

# classpath de run, sans modifier build.gradle.kts
cat > $S/init.gradle.kts <<'EOF'
allprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val ss = extensions.getByType(SourceSetContainer::class.java)
        tasks.register("printCp") {
            dependsOn("classes")
            doLast { println("CLASSPATH=" + ss["main"].runtimeClasspath.asPath) }
        }
    }
}
EOF
cd /Users/mlo/git/autocommit
CP=$(./gradlew --init-script $S/init.gradle.kts printCp -q | grep '^CLASSPATH=' | sed 's/^CLASSPATH=//')
java -Duser.home=$S/home -Dorg.slf4j.simpleLogger.defaultLogLevel=trace \
     -cp "$CP" net.mlorber.autocommit.MainKt watch > $S/app.log 2>&1 &
```

Mettre `net.mlorber.autocommit` en TRACE le temps de la recette (fichier logback ou
`-Dlogback.configurationFile=`), sinon les commandes git ne sont plus visibles dans `app.log` —
c'est justement l'objet du point 5.

Attendus, en comptant les occurrences de `git pull` / `git push` / `Commit testrepo` dans
`$S/app.log` :

| Action sur le repo de test                                  | Attendu                                                  |
| ----------------------------------------------------------- | -------------------------------------------------------- |
| `for i in $(seq 20); do echo $i >> note.md; sleep 0.1; done` | **un seul** cycle, **un seul** commit                     |
| `touch .DS_Store` puis `mkdir ignored && touch ignored/x`    | **aucun** git : ni `add`, ni `pull`, ni `push`             |
| `echo x >> note.md` (une écriture)                          | un commit, un `pull`, un `push`                           |
| rien du tout pendant 30 s                                   | **aucun** `pull`/`push` — plus de trafic à vide           |
| `mkdir sub && sleep 1 && echo y > sub/new.md`                | `sub/new.md` committé (point 4)                           |
| `git checkout -b autre` puis écriture                        | warning sans commit, comportement inchangé                |
| `git checkout --detach` puis écriture                        | warning HEAD détaché, aucune écriture                     |

Le point qui compte est la première ligne : c'est la régression qu'on corrige. Tuer le JVM et
`rm -rf /tmp/ac-amp` à la fin.

Mettre ensuite à jour la section de `README.md` qui décrit le cycle : debounce de 3 s, fichiers
ignorés sans effet, `pull`/`push` conditionnels.

## Déploiement (à faire seulement si demandé)

Le launchd agent exécute `~/work/autocommit/autocommit`, un binaire natif **daté du 24 août
2025** — aucun correctif de cette branche n'est en production, et c'est bien cet ancien binaire
qui a produit la boucle. Il ne peut pas être écrasé à chaud, donc stop / copie / start :

```sh
./gradlew nativeCompile
launchctl bootout gui/$(id -u)/autocommit
cp build/native/nativeCompile/autocommit ~/work/autocommit/autocommit
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/net.mlorber.autocommit.plist
launchctl print gui/$(id -u)/autocommit | head -5   # attendu : state = running
tail -f ~/work/autocommit/autocommit.log
```

Le label launchd est `autocommit`, pas le nom du fichier plist. `KeepAlive` est actif : un
`kill` ne suffit pas à l'arrêter, il faut `bootout`. `autocommit.log` fait 11 Mo et n'est jamais
tourné : `: > ~/work/autocommit/autocommit.log` avant de relancer, pour lire une trace propre.

## Deux constats du même diagnostic

**Les watchers meurent en silence.** `autocommit-error.log` contient 5
`Exception in thread "Thread-1"` — Thread-1 est le watcher de `pap`, le premier dépôt de la
config. La mort d'un thread ne fait pas sortir le process, donc `KeepAlive` ne relance rien :
`pap` est resté non surveillé jusqu'au redémarrage manuel suivant, sans que rien ne le signale.
Le catch-all de `0ae5dad` referme le cas « erreur pendant un cycle », mais pas le `break` sur
`key.reset()` invalide, ni une exception pendant `register()`. Une surveillance
`thread.isAlive` dans la boucle de `WatchCommand`, avec relance ou log d'erreur, est le
complément naturel de ce lot.

**Deux dépôts n'ont jamais été surveillés.** Sur tout l'historique du log :
3736 cycles dans `pap`, 196 dans `pap-lite`, 142 dans `obsidian-test`, et **zéro** dans
`pap-archive` et `pap-lite-archive`. C'est cohérent avec le binaire déployé — pas de cycle au
démarrage, et des dépôts d'archive ne produisent aucun événement fichier — donc `022774a` le
corrige déjà. À garder en tête en vérifiant le point 4 : le cycle de démarrage est la seule
chose qui regarde ces deux dépôts.

## Pour plus tard, hors périmètre

- Le commit `0ae5dad` (attraper tout `Throwable` pour garder le thread en vie) a supprimé un
  coupe-circuit involontaire : avant, une exception tuait le thread et arrêtait le spin jusqu'au
  redémarrage par `KeepAlive`. C'est le bon changement, mais il rend le debounce d'autant plus
  nécessaire — un dépôt en mauvais état réessaie désormais indéfiniment.
- Un plafond de fréquence par dépôt (au maximum un cycle réseau toutes les N secondes, quoi
  qu'il arrive) serait une seconde ligne de défense indépendante du debounce.
