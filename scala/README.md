## Scala scripts

_Scripts for data pre-processing_

---
#### `clean-data`

Cleans input xml data:
  - Extracts text from tags `<p>` and `<em>`.
  - Drops tags `<url>`, `<h1>`, `<h2>`.
  - Warns if encounters other tag.

Clean logic is defined by function `cleanAndFlatten`.

**[File](./clean-data.sc)**

**Main class**: `CleanData`

**Arguments**:
  1. Input data directory. Will be searched recursively.
  2. Data file suffix.
  3. Output data directory. Input directory structure will be preserved. All the directories will be created if not exist.

**Hardcoded params**:
  - `logFile = "clean-data.log"` 
  - `maxPar = 4` - process files in parallel

---
#### `find-keywords`

Searches for _keywords_ in specified dir and reports encounters to specified file.

A _keyword_ is a sequence of capitalized* words.

The logic is defined by function `extractKeywords`.

**[File](./find-keywords.sc)**

**Main class**: `FindKeywords`

**Arguments**:
1. Input data directory (clean data). Will be searched recursively.
2. Data file suffix.
3. Output file.

**Hardcoded params**:
- `maxPar = 4` - process files in parallel
- `limFreq = 3` - limit output keywords by frequency

---

## Scala CLI

The scripts require only [scala-cli](https://scala-cli.virtuslab.org/install) to be installed.
The CLI manages JVMs, Scala versions and the libraries. 

The commands below are assumed to be run from current directory.

### Compile
Just compile:
```shell
scala-cli compile .
```
Use system JDK:
```shell
scala-cli compile . --bloop-jvm=system
```
Watch sources:
```shell
scala-cli compile . -w
```

### Package
[Lightweight launcher JAR](https://scala-cli.virtuslab.org/docs/commands/package#default-package-format):
```shell
scala-cli package . -M <main_name> -o <executable_name>
```

Then you just run it
```shell
./<executable_name> arg1 arg2 ...
```

Examples:
- ```shell
  scala-cli package . -M CleanData -o clean-data
  ```
  ```shell
  ./clean-data <data_dir> <file_suffix> <output_dir>
  ```
   

### Run
(Or use the packaged launcher :arrow_up:)

```shell
scala-cli run . -M <main_name> -- arg1 arg2 ...
```


### IDE integration
The CLI supports IDE integration based on [Build Server Protocol](https://build-server-protocol.github.io/).
```
scala-cli setup-ide .
```

#### IntelliJ Idea
Setting up project in current directory would result in duplicate project roots.
To avoid it, set up the IDE in the parent dir:
```
scala-cli setup-ide ..
```
