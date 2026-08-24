# autocommit — faire du switch de branche un vrai interrupteur

## Contexte

Repo : `/Users/mlo/git/autocommit` (Kotlin + GraalVM native-image), branche `watcher-resilience`.

autocommit surveille les repos listés dans `~/autocommit-config.yaml` et committe chaque
changement, uniquement quand le repo est sur la branche configurée (`main`).

Sur une **autre** branche, il ne committe pas — mais il exécute quand même `git add --all` à
chaque événement fichier, parce que `GitUtils.saveAndUpdate()` appelle `diffFiles()` juste
pour construire son message de warning :

```kotlin
} else {
    val diff = diffFiles(repositoryConfig)   // <-- fait un `git add --all`
    if (diff.isNotEmpty()) {
        logger.warn { "... is not on '...' branch (but '...'). Changed files: ..." }
    }
}
```

Conséquence : sortir de `main` n'arrête pas l'outil, il continue de réindexer en boucle. Si on
prépare un commit partiel à la main, l'index est écrasé. Le seul état vraiment inerte
aujourd'hui est le HEAD détaché, qui sort avant toute écriture.

## Tâche

Faire que le chemin « mauvaise branche » n'écrive **rien** : ni index, ni working tree.

- Lister les fichiers modifiés avec une commande en lecture seule — `git status --porcelain`
  convient et remonte en plus les fichiers non suivis (`??`), que `add --all` masquait en `A`.
- Garder le warning, avec la même intention (nom du repo coloré, branche attendue, branche
  courante, liste des fichiers). Le format des entrées change légèrement
  (`M  file` au lieu de `M\tfile`) : acceptable, ou normalise-le.
- **Ne pas toucher** à `diffFiles()` telle qu'utilisée par `save()` : là, le `git add --all`
  est voulu, c'est ce qui produit le commit. Ajoute une fonction séparée plutôt que de
  modifier celle qui existe.

## Contraintes

- `./gradlew compileKotlin` doit passer.
- Formater avec `./ktfmt` (ktfmt 0.49, `--dropbox-style`). Il reformate au passage un espace
  double sans rapport dans `Configuration.kt` : écarter ce fichier du commit.
- Un seul commit cohérent, message en anglais à l'impératif, comme les précédents.

## Vérification

Ne pas tester sur les vrais repos de `~/autocommit-config.yaml`. Recette avec un repo jetable,
en lançant le `main` en JVM avec un `user.home` bidon :

```sh
S=/tmp/ac-test && rm -rf $S && mkdir -p $S/home
git init -q --bare $S/origin.git
git init -q -b main $S/repo
cd $S/repo && git config user.email t@e.com && git config user.name T
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
java -Duser.home=$S/home -cp "$CP" net.mlorber.autocommit.MainKt watch > $S/app.log 2>&1 &
```

Attendus, en touchant `$S/repo/note.md` puis en regardant `$S/app.log` :

| État du repo de test              | Attendu                                                                 |
| --------------------------------- | ----------------------------------------------------------------------- |
| sur `main`                        | `Commit testrepo ...`, et `git log` avance                               |
| `git checkout -b autre`           | warning listant les fichiers, **et `git diff --cached` reste vide**      |
| `git checkout --detach`           | warning HEAD détaché, aucune commande d'écriture                         |
| retour sur `main`                 | les commits reprennent                                                   |

Le point qui compte est la colonne « `git diff --cached` reste vide » : c'est la régression
qu'on corrige. Tuer le JVM et `rm -rf /tmp/ac-test` à la fin.

## Déploiement (à faire seulement si demandé)

Le launchd agent exécute `~/work/autocommit/autocommit`, un binaire natif qui date d'août 2025
— aucun des correctifs de cette branche n'est en production. Le binaire ne peut pas être
écrasé à chaud, donc stop / copie / start :

```sh
./gradlew nativeCompile
launchctl bootout gui/$(id -u)/autocommit
cp build/native/nativeCompile/autocommit ~/work/autocommit/autocommit
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/net.mlorber.autocommit.plist
launchctl print gui/$(id -u)/autocommit | head -5   # attendu : state = running
tail -f ~/work/autocommit/autocommit.log
```

Le label launchd est `autocommit`, pas le nom du fichier plist. `KeepAlive` est actif : un
`kill` ne suffit pas à l'arrêter, il faut `bootout`.

## Pour plus tard, hors périmètre

`WatchCommand` ne surveille pas la santé de ses watchers. Un thread qui sort de sa boucle —
`key.reset()` invalide parce qu'un dossier surveillé a disparu, ce qui fait `break` — laisse un
repo non surveillé sans que le process ne le signale. Une vérification `thread.isAlive` dans la
boucle principale, avec relance ou log d'erreur, fermerait le dernier trou de ce genre.
