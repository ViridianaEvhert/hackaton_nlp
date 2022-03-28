.RECIPEPREFIX = >


scala_compile:
> @scala-cli compile scala

scala_package:
> @scala-cli package scala -M CleanData -f -o clean-data
> @scala-cli package scala -M FindKeywords -f -o find-keywords
