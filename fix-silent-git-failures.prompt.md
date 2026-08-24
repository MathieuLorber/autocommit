# autocommit — remonter les échecs git au lieu de les avaler

## Contexte

Repo : `/Users/mlo/git/autocommit` (Kotlin + GraalVM native-image), branche `watcher-resilience`.

`ShellRunner.CommandResult` porte `result` (code retour) et `errorOutput` (stderr). Ni l'un ni
l'autre n'est jamais exploité : `grep -rn '\.result' src/` ne remonte rien, et `errorOutput` est
déclaré dans la data class puis jamais lu. Tout finit en DEBUG et disparaît.

Ce que ça cache, relevé dans `~/work/autocommit/autocommit.log` — **327 codes retour non nuls,
zéro remontée** :

| Occurrences | Commande | Erreur |
| ----------- | -------- | ------ |
| **310** | `git pull --rebase` (pap) | `error: cannot rebase: You have unstaged changes.` |
| 13 | `git pull --rebase` (pap) | code 128, dont `fatal: Cannot rebase onto multiple branches.` |
| 6 + 1 | `git pull --rebase` (obsidian-test) | codes 1 et 128 |
| 2 | `git branch --show-current` | code 69 — licence Xcode non acceptée |
| 1 | `git push` | `Could not read from remote repository`, `Connection reset by ... port 22` |

Trois problèmes distincts derrière ces chiffres.

**1. Les échecs sont invisibles.** Un `push` qui casse sur réseau ou credentials, un
`pull --rebase` qui refuse de démarrer : le cycle continue comme si tout allait bien. Pour un
outil dont le rôle est de ne rien perdre, des commits qui n'atteignent jamais le remote sont
exactement ce qu'on veut voir signalé.

**2. Une race dans `save()` → `pull()`.** Le cycle fait `git add --all`, `git commit`, puis
`git pull --rebase` en commande séparée. Entre le commit et le pull, Obsidian réécrit un `.md`
suivi, donc le rebase refuse de démarrer : c'est l'origine des 310. Environ 8 % des cycles. Et
comme rien n'est vérifié, `push()` s'exécute juste après sur un état non rebasé.

**3. `currentBranch()` confond deux causes.** Elle renvoie `null` aussi bien pour un HEAD
détaché que pour un git en échec (codes 69 et 128, stdout vide). Le warning annonce alors
« detached HEAD (rebase or merge in progress?) » alors que le vrai problème est une licence Xcode
ou un dépôt cassé — il envoie sur une fausse piste. Les 2 codes 69 sont d'ailleurs la preuve que
le crash historique (`.first()` sur liste vide, corrigé en `6688812`) n'était pas spécifique au
rebase : n'importe quelle défaillance de git tuait le watcher, d'où les 5 stacktraces identiques
de `autocommit-error.log`.

## Tâche

### 1. Faire remonter les échecs

Une commande qui sort en code non nul doit être signalée avec son stderr, au niveau WARN, et de
façon exploitable : quelle commande, quel dépôt, quel message.

- **Le code retour décide, pas la présence de stderr.** git écrit son avancement normal sur
  stderr : le log de prod contient 2345 `Everything up-to-date` et 1657 `To github.com:...` qui
  sont des succès. Ne jamais traiter « stderr non vide » comme une erreur.
- **Certains échecs sont attendus** et ne doivent produire aucun bruit : `git check-ignore` sort
  en 1 quand aucun chemin n'est ignoré, et `git rev-list --count @{u}..HEAD` échoue quand il n'y
  a pas d'upstream (voir `fix-event-amplification.prompt.md`). Prévoir de pouvoir lancer une
  commande en tolérant explicitement l'échec, plutôt que de traiter tout non-zéro pareil.
- **Ne pas inonder le log.** La même erreur s'est répétée 310 fois : ne pas produire 310 lignes
  WARN. Une remontée quand l'état change (première occurrence, puis retour à la normale), pas une
  par cycle.

### 2. Ne pas pull/push sur un arbre redevenu sale

Après le commit, vérifier que l'arbre est propre avant `pull --rebase`. S'il ne l'est pas, la
bonne réponse est de **sauter le pull et le push pour ce cycle** : une écriture vient d'arriver,
donc un autre cycle suit de toute façon.

- **Ne pas utiliser `git pull --rebase --autostash`** : sur conflit, le rebase laisse le contenu
  dans une stash et l'arbre ne montre plus rien. Sur un vault de notes, une modification devenue
  invisible dans une stash est le pire des résultats. Sauter le cycle est sans risque.
- `save()` doit remonter s'il a committé ou non, ce dont le point 2 de
  `fix-event-amplification.prompt.md` a également besoin.

### 3. Séparer « HEAD détaché » de « git en échec »

