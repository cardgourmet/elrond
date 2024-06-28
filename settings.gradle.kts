plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "elrond"
include("elrond-core")
include("elrond-user")
include("elrond-tokenizer")
include("elrond-tcg")
