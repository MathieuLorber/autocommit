# autocommit — diagnostic de l'emballement du 18 août 2026

Relevés faits le 2026-08-24 sur `~/work/autocommit/autocommit.log` (11,9 Mo, jamais tourné,
donc il couvre plusieurs mois et plusieurs redémarrages) et `autocommit-error.log`.

## Ce qui tourne n'est pas ce qui est dans le repo

`~/work/autocommit/autocommit` est daté du **24 août 2025**. Aucun commit de la branche
`watcher-resilience` n'y est — ni le fix HEAD détaché, ni le catch-all, ni le cycle au
démarrage. Deux preuves indépendantes :

- `autocommit-error.log`, dernière stack : `NoSuchElementException` dans
  `GitUtils.currentBranch` sur `first()`, alors que le source actuel utilise `firstOrNull()`.
- Le log est en DEBUG **avec le pattern par défaut de logback**
  (`HH:mm:ss.SSS [thread] LEVEL logger -- msg`), pas celui de
  `src/main/resources/logback.xml` : la configuration n'est pas embarquée dans l'image native,
  donc logback tombe sur ses défauts (root DEBUG).

Conséquence de méthode : l'emballement observé n'est pas imputable au code de la branche
courante. Rien n'a été cassé récemment, le problème est structurel et ancien.

## L'emballement

Obsidian réécrit en continu `.obsidian/workspace.json` dans `~/git-pap/pap`. Ce fichier est
ignoré par git (`git status --porcelain --ignored` remonte aussi `.DS_Store` et
`CRIDF/.DS_Store`), mais le watcher ne consulte pas `.gitignore` : chaque écriture déclenche un
cycle complet `git branch --show-current` → `add --all` → `diff --cached` → `pull --rebase` →
`push`, soit ~3 s dont deux opérations réseau.

Trois amplificateurs se cumulent :

1. `Watcher.kt` lance un cycle **par événement** (`key.pollEvents().forEach { … update() }`),
   pas par lot. 20 événements = 20 cycles = ~60 s de git, pendant lesquels les événements
   continuent de s'empiler.
2. `GitUtils.saveAndUpdate` appelle `pull()` et `push()` **même quand `save()` n'a rien
   committé**.
3. Aucun filtrage `.gitignore`, donc le bruit d'un fichier ignoré coûte autant qu'une vraie
   modification.

Chiffres sur l'historique du log :

| Mesure                                    | Valeur                                     |
| ----------------------------------------- | ------------------------------------------ |
| cycles totaux                             | 4074                                       |
| commits produits                          | 1691                                       |
| cycles sans rien à committer              | ~2400, chacun avec un `pull` + un `push`   |
| pic observé                               | 44 cycles dans la minute de 15:29          |
| durée d'un cycle                          | ~3 s                                       |

Le pic dit tout : 44 cycles en 60 s pour des cycles de 3 s, le watcher est saturé en permanence
et ne rattrape jamais sa file.

## Répartition par dépôt

| Dépôt              | Cycles | Commandes git | Lecture                                          |
| ------------------ | ------ | ------------- | ------------------------------------------------ |
| `pap`              | 3736   | 20260         | le vault Obsidian actif — toute la charge         |
| `pap-lite`         | 196    | 984           | usage normal                                      |
| `obsidian-test`    | 142    | 735           | usage normal                                      |
| `pap-archive`      | 0      | 0             | **jamais surveillé**                              |
| `pap-lite-archive` | 0      | 0             | **jamais surveillé**                              |

Les deux archives n'ont jamais eu un seul cycle : le binaire déployé ne fait pas de cycle au
démarrage, et un dépôt d'archive ne produit aucun événement fichier. Le commit `022774a` (check
au démarrage) est précisément ce qui les fera exister — et sera la *seule* chose qui les
regardera jamais.

## Les watchers meurent en silence

`autocommit-error.log` contient 5 `Exception in thread "Thread-1"`. Thread-1 est le watcher de
`pap`, le premier dépôt de la config. La mort d'un thread ne fait pas sortir le process, donc
`KeepAlive` ne relance rien : `pap` est resté non surveillé jusqu'au redémarrage manuel suivant,
sans aucun signal.

Le catch-all de `0ae5dad` referme le cas « erreur pendant un cycle », mais pas le `break` sur
`key.reset()` invalide (dossier surveillé disparu), ni une exception pendant `register()`.
`WatchCommand` ne vérifie jamais `thread.isAlive`.

## État au 2026-08-24

- Le process **tourne** : pid 79087, relancé par `KeepAlive` après le `kill` du 18 août. Il
  committe toujours — push `74c4de9..b0fb3ad` sur `pap` à 18:38. L'emballement peut donc revenir
  à la prochaine rafale d'écritures. Pour l'arrêter vraiment :
  `launchctl bootout gui/$(id -u)/autocommit`.
- **Aucune perte de travail** : les 5 dépôts sont sur `main`, working tree propre, `ahead=0` et
  `behind=0`.
- `autocommit.log` fait 11,9 Mo et n'est jamais tourné :
  `: > ~/work/autocommit/autocommit.log` avant de relancer, pour lire une trace propre.

## Suites

Le plan de correction est dans `fix-event-amplification.prompt.md` : debounce de 3 s,
`pull`/`push` conditionnels, filtrage `.gitignore`, enregistrement des répertoires créés à
chaud, et vérification que `logback.xml` est bien embarqué dans l'image native. La surveillance
`thread.isAlive` dans `WatchCommand` reste à arbitrer : le diagnostic ci-dessus montre que le
cas s'est produit 5 fois.