- Code 0 + stdout vide = HEAD détaché → le warning actuel est correct.
- Code non nul = git en échec → warning distinct, portant la commande et le stderr, et aucune
  écriture pour ce cycle.

## Contraintes

- Pas de nouvelle dépendance, pas de coroutines : un `Thread` par dépôt, compatible native-image.
- `fix-branch-switch.prompt.md` et `fix-event-amplification.prompt.md` (même répertoire)
  modifient aussi `saveAndUpdate`. S'ils ont déjà été appliqués, composer avec plutôt que les
  défaire ; sinon, ne pas empiéter sur leur périmètre.
- Ne pas casser les comportements documentés dans `README.md` : cycle au démarrage, HEAD détaché
  = on ne touche à rien, branche autre que celle configurée = warning sans commit, et une erreur
  pendant un cycle est loggée sans tuer le thread.
- `./gradlew compileKotlin` doit passer. Formater avec `./ktfmt` (ktfmt 0.49, `--dropbox-style`),
  et écarter du commit les fichiers qu'il reformate sans rapport avec le sujet.
- Un commit par correctif, message en anglais à l'impératif, comme les précédents.

## Vérification

Ne pas tester sur les vrais repos de `~/autocommit-config.yaml`. Recette avec un repo jetable,
en lançant le `main` en JVM avec un `user.home` bidon :

```sh
S=/tmp/ac-fail && rm -rf $S && mkdir -p $S/home
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

Les trois échecs se provoquent de façon déterministe :

**La race du point 2**, avec un hook qui salit l'arbre juste après le commit — c'est exactement
la séquence des 310 occurrences :

```sh
printf '#!/bin/sh\necho race >> note.md\n' > $S/repo/.git/hooks/post-commit
chmod +x $S/repo/.git/hooks/post-commit
echo x >> $S/repo/note.md
# attendu : le commit passe, PAS de `cannot rebase: You have unstaged changes`,
#           et le cycle suivant committe la ligne ajoutée par le hook
rm $S/repo/.git/hooks/post-commit
```

**Un git en échec** (le cas des codes 69 / 128), en cassant `HEAD` puis en le restaurant :

```sh
mv $S/repo/.git/HEAD $S/repo/.git/HEAD.bak && echo x >> $S/repo/note.md
# attendu : un WARN « git en échec » portant la commande et le stderr,
#           PAS le message « detached HEAD », et le thread reste vivant
mv $S/repo/.git/HEAD.bak $S/repo/.git/HEAD
```

**Un push cassé**, en pointant `origin` dans le vide :

```sh
git -C $S/repo remote set-url origin /tmp/does-not-exist.git && echo x >> $S/repo/note.md
# attendu : le commit local a bien lieu, un WARN signale l'échec du push,
#           et le WARN ne se répète pas à chaque cycle suivant
git -C $S/repo remote set-url origin $S/origin.git
```

Attendus d'ensemble :

| Situation                          | Attendu                                                        |
| ---------------------------------- | -------------------------------------------------------------- |
| cycle normal                       | inchangé : `Commit testrepo ...`, aucun WARN                    |
| arbre sali après le commit          | commit fait, pull/push sautés, aucune erreur de rebase          |
| `git` en échec                      | WARN distinct avec la commande et le stderr, thread vivant      |
| push impossible                     | WARN une fois, pas à chaque cycle                               |
| retour à la normale                 | les cycles repartent, et ça se voit dans le log                 |
| `git checkout --detach` + écriture  | message « detached HEAD », inchangé                             |

Tuer le JVM et `rm -rf /tmp/ac-fail` à la fin.

Mettre ensuite à jour `README.md` : ce qui est signalé, à quel niveau, et le fait qu'un cycle est
sauté quand l'arbre est sali entre le commit et le pull.

## Déploiement (à faire seulement si demandé)

Le launchd agent exécute `~/work/autocommit/autocommit`, un binaire natif **daté du 24 août
2025** — aucun correctif de cette branche n'est en production. Il ne peut pas être écrasé à
chaud, donc stop / copie / start :

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

- **Deux machines écrivent dans les mêmes dépôts** : sur les 400 derniers commits de
  `~/git-pap/pap`, 216 `[mbp m4]` et 184 `[mb air m4]`, chacune committant, pullant et poussant
  en continu. Aucun `rejected` dans le log actuel, donc ça se rattrape en pratique — mais un pull
  qui échoue en silence sur une machine pendant que l'autre pousse est le scénario qui finit en
  divergence non résolue. Une fois les échecs remontés (point 1), on saura si ça arrive
  réellement avant d'imaginer une parade.
- **Une notification quand ça bloque.** Un warning dans un log de 11 Mo que personne ne lit ne
  vaut pas beaucoup mieux que le silence. Un signal visible (notification macOS, ou un fichier
  d'état que la barre de menu peut lire) quand un dépôt n'a pas réussi à pousser depuis N
  minutes.
